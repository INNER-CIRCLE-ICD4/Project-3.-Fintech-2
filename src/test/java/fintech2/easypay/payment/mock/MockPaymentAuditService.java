package fintech2.easypay.payment.mock;

import fintech2.easypay.payment.service.PaymentAuditService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 결제 테스트용 감사 로그 서비스 Mock 구현체
 * 실제 로그 저장 없이 메모리에서 로그 정보를 관리하고 검증 가능
 */
public class MockPaymentAuditService implements PaymentAuditService {
    
    /**
     * 감사 로그 기록 클래스
     */
    public static class AuditLog {
        private final String type;
        private final Long memberId;
        private final String phoneNumber;
        private final String eventDescription;
        private final String requestData;
        private final String responseData;
        private final LocalDateTime timestamp;
        
        public AuditLog(String type, Long memberId, String phoneNumber, String eventDescription,
                       String requestData, String responseData) {
            this.type = type;
            this.memberId = memberId;
            this.phoneNumber = phoneNumber;
            this.eventDescription = eventDescription;
            this.requestData = requestData;
            this.responseData = responseData;
            this.timestamp = LocalDateTime.now();
        }
        
        // Getters
        public String getType() { return type; }
        public Long getMemberId() { return memberId; }
        public String getPhoneNumber() { return phoneNumber; }
        public String getEventDescription() { return eventDescription; }
        public String getRequestData() { return requestData; }
        public String getResponseData() { return responseData; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }
    
    private final List<AuditLog> auditLogs = new ArrayList<>();
    
    /**
     * 기록된 감사 로그 조회
     */
    public List<AuditLog> getAuditLogs() {
        return new ArrayList<>(auditLogs);
    }
    
    /**
     * 특정 타입의 감사 로그만 조회
     */
    public List<AuditLog> getAuditLogsByType(String type) {
        return auditLogs.stream()
                .filter(log -> type.equals(log.getType()))
                .toList();
    }
    
    /**
     * 감사 로그 데이터 초기화
     */
    public void clearAuditLogs() {
        auditLogs.clear();
    }
    
    @Override
    public void logPaymentSuccess(Long memberId, String phoneNumber, String eventDescription,
                                 String requestData, String responseData) {
        auditLogs.add(new AuditLog("PAYMENT_SUCCESS", memberId, phoneNumber, 
                                  eventDescription, requestData, responseData));
    }
    
    @Override
    public void logPaymentFailure(Long memberId, String phoneNumber, String eventDescription,
                                 String requestData, String errorMessage) {
        auditLogs.add(new AuditLog("PAYMENT_FAILURE", memberId, phoneNumber, 
                                  eventDescription, requestData, errorMessage));
    }
    
    @Override
    public void logPaymentCancel(Long memberId, String phoneNumber, String eventDescription,
                                String requestData, String responseData) {
        auditLogs.add(new AuditLog("PAYMENT_CANCEL", memberId, phoneNumber, 
                                  eventDescription, requestData, responseData));
    }
    
    @Override
    public void logPaymentRefund(Long memberId, String phoneNumber, String eventDescription,
                                String requestData, String responseData) {
        auditLogs.add(new AuditLog("PAYMENT_REFUND", memberId, phoneNumber, 
                                  eventDescription, requestData, responseData));
    }
}