package fintech2.easypay.payment.entity;

public enum PaymentMethod {
    BALANCE,     // 잔액 결제
    CARD,        // 카드 결제
    BANK_TRANSFER, // 계좌 이체
    PG_GATEWAY   // PG 결제
}
