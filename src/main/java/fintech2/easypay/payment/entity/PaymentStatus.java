package fintech2.easypay.payment.entity;

public enum PaymentStatus {
    REQUESTED,   // 요청됨
    PROCESSING,  // 처리중
    APPROVED,    // 승인됨
    FAILED,      // 실패
    CANCELLED,   // 취소됨
    REFUNDED     // 환불됨
}
