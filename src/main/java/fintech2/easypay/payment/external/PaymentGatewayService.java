package fintech2.easypay.payment.external;

/**
 * 외부 PG(Payment Gateway) API 서비스 인터페이스
 * 실제 결제 처리를 위한 외부 PG사와의 통신을 담당
 */
public interface PaymentGatewayService {
    
    /**
     * 결제 승인 요청
     * @param request 결제 요청 정보
     * @return 결제 처리 결과
     */
    PgApiResponse processPayment(PgApiRequest request);
    
    /**
     * 결제 취소 요청
     * @param paymentId 결제 ID
     * @param reason 취소 사유
     * @return 취소 처리 결과
     */
    PgApiResponse cancelPayment(String paymentId, String reason);
    
    /**
     * 결제 환불 요청
     * @param paymentId 결제 ID
     * @param amount 환불 금액 (부분 환불 가능)
     * @param reason 환불 사유
     * @return 환불 처리 결과
     */
    PgApiResponse refundPayment(String paymentId, java.math.BigDecimal amount, String reason);
    
    /**
     * 결제 상태 조회
     * @param paymentId 결제 ID
     * @return 결제 상태 정보
     */
    PgApiResponse getPaymentStatus(String paymentId);
}