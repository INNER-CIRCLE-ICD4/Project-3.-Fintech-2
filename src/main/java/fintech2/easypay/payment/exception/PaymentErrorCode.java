package fintech2.easypay.payment.exception;

import lombok.Getter;

@Getter
public enum PaymentErrorCode {
    
    PAYMENT_NOT_FOUND("P001", "결제 정보를 찾을 수 없습니다"),
    INSUFFICIENT_BALANCE("P002", "잔액이 부족합니다"),
    INVALID_PAYMENT_AMOUNT("P003", "유효하지 않은 결제 금액입니다"),
    PAYMENT_ALREADY_PROCESSED("P004", "이미 처리된 결제입니다"),
    PAYMENT_CANCELLED("P005", "취소된 결제입니다"),
    PAYMENT_TIMEOUT("P006", "결제 처리 시간이 초과되었습니다"),
    PG_API_ERROR("P007", "PG사 API 호출 중 오류가 발생했습니다"),
    ACCOUNT_NOT_FOUND("P008", "계좌를 찾을 수 없습니다"),
    MEMBER_NOT_FOUND("P009", "회원을 찾을 수 없습니다"),
    CANNOT_CANCEL_PAYMENT("P010", "취소할 수 없는 결제입니다"),
    CANNOT_REFUND_PAYMENT("P011", "환불할 수 없는 결제입니다"),
    INVALID_MERCHANT_INFO("P012", "유효하지 않은 가맹점 정보입니다"),
    PAYMENT_FAILED("P013", "결제 처리에 실패했습니다"),
    PAYMENT_CANNOT_BE_CANCELLED("P014", "취소할 수 없는 결제입니다"),
    PAYMENT_CANCEL_FAILED("P015", "결제 취소에 실패했습니다"),
    PAYMENT_CANNOT_BE_REFUNDED("P016", "환불할 수 없는 결제입니다"),
    PAYMENT_REFUND_FAILED("P017", "결제 환불에 실패했습니다"),
    INVALID_REFUND_AMOUNT("P018", "유효하지 않은 환불 금액입니다");
    
    private final String code;
    private final String message;
    
    PaymentErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}