package fintech2.easypay.payment.exception;

import lombok.Getter;

@Getter
public class PaymentException extends RuntimeException {
    
    private final PaymentErrorCode errorCode;
    
    public PaymentException(PaymentErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public PaymentException(PaymentErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
    
    public PaymentException(PaymentErrorCode errorCode, String additionalMessage) {
        super(errorCode.getMessage() + " - " + additionalMessage);
        this.errorCode = errorCode;
    }
}