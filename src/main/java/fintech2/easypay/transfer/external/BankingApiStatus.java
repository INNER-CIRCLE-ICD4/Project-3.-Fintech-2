package fintech2.easypay.transfer.external;

/**
 * 외부 뱅킹 API 처리 상태
 */
public enum BankingApiStatus {
    SUCCESS("성공"),
    PENDING("처리중"),
    FAILED("실패"),
    TIMEOUT("타임아웃"),
    UNKNOWN("알 수 없음"),
    INSUFFICIENT_BALANCE("잔액부족"),
    INVALID_ACCOUNT("계좌정보오류"),
    SYSTEM_ERROR("시스템오류");
    
    private final String description;
    
    BankingApiStatus(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}