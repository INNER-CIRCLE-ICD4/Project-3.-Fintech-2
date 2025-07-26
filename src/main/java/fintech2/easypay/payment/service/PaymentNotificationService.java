package fintech2.easypay.payment.service;

/**
 * 결제 모듈 전용 알림 서비스 인터페이스
 * 실제 NotificationService 의존성을 제거하고 테스트 가능하도록 분리
 */
public interface PaymentNotificationService {
    
    /**
     * 결제 활동 알림 전송
     */
    void sendPaymentActivityNotification(Long memberId, String phoneNumber, String message);
    
    /**
     * 결제 실패 알림 전송
     */
    void sendPaymentFailureNotification(Long memberId, String phoneNumber, String message);
    
    /**
     * 결제 환불 알림 전송
     */
    void sendRefundNotification(Long memberId, String phoneNumber, String message);
}