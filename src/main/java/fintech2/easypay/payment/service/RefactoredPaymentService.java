package fintech2.easypay.payment.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.member.entity.Member;
import fintech2.easypay.payment.dto.PaymentRequest;
import fintech2.easypay.payment.dto.PaymentResponse;
import fintech2.easypay.payment.entity.Payment;
import fintech2.easypay.payment.entity.PaymentMethod;
import fintech2.easypay.payment.entity.PaymentStatus;
import fintech2.easypay.payment.external.PaymentGatewayService;
import fintech2.easypay.payment.external.PgApiRequest;
import fintech2.easypay.payment.external.PgApiResponse;
import fintech2.easypay.payment.external.PgApiStatus;
import fintech2.easypay.payment.repository.PaymentRepository;

/**
 * 리팩토링된 결제 서비스
 * 추상화된 인터페이스를 사용하여 의존성을 분리하고 테스트 가능하도록 구현
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RefactoredPaymentService {
    
    private final PaymentRepository paymentRepository;
    private final PaymentGatewayService paymentGatewayService;
    
    // 추상화된 서비스 인터페이스들
    private final AccountService accountService;
    private final MemberService memberService;
    private final PaymentAuditService auditService;
    private final PaymentNotificationService notificationService;
    
    /**
     * 결제 처리
     * 1. 결제 가능 여부 확인
     * 2. 결제 요청 DB 저장 (PENDING)
     * 3. 외부 PG API 호출
     * 4. 결제 결과에 따른 상태 업데이트
     */
    @Transactional
    public PaymentResponse processPayment(String phoneNumber, PaymentRequest request) {
        // 회원 조회
        Member member = memberService.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 계좌 조회 및 검증
        Account account = accountService.findByMemberId(member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
        
        // 결제 방법이 BALANCE인 경우 잔액 확인
        if (request.getPaymentMethod() == PaymentMethod.BALANCE) {
            if (!account.hasEnoughBalance(request.getAmount())) {
                throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE);
            }
        }
        
        // 결제 ID 생성
        String paymentId = generatePaymentId();
        
        // 1. 결제 요청을 REQUESTED 상태로 DB 저장
        Payment payment = Payment.builder()
                .paymentId(paymentId)
                .member(member)
                .accountNumber(account.getAccountNumber())
                .merchantId(request.getMerchantId())
                .merchantName(request.getMerchantName())
                .amount(request.getAmount())
                .memo(request.getMemo())
                .paymentMethod(request.getPaymentMethod())
                .build();
        
        Payment savedPayment = paymentRepository.save(payment);
        
        try {
            // 2. 결제 처리 중 상태로 변경
            payment.markAsProcessing();
            paymentRepository.save(payment);
            
            // 3. 외부 PG API 호출 (BALANCE가 아닌 경우에만)
            if (request.getPaymentMethod() != PaymentMethod.BALANCE) {
                PgApiRequest pgRequest = buildPgApiRequest(paymentId, member, request);
                
                log.info("외부 PG API 호출 시작: {}", paymentId);
                PgApiResponse pgResponse = paymentGatewayService.processPayment(pgRequest);
                
                // 4. PG API 응답에 따른 처리
                if (pgResponse.getStatus() == PgApiStatus.SUCCESS) {
                    payment.markAsApproved(pgResponse.getPgTransactionId(), pgResponse.getRawResponse());
                    
                    // 감사 로그 기록
                    auditService.logPaymentSuccess(
                        member.getId(),
                        phoneNumber,
                        String.format("결제 승인: %s (%s원)", request.getMerchantName(), request.getAmount()),
                        String.format("paymentId: %s, method: %s", paymentId, request.getPaymentMethod()),
                        pgResponse.getRawResponse()
                    );
                } else {
                    String failureReason = String.format("PG 오류: %s - %s", 
                        pgResponse.getStatus().getDescription(), 
                        pgResponse.getErrorMessage());
                    payment.markAsFailed(failureReason);
                    
                    throw new BusinessException(ErrorCode.PAYMENT_FAILED, failureReason);
                }
            } else {
                // BALANCE 결제인 경우 즉시 승인 및 잔액 차감
                accountService.withdraw(account, request.getAmount());
                payment.markAsApproved("BALANCE-" + paymentId, "잔액 결제");
                
                // 감사 로그 기록
                auditService.logPaymentSuccess(
                    member.getId(),
                    phoneNumber,
                    String.format("잔액 결제 승인: %s (%s원)", request.getMerchantName(), request.getAmount()),
                    String.format("paymentId: %s, balance: %s", paymentId, account.getBalance()),
                    "잔액 결제 완료"
                );
            }
            
            // 알림 전송
            notificationService.sendPaymentActivityNotification(
                member.getId(),
                phoneNumber,
                String.format("%s에서 %s원이 결제되었습니다.", request.getMerchantName(), request.getAmount())
            );
            
            log.info("결제 완료: {} - {} ({}원)", paymentId, request.getMerchantName(), request.getAmount());
            
            return PaymentResponse.from(savedPayment);
            
        } catch (Exception e) {
            // 결제 실패 처리
            payment.markAsFailed(e.getMessage());
            
            // 감사 로그 기록
            auditService.logPaymentFailure(
                member.getId(),
                phoneNumber,
                "결제 실패: " + e.getMessage(),
                String.format("paymentId: %s, amount: %s", paymentId, request.getAmount()),
                e.getMessage()
            );
            
            // 실패 알림 전송
            notificationService.sendPaymentFailureNotification(
                member.getId(),
                phoneNumber,
                String.format("결제 실패: %s", e.getMessage())
            );
            
            log.error("결제 실패: {} - {} ({}원) - {}", 
                    paymentId, request.getMerchantName(), request.getAmount(), e.getMessage());
            
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.PAYMENT_FAILED, e.getMessage());
        }
    }
    
    /**
     * 결제 취소
     */
    @Transactional
    public PaymentResponse cancelPayment(String phoneNumber, String paymentId, String reason) {
        // 회원 조회
        Member member = memberService.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 결제 정보 조회
        Payment payment = paymentRepository.findByPaymentIdAndMemberId(paymentId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        
        // 취소 가능 여부 확인
        if (!payment.canBeCancelled()) {
            throw new BusinessException(ErrorCode.PAYMENT_CANNOT_BE_CANCELLED);
        }
        
        try {
            // 외부 PG API 호출 (BALANCE가 아닌 경우)
            if (payment.getPaymentMethod() != PaymentMethod.BALANCE) {
                log.info("결제 취소 API 호출: {}", paymentId);
                PgApiResponse pgResponse = paymentGatewayService.cancelPayment(paymentId, reason);
                
                if (pgResponse.getStatus() != PgApiStatus.CANCELLED) {
                    throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, pgResponse.getErrorMessage());
                }
            } else {
                // BALANCE 결제인 경우 잔액 복원
                Account account = accountService.findByMemberId(member.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
                accountService.deposit(account, payment.getAmount());
            }
            
            // 결제 상태 변경
            payment.markAsCancelled();
            
            // 감사 로그 기록
            auditService.logPaymentCancel(
                member.getId(),
                phoneNumber,
                String.format("결제 취소: %s (%s원)", payment.getMerchantName(), payment.getAmount()),
                String.format("paymentId: %s, reason: %s", paymentId, reason),
                "취소 완료"
            );
            
            // 알림 전송
            notificationService.sendPaymentActivityNotification(
                member.getId(),
                phoneNumber,
                String.format("%s 결제가 취소되었습니다. (%s원)", payment.getMerchantName(), payment.getAmount())
            );
            
            return PaymentResponse.from(payment);
            
        } catch (Exception e) {
            log.error("결제 취소 실패: {} - {}", paymentId, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_CANCEL_FAILED, e.getMessage());
        }
    }
    
    /**
     * 결제 환불
     */
    @Transactional
    public PaymentResponse refundPayment(String phoneNumber, String paymentId, BigDecimal amount, String reason) {
        // 회원 조회
        Member member = memberService.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 결제 정보 조회
        Payment payment = paymentRepository.findByPaymentIdAndMemberId(paymentId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        
        // 환불 가능 여부 확인
        if (!payment.canBeRefunded()) {
            throw new BusinessException(ErrorCode.PAYMENT_CANNOT_BE_REFUNDED);
        }
        
        // 환불 금액 검증
        if (amount.compareTo(payment.getAmount()) > 0) {
            throw new BusinessException(ErrorCode.INVALID_REFUND_AMOUNT);
        }
        
        try {
            // 외부 PG API 호출 (BALANCE가 아닌 경우)
            if (payment.getPaymentMethod() != PaymentMethod.BALANCE) {
                log.info("결제 환불 API 호출: {} - {}원", paymentId, amount);
                PgApiResponse pgResponse = paymentGatewayService.refundPayment(paymentId, amount, reason);
                
                if (pgResponse.getStatus() != PgApiStatus.REFUNDED) {
                    throw new BusinessException(ErrorCode.PAYMENT_REFUND_FAILED, pgResponse.getErrorMessage());
                }
            } else {
                // BALANCE 결제인 경우 잔액 복원
                Account account = accountService.findByMemberId(member.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
                accountService.deposit(account, amount);
            }
            
            // 결제 상태 변경
            payment.markAsRefunded();
            
            // 감사 로그 기록
            auditService.logPaymentRefund(
                member.getId(),
                phoneNumber,
                String.format("결제 환불: %s (%s원)", payment.getMerchantName(), amount),
                String.format("paymentId: %s, reason: %s", paymentId, reason),
                "환불 완료"
            );
            
            // 알림 전송
            notificationService.sendRefundNotification(
                member.getId(),
                phoneNumber,
                String.format("%s 결제가 환불되었습니다. (%s원)", payment.getMerchantName(), amount)
            );
            
            return PaymentResponse.from(payment);
            
        } catch (Exception e) {
            log.error("결제 환불 실패: {} - {}", paymentId, e.getMessage());
            throw new BusinessException(ErrorCode.PAYMENT_REFUND_FAILED, e.getMessage());
        }
    }
    
    /**
     * 결제 내역 조회
     */
    public Page<PaymentResponse> getPaymentHistory(String phoneNumber, Pageable pageable) {
        Member member = memberService.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        Page<Payment> payments = paymentRepository.findByMemberIdOrderByCreatedAtDesc(member.getId(), pageable);
        return payments.map(PaymentResponse::from);
    }
    
    /**
     * 결제 상세 조회
     */
    public PaymentResponse getPayment(String phoneNumber, String paymentId) {
        Member member = memberService.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        Payment payment = paymentRepository.findByPaymentIdAndMemberId(paymentId, member.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
        
        return PaymentResponse.from(payment);
    }
    
    /**
     * 결제 ID 생성
     */
    private String generatePaymentId() {
        String paymentId;
        do {
            paymentId = "PAY" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (paymentRepository.existsByPaymentId(paymentId));
        
        return paymentId;
    }
    
    /**
     * PG API 요청 생성
     */
    private PgApiRequest buildPgApiRequest(String paymentId, Member member, PaymentRequest request) {
        Map<String, String> cardInfo = null;
        Map<String, String> accountInfo = null;
        
        // 결제 수단에 따른 정보 설정
        if (request.getPaymentMethod() == PaymentMethod.CARD) {
            cardInfo = new HashMap<>();
            cardInfo.put("cardNumber", request.getCardNumber());
            cardInfo.put("expiryDate", request.getCardExpiryDate());
            cardInfo.put("cvv", request.getCardCvv());
        } else if (request.getPaymentMethod() == PaymentMethod.BANK_TRANSFER) {
            accountInfo = new HashMap<>();
            accountInfo.put("bankCode", request.getBankCode());
            accountInfo.put("accountNumber", request.getAccountNumber());
        }
        
        return PgApiRequest.builder()
                .paymentId(paymentId)
                .merchantId(request.getMerchantId())
                .merchantName(request.getMerchantName())
                .amount(request.getAmount())
                .currency("KRW")
                .paymentMethod(request.getPaymentMethod().name())
                .customerName(member.getName())
                .customerPhone(member.getPhoneNumber())
                .productName(request.getProductName())
                .orderNumber(request.getOrderNumber())
                .cardInfo(cardInfo)
                .accountInfo(accountInfo)
                .build();
    }
}