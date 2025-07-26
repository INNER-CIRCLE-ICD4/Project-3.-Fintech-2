package fintech2.easypay.payment.service;

import fintech2.easypay.audit.entity.AuditEventType;

/**
 * 결제 모듈 전용 감사 로그 서비스 인터페이스
 * 실제 AuditLogService 의존성을 제거하고 테스트 가능하도록 분리
 */
public interface PaymentAuditService {
    
    /**
     * 결제 성공 로그 기록
     */
    void logPaymentSuccess(Long memberId, String phoneNumber, String eventDescription,
                          String requestData, String responseData);
    
    /**
     * 결제 실패 로그 기록
     */
    void logPaymentFailure(Long memberId, String phoneNumber, String eventDescription,
                          String requestData, String errorMessage);
    
    /**
     * 결제 취소 로그 기록
     */
    void logPaymentCancel(Long memberId, String phoneNumber, String eventDescription,
                         String requestData, String responseData);
    
    /**
     * 결제 환불 로그 기록
     */
    void logPaymentRefund(Long memberId, String phoneNumber, String eventDescription,
                         String requestData, String responseData);
}