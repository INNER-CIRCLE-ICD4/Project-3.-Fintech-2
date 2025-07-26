package fintech2.easypay.transfer.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 외부 뱅킹 API 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankingApiRequest {
    private String transactionId;
    private String senderAccountNumber;
    private String senderBankCode;
    private String receiverAccountNumber;
    private String receiverBankCode;
    private BigDecimal amount;
    private String currency;
    private String memo;
}