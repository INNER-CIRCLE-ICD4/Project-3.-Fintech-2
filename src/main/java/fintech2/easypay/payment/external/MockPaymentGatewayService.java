package fintech2.easypay.payment.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock PG API 서비스 구현체
 * 실제 PG사 API 대신 테스트용 Mock 응답을 생성
 */
@Service
@Slf4j
public class MockPaymentGatewayService implements PaymentGatewayService {
    
    // 결제 정보를 메모리에 저장 (실제로는 PG사 DB에 저장됨)
    private final ConcurrentHashMap<String, PgApiResponse> paymentStore = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    public PgApiResponse processPayment(PgApiRequest request) {
        log.info("Mock PG API 호출 - 결제 처리: {}", request);
        
        // 실제 API 호출 시뮬레이션 (100-300ms 지연)
        simulateApiDelay();
        
        // 카드번호 유효성 검사 시뮬레이션
        if (request.getCardInfo() != null) {
            String cardNumber = request.getCardInfo().get("cardNumber");
            if (cardNumber != null && cardNumber.startsWith("9999")) {
                // 테스트용 실패 카드번호
                return createFailureResponse(request, PgApiStatus.INVALID_CARD, "E101", "유효하지 않은 카드번호입니다.");
            }
        }
        
        // 랜덤하게 성공/실패 시나리오 생성
        PgApiResponse response;
        double randomValue = random.nextDouble();
        
        if (randomValue < 0.80) {
            // 80% 성공 케이스
            response = createSuccessResponse(request);
        } else if (randomValue < 0.85) {
            // 5% 잔액 부족
            response = createFailureResponse(request, PgApiStatus.INSUFFICIENT_BALANCE, "E201", "카드 한도가 부족합니다.");
        } else if (randomValue < 0.90) {
            // 5% 한도 초과
            response = createFailureResponse(request, PgApiStatus.LIMIT_EXCEEDED, "E202", "일일 결제 한도를 초과했습니다.");
        } else if (randomValue < 0.95) {
            // 5% 사기 의심
            response = createFailureResponse(request, PgApiStatus.SUSPECTED_FRAUD, "E301", "사기 의심 거래로 차단되었습니다.");
        } else {
            // 5% 시스템 오류
            response = createFailureResponse(request, PgApiStatus.SYSTEM_ERROR, "E999", "PG사 시스템 오류가 발생했습니다.");
        }
        
        // 결제 정보 저장
        paymentStore.put(request.getPaymentId(), response);
        
        log.info("Mock PG API 응답: {}", response);
        return response;
    }
    
    @Override
    public PgApiResponse cancelPayment(String paymentId, String reason) {
        log.info("Mock PG API 호출 - 결제 취소: paymentId={}, reason={}", paymentId, reason);
        
        simulateApiDelay();
        
        // 저장된 결제 정보 조회
        PgApiResponse originalPayment = paymentStore.get(paymentId);
        if (originalPayment == null) {
            return PgApiResponse.builder()
                    .paymentId(paymentId)
                    .status(PgApiStatus.FAILED)
                    .errorCode("E404")
                    .errorMessage("결제 정보를 찾을 수 없습니다.")
                    .processedAt(LocalDateTime.now())
                    .build();
        }
        
        // 이미 취소된 결제인지 확인
        if (originalPayment.getStatus() == PgApiStatus.CANCELLED) {
            return PgApiResponse.builder()
                    .paymentId(paymentId)
                    .status(PgApiStatus.FAILED)
                    .errorCode("E405")
                    .errorMessage("이미 취소된 결제입니다.")
                    .processedAt(LocalDateTime.now())
                    .build();
        }
        
        // 취소 처리
        PgApiResponse cancelResponse = PgApiResponse.builder()
                .paymentId(paymentId)
                .pgTransactionId("PG-CANCEL-" + UUID.randomUUID().toString().substring(0, 8))
                .status(PgApiStatus.CANCELLED)
                .message("결제가 취소되었습니다.")
                .processedAt(LocalDateTime.now())
                .approvedAmount(originalPayment.getApprovedAmount().negate())
                .build();
        
        paymentStore.put(paymentId, cancelResponse);
        return cancelResponse;
    }
    
    @Override
    public PgApiResponse refundPayment(String paymentId, BigDecimal amount, String reason) {
        log.info("Mock PG API 호출 - 결제 환불: paymentId={}, amount={}, reason={}", paymentId, amount, reason);
        
        simulateApiDelay();
        
        // 저장된 결제 정보 조회
        PgApiResponse originalPayment = paymentStore.get(paymentId);
        if (originalPayment == null) {
            return PgApiResponse.builder()
                    .paymentId(paymentId)
                    .status(PgApiStatus.FAILED)
                    .errorCode("E404")
                    .errorMessage("결제 정보를 찾을 수 없습니다.")
                    .processedAt(LocalDateTime.now())
                    .build();
        }
        
        // 환불 가능 금액 확인
        if (amount.compareTo(originalPayment.getApprovedAmount()) > 0) {
            return PgApiResponse.builder()
                    .paymentId(paymentId)
                    .status(PgApiStatus.FAILED)
                    .errorCode("E406")
                    .errorMessage("환불 금액이 결제 금액을 초과합니다.")
                    .processedAt(LocalDateTime.now())
                    .build();
        }
        
        // 환불 처리
        PgApiResponse refundResponse = PgApiResponse.builder()
                .paymentId(paymentId)
                .pgTransactionId("PG-REFUND-" + UUID.randomUUID().toString().substring(0, 8))
                .status(PgApiStatus.REFUNDED)
                .message("환불이 완료되었습니다.")
                .processedAt(LocalDateTime.now())
                .approvedAmount(amount.negate())
                .build();
        
        return refundResponse;
    }
    
    @Override
    public PgApiResponse getPaymentStatus(String paymentId) {
        log.info("Mock PG API 호출 - 결제 상태 조회: {}", paymentId);
        
        PgApiResponse response = paymentStore.get(paymentId);
        if (response == null) {
            return PgApiResponse.builder()
                    .paymentId(paymentId)
                    .status(PgApiStatus.FAILED)
                    .errorCode("E404")
                    .errorMessage("결제 정보를 찾을 수 없습니다.")
                    .processedAt(LocalDateTime.now())
                    .build();
        }
        
        return response;
    }
    
    /**
     * 성공 응답 생성
     */
    private PgApiResponse createSuccessResponse(PgApiRequest request) {
        String pgTransactionId = "PG-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String approvalNumber = generateApprovalNumber();
        
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("merchantId", request.getMerchantId());
        responseData.put("amount", request.getAmount());
        responseData.put("approvalNumber", approvalNumber);
        responseData.put("timestamp", LocalDateTime.now().toString());
        
        return PgApiResponse.builder()
                .paymentId(request.getPaymentId())
                .pgTransactionId(pgTransactionId)
                .status(PgApiStatus.SUCCESS)
                .message("결제가 정상적으로 승인되었습니다.")
                .processedAt(LocalDateTime.now())
                .approvedAmount(request.getAmount())
                .approvalNumber(approvalNumber)
                .cardCompany(getRandomCardCompany())
                .cardNumber(maskCardNumber(request.getCardInfo() != null ? 
                    request.getCardInfo().get("cardNumber") : "1234567890123456"))
                .rawResponse(toJson(responseData))
                .build();
    }
    
    /**
     * 실패 응답 생성
     */
    private PgApiResponse createFailureResponse(PgApiRequest request, PgApiStatus status, 
                                              String errorCode, String errorMessage) {
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("merchantId", request.getMerchantId());
        responseData.put("amount", request.getAmount());
        responseData.put("errorCode", errorCode);
        responseData.put("errorMessage", errorMessage);
        responseData.put("timestamp", LocalDateTime.now().toString());
        
        return PgApiResponse.builder()
                .paymentId(request.getPaymentId())
                .status(status)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .processedAt(LocalDateTime.now())
                .rawResponse(toJson(responseData))
                .build();
    }
    
    /**
     * API 호출 지연 시뮬레이션
     */
    private void simulateApiDelay() {
        try {
            Thread.sleep(100 + random.nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 승인번호 생성
     */
    private String generateApprovalNumber() {
        return String.format("%08d", random.nextInt(100000000));
    }
    
    /**
     * 랜덤 카드사 선택
     */
    private String getRandomCardCompany() {
        String[] companies = {"신한카드", "국민카드", "우리카드", "하나카드", "롯데카드", "삼성카드", "현대카드", "BC카드"};
        return companies[random.nextInt(companies.length)];
    }
    
    /**
     * 카드번호 마스킹
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 8) {
            return "****-****-****-****";
        }
        return cardNumber.substring(0, 4) + "-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }
    
    /**
     * JSON 변환
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
    
    /**
     * 특정 금액 이상에서만 실패하도록 설정 (테스트용)
     */
    public PgApiResponse processPaymentWithThreshold(PgApiRequest request, BigDecimal threshold) {
        if (request.getAmount().compareTo(threshold) > 0) {
            return createFailureResponse(request, PgApiStatus.LIMIT_EXCEEDED, "E202", 
                "테스트 한도 " + threshold + "원을 초과했습니다.");
        }
        return processPayment(request);
    }
}