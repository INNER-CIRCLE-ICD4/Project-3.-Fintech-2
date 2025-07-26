package fintech2.easypay.transfer.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 외부 뱅킹 API 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankingApiResponse {
    private String transactionId;
    private String bankTransactionId; // 은행 시스템의 거래 ID
    private BankingApiStatus status;
    private String message;
    private LocalDateTime processedAt;
    private String errorCode;
    private String errorMessage;
}