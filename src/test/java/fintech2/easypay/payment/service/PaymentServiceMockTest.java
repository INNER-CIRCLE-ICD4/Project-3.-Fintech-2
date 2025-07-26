package fintech2.easypay.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * 결제 서비스 독립 테스트
 * Mockito를 사용하여 외부 의존성 없이 결제 로직만 단독으로 테스트
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceMockTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewayService paymentGatewayService;

    @Mock
    private AccountService accountService;

    @Mock
    private MemberService memberService;

    @Mock
    private PaymentAuditService auditService;

    @Mock
    private PaymentNotificationService notificationService;

    @InjectMocks
    private RefactoredPaymentService paymentService;

    private Member testMember;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        // 테스트용 회원 데이터 설정
        testMember = Member.builder()
                .id(1L)
                .phoneNumber("010-1234-5678")
                .name("홍길동")
                .password("password123")
                .build();

        // 테스트용 계좌 데이터 설정
        testAccount = Account.builder()
                .id(1L)
                .accountNumber("1234567890")
                .balance(new BigDecimal("1000000")) // 100만원
                .member(testMember)
                .build();
    }

    @Test
    @DisplayName("잔액 결제 성공 테스트")
    void testBalancePaymentSuccess() {
        // Given: 잔액 결제 요청 데이터
        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("50000"),
                PaymentMethod.BALANCE,
                "잔액 결제 테스트",
                "테스트 상품",
                "ORDER001",
                null, null, null, null, null
        );

        Payment savedPayment = Payment.builder()
                .paymentId("PAY123456789ABC")
                .member(testMember)
                .status(PaymentStatus.REQUESTED)
                .amount(request.getAmount())
                .merchantName(request.getMerchantName())
                .build();

        // Mock 설정
        when(memberService.findByPhoneNumber("010-1234-5678")).thenReturn(Optional.of(testMember));
        when(accountService.findByMemberId(1L)).thenReturn(Optional.of(testAccount));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        // When: 결제 처리 실행
        PaymentResponse response = paymentService.processPayment("010-1234-5678", request);

        // Then: 결제 결과 검증
        assertThat(response.getPaymentId()).isEqualTo("PAY123456789ABC");
        
        // Mock 호출 검증
        verify(memberService, times(1)).findByPhoneNumber("010-1234-5678");
        verify(accountService, times(1)).findByMemberId(1L);
        verify(accountService, times(1)).withdraw(testAccount, request.getAmount());
        verify(auditService, times(1)).logPaymentSuccess(
                eq(1L), eq("010-1234-5678"), contains("잔액 결제 승인"), anyString(), anyString());
        verify(notificationService, times(1)).sendPaymentActivityNotification(
                eq(1L), eq("010-1234-5678"), contains("결제되었습니다"));
    }

    @Test
    @DisplayName("잔액 부족 시 결제 실패 테스트")
    void testInsufficientBalancePaymentFailure() {
        // Given: 잔액보다 큰 금액 결제 요청
        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("2000000"), // 200만원 (잔액 100만원보다 큼)
                PaymentMethod.BALANCE,
                "잔액 부족 테스트",
                "테스트 상품",
                "ORDER001",
                null, null, null, null, null
        );

        // 잔액 부족 계좌 생성
        Account insufficientAccount = Account.builder()
                .id(1L)
                .accountNumber("1234567890")
                .balance(new BigDecimal("100000")) // 10만원만 있음
                .member(testMember)
                .build();

        // Mock 설정
        when(memberService.findByPhoneNumber("010-1234-5678")).thenReturn(Optional.of(testMember));
        when(accountService.findByMemberId(1L)).thenReturn(Optional.of(insufficientAccount));

        // When & Then: 잔액 부족 예외 발생 확인
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            paymentService.processPayment("010-1234-5678", request);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        
        // Mock 호출 검증 - 실제 출금은 호출되지 않아야 함
        verify(accountService, never()).withdraw(any(Account.class), any(BigDecimal.class));
    }

    @Test
    @DisplayName("카드 결제 성공 테스트")
    void testCardPaymentSuccess() {
        // Given: 카드 결제 요청 데이터
        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("30000"),
                PaymentMethod.CARD,
                "카드 결제 테스트",
                "테스트 상품",
                "ORDER001",
                "1234-5678-9012-3456", "12/25", "123", null, null
        );

        Payment savedPayment = Payment.builder()
                .paymentId("PAY123456789ABC")
                .member(testMember)
                .status(PaymentStatus.REQUESTED)
                .amount(request.getAmount())
                .merchantName(request.getMerchantName())
                .build();

        // PG API 성공 응답 설정
        PgApiResponse pgResponse = new PgApiResponse();
        pgResponse.setStatus(PgApiStatus.SUCCESS);
        pgResponse.setPgTransactionId("PG_TXN_123");
        pgResponse.setRawResponse("카드 결제 승인");

        // Mock 설정
        when(memberService.findByPhoneNumber("010-1234-5678")).thenReturn(Optional.of(testMember));
        when(accountService.findByMemberId(1L)).thenReturn(Optional.of(testAccount));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(paymentGatewayService.processPayment(any(PgApiRequest.class))).thenReturn(pgResponse);

        // When: 결제 처리 실행
        PaymentResponse response = paymentService.processPayment("010-1234-5678", request);

        // Then: 결제 결과 검증
        assertThat(response.getPaymentId()).isEqualTo("PAY123456789ABC");
        
        // Mock 호출 검증
        verify(paymentGatewayService, times(1)).processPayment(any(PgApiRequest.class));
        verify(accountService, never()).withdraw(any(Account.class), any(BigDecimal.class)); // 카드 결제이므로 잔액 차감 없음
        verify(auditService, times(1)).logPaymentSuccess(
                eq(1L), eq("010-1234-5678"), contains("결제 승인"), anyString(), anyString());
    }

    @Test
    @DisplayName("카드 결제 실패 테스트")
    void testCardPaymentFailure() {
        // Given: 카드 결제 요청 데이터
        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("30000"),
                PaymentMethod.CARD,
                "카드 결제 실패 테스트",
                "테스트 상품",
                "ORDER001",
                "1234-5678-9012-3456", "12/25", "123", null, null
        );

        Payment savedPayment = Payment.builder()
                .paymentId("PAY123456789ABC")
                .member(testMember)
                .status(PaymentStatus.REQUESTED)
                .amount(request.getAmount())
                .merchantName(request.getMerchantName())
                .build();

        // PG API 실패 응답 설정
        PgApiResponse pgResponse = new PgApiResponse();
        pgResponse.setStatus(PgApiStatus.FAILED);
        pgResponse.setErrorMessage("카드 승인 거절");

        // Mock 설정
        when(memberService.findByPhoneNumber("010-1234-5678")).thenReturn(Optional.of(testMember));
        when(accountService.findByMemberId(1L)).thenReturn(Optional.of(testAccount));
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(paymentGatewayService.processPayment(any(PgApiRequest.class))).thenReturn(pgResponse);

        // When & Then: 결제 실패 예외 발생 확인
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            paymentService.processPayment("010-1234-5678", request);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED);
        
        // Mock 호출 검증
        verify(auditService, times(1)).logPaymentFailure(
                eq(1L), eq("010-1234-5678"), contains("결제 실패"), anyString(), anyString());
        verify(notificationService, times(1)).sendPaymentFailureNotification(
                eq(1L), eq("010-1234-5678"), contains("결제 실패"));
    }

    @Test
    @DisplayName("존재하지 않는 회원 결제 실패 테스트")
    void testPaymentWithNonExistentMember() {
        // Given: 존재하지 않는 회원 번호
        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("30000"),
                PaymentMethod.BALANCE,
                "테스트",
                "테스트 상품",
                "ORDER001",
                null, null, null, null, null
        );

        // Mock 설정 - 회원이 존재하지 않음
        when(memberService.findByPhoneNumber("010-9999-9999")).thenReturn(Optional.empty());

        // When & Then: 회원 없음 예외 발생 확인
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            paymentService.processPayment("010-9999-9999", request);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
        
        // Mock 호출 검증 - 계좌 조회는 호출되지 않아야 함
        verify(accountService, never()).findByMemberId(any(Long.class));
    }

    @Test
    @DisplayName("계좌 없는 회원 결제 실패 테스트")
    void testPaymentWithMemberWithoutAccount() {
        // Given: 계좌가 없는 회원
        Member memberWithoutAccount = Member.builder()
                .id(999L)
                .phoneNumber("010-9999-8888")
                .name("계좌없음")
                .password("password123")
                .build();

        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("30000"),
                PaymentMethod.BALANCE,
                "테스트",
                "테스트 상품",
                "ORDER001",
                null, null, null, null, null
        );

        // Mock 설정 - 회원은 존재하지만 계좌가 없음
        when(memberService.findByPhoneNumber("010-9999-8888")).thenReturn(Optional.of(memberWithoutAccount));
        when(accountService.findByMemberId(999L)).thenReturn(Optional.empty());

        // When & Then: 계좌 없음 예외 발생 확인
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            paymentService.processPayment("010-9999-8888", request);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("결제 취소 성공 테스트")
    void testPaymentCancelSuccess() {
        // Given: 취소 가능한 결제 생성
        Payment payment = Payment.builder()
                .paymentId("PAY123456789ABC")
                .member(testMember)
                .status(PaymentStatus.APPROVED)
                .amount(new BigDecimal("50000"))
                .merchantName("테스트 거래처")
                .paymentMethod(PaymentMethod.BALANCE)
                .build();

        // Mock 설정
        when(memberService.findByPhoneNumber("010-1234-5678")).thenReturn(Optional.of(testMember));
        when(paymentRepository.findByPaymentIdAndMemberId("PAY123456789ABC", 1L))
                .thenReturn(Optional.of(payment));
        when(accountService.findByMemberId(1L)).thenReturn(Optional.of(testAccount));

        // When: 결제 취소 실행
        PaymentResponse response = paymentService.cancelPayment("010-1234-5678", "PAY123456789ABC", "고객 요청");

        // Then: 취소 결과 검증
        assertThat(response.getPaymentId()).isEqualTo("PAY123456789ABC");
        
        // Mock 호출 검증
        verify(accountService, times(1)).deposit(testAccount, new BigDecimal("50000"));
        verify(auditService, times(1)).logPaymentCancel(
                eq(1L), eq("010-1234-5678"), contains("결제 취소"), anyString(), anyString());
        verify(notificationService, times(1)).sendPaymentActivityNotification(
                eq(1L), eq("010-1234-5678"), contains("취소되었습니다"));
    }

    @Test
    @DisplayName("결제 환불 성공 테스트")
    void testPaymentRefundSuccess() {
        // Given: 환불 가능한 결제 생성
        Payment payment = Payment.builder()
                .paymentId("PAY123456789ABC")
                .member(testMember)
                .status(PaymentStatus.APPROVED)
                .amount(new BigDecimal("50000"))
                .merchantName("테스트 거래처")
                .paymentMethod(PaymentMethod.BALANCE)
                .build();

        // Mock 설정
        when(memberService.findByPhoneNumber("010-1234-5678")).thenReturn(Optional.of(testMember));
        when(paymentRepository.findByPaymentIdAndMemberId("PAY123456789ABC", 1L))
                .thenReturn(Optional.of(payment));
        when(accountService.findByMemberId(1L)).thenReturn(Optional.of(testAccount));

        // When: 결제 환불 실행
        PaymentResponse response = paymentService.refundPayment(
                "010-1234-5678", 
                "PAY123456789ABC", 
                new BigDecimal("30000"), 
                "부분 환불"
        );

        // Then: 환불 결과 검증
        assertThat(response.getPaymentId()).isEqualTo("PAY123456789ABC");
        
        // Mock 호출 검증
        verify(accountService, times(1)).deposit(testAccount, new BigDecimal("30000"));
        verify(auditService, times(1)).logPaymentRefund(
                eq(1L), eq("010-1234-5678"), contains("결제 환불"), anyString(), anyString());
        verify(notificationService, times(1)).sendRefundNotification(
                eq(1L), eq("010-1234-5678"), contains("환불되었습니다"));
    }
}