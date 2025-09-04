package fintech2.easypay.transfer.action;

import java.util.HashMap;
import java.util.Map;

/**
 * 송금 액션의 실행 결과를 나타내는 클래스
 * 모든 송금 작업의 결과를 표준화된 형태로 반환
 */
public class ActionResult {
    
    public enum Status { 
        SUCCESS,    // 송금 성공
        FAILURE,    // 송금 실패
        PENDING     // 송금 처리 중
    }
    
    private final Status status;
    private final String code;                    // 결과 코드 (예: "OK", "INSUFFICIENT_FUNDS", "NETWORK_ERROR")
    private final String message;                 // 사용자에게 표시할 메시지
    private final Map<String, Object> data;       // 추가 데이터
    private final String transactionId;           // 거래 ID
    private final String bankTransactionId;       // 외부 은행 거래 ID
    
    private ActionResult(Builder builder) {
        this.status = builder.status;
        this.code = builder.code;
        this.message = builder.message;
        this.data = builder.data != null ? builder.data : new HashMap<>();
        this.transactionId = builder.transactionId;
        this.bankTransactionId = builder.bankTransactionId;
    }
    
    // 정적 팩토리 메서드
    public static ActionResult success(String message, String transactionId, Map<String, Object> data) {
        return new Builder()
                .status(Status.SUCCESS)
                .code("OK")
                .message(message)
                .transactionId(transactionId)
                .data(data)
                .build();
    }
    
    public static ActionResult failure(String code, String message, String transactionId, Map<String, Object> data) {
        return new Builder()
                .status(Status.FAILURE)
                .code(code)
                .message(message)
                .transactionId(transactionId)
                .data(data)
                .build();
    }
    
    public static ActionResult pending(String message, String transactionId, Map<String, Object> data) {
        return new Builder()
                .status(Status.PENDING)
                .code("PENDING")
                .message(message)
                .transactionId(transactionId)
                .data(data)
                .build();
    }
    
    // Getters
    public Status getStatus() { return status; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Map<String, Object> getData() { return data; }
    public String getTransactionId() { return transactionId; }
    public String getBankTransactionId() { return bankTransactionId; }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean isFailure() {
        return status == Status.FAILURE;
    }
    
    public boolean isPending() {
        return status == Status.PENDING;
    }
    
    // Builder 클래스
    public static class Builder {
        private Status status;
        private String code;
        private String message;
        private Map<String, Object> data;
        private String transactionId;
        private String bankTransactionId;
        
        public Builder status(Status status) {
            this.status = status;
            return this;
        }
        
        public Builder code(String code) {
            this.code = code;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }
        
        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }
        
        public Builder bankTransactionId(String bankTransactionId) {
            this.bankTransactionId = bankTransactionId;
            return this;
        }
        
        public ActionResult build() {
            return new ActionResult(this);
        }
    }
}