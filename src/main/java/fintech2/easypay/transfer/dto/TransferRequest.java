package fintech2.easypay.transfer.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    
    @NotBlank(message = "수신자 계좌번호는 필수입니다.")
    private String receiverAccountNumber;
    
    @NotNull(message = "송금 금액은 필수입니다.")
    @DecimalMin(value = "0.01", message = "송금 금액은 0보다 커야 합니다.")
    private BigDecimal amount;
    
    @Size(max = 100, message = "메모는 100자 이하여야 합니다.")
    private String memo;
}
