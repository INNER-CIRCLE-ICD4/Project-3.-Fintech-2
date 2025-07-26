package fintech2.easypay.transfer.entity;

public enum TransferStatus {
    REQUESTED,    // 요청됨
    PROCESSING,   // 처리중
    COMPLETED,    // 완료
    FAILED,       // 실패
    CANCELLED,    // 취소
    TIMEOUT,      // 타임아웃
    UNKNOWN       // 알 수 없음
}
