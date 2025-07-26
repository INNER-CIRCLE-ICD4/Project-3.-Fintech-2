package fintech2.easypay.payment.external;

/**
 * PG API 처리 상태
 */
public enum PgApiStatus {
    SUCCESS("성공"),
    PENDING("처리중"),
    FAILED("실패"),
    CANCELLED("취소됨"),
    REFUNDED("환불됨"),
    EXPIRED("만료됨"),
    INVALID_CARD("유효하지 않은 카드"),
    INSUFFICIENT_BALANCE("잔액부족"),
    LIMIT_EXCEEDED("한도초과"),
    SUSPECTED_FRAUD("사기의심"),
    NETWORK_ERROR("네트워크오류"),
    SYSTEM_ERROR("시스템오류");
    
    private final String description;
    
    PgApiStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}