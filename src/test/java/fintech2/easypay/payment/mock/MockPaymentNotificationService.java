package fintech2.easypay.payment.mock;

import fintech2.easypay.payment.service.PaymentNotificationService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 결제 테스트용 알림 서비스 Mock 구현체
 * 실제 알림 전송 없이 메모리에서 알림 정보를 관리하고 검증 가능
 */
public class MockPaymentNotificationService implements PaymentNotificationService {
    
    /**
     * 알림 기록 클래스
     */
    public static class NotificationLog {
        private final String type;
        private final Long memberId;
        private final String phoneNumber;
        private final String message;
        private final LocalDateTime timestamp;
        
        public NotificationLog(String type, Long memberId, String phoneNumber, String message) {
            this.type = type;
            this.memberId = memberId;
            this.phoneNumber = phoneNumber;
            this.message = message;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getType() { return type; }
        public Long getMemberId() { return memberId; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getMessage() { return message; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    private final List<NotificationLog> notificationLogs = new ArrayList<>();
    
    /**
     * 전송된 알림 조회
     */
    public List<NotificationLog> getNotificationLogs() {
        return new ArrayList<>(notificationLogs);
    }
    
    /**
     * 특정 타입의 알림만 조회
     */
    public List<NotificationLog> getNotificationLogsByType(String type) {
        return notificationLogs.stream()
                .filter(log -> type.equals(log.getType()))
                .toList();
    }
    
    /**
     * 알림 데이터 초기화
     */
    public void clearNotificationLogs() {
        notificationLogs.clear();
    }
    
    @Override
    public void sendPaymentActivityNotification(Long memberId, String phoneNumber, String message) {
        notificationLogs.add(new NotificationLog("PAYMENT_ACTIVITY", memberId, phoneNumber, message));
    }
    
    @Override
    public void sendPaymentFailureNotification(Long memberId, String phoneNumber, String message) {
        notificationLogs.add(new NotificationLog("PAYMENT_FAILURE", memberId, phoneNumber, message));
    }
    
    @Override
    public void sendRefundNotification(Long memberId, String phoneNumber, String message) {
        notificationLogs.add(new NotificationLog("REFUND", memberId, phoneNumber, message));
    }
}