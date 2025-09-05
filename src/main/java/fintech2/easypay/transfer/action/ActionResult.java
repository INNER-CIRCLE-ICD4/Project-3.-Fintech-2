package fintech2.easypay.transfer.action;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 송금 액션 실행 결과
 * 표준화된 실행 결과를 전달하기 위한 공통 모델
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActionResult {
    
    /**
     * 액션 실행 상태
     */
    public enum Status { 
        SUCCESS,    // 성공
        FAILURE,    // 실패  
        PENDING,    // 처리중
        TIMEOUT,    // 타임아웃
        UNKNOWN     // 알 수 없음
    }
    
    private Status status;
    private String code;        // 예: "OK", "INSUFFICIENT_FUNDS", "NETWORK_ERROR"
    private String message;     // 사용자/로그 메시지
    private Map<String, Object> data;    // 추가 데이터
    
    public static ActionResult success(String message, Map<String, Object> data) {
        return new ActionResult(Status.SUCCESS, "OK", message, data);
    }
    
    public static ActionResult success(String message) {
        return new ActionResult(Status.SUCCESS, "OK", message, null);
    }
    
    public static ActionResult failure(String code, String message, Map<String, Object> data) {
        return new ActionResult(Status.FAILURE, code, message, data);
    }
    
    public static ActionResult failure(String code, String message) {
        return new ActionResult(Status.FAILURE, code, message, null);
    }
    
    public static ActionResult pending(String message, Map<String, Object> data) {
        return new ActionResult(Status.PENDING, "PENDING", message, data);
    }
    
    public static ActionResult pending(String message) {
        return new ActionResult(Status.PENDING, "PENDING", message, null);
    }
    
    public static ActionResult timeout(String message, Map<String, Object> data) {
        return new ActionResult(Status.TIMEOUT, "TIMEOUT", message, data);
    }
    
    public static ActionResult timeout(String message) {
        return new ActionResult(Status.TIMEOUT, "TIMEOUT", message, null);
    }
    
    public static ActionResult unknown(String message, Map<String, Object> data) {
        return new ActionResult(Status.UNKNOWN, "UNKNOWN", message, data);
    }
    
    public static ActionResult unknown(String message) {
        return new ActionResult(Status.UNKNOWN, "UNKNOWN", message, null);
    }
    
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
    
    public boolean isFailure() {
        return status == Status.FAILURE;
    }
    
    public boolean isPending() {
        return status == Status.PENDING;
    }
    
    public boolean isTimeout() {
        return status == Status.TIMEOUT;
    }
    
    public boolean isUnknown() {
        return status == Status.UNKNOWN;
    }
}