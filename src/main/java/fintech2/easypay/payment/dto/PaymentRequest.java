package fintech2.easypay.payment.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import fintech2.easypay.payment.entity.PaymentMethod;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    
    @NotBlank(message = "거래처 ID는 필수입니다.")
    private String merchantId;
    
    @NotBlank(message = "거래처명은 필수입니다.")
    private String merchantName;
    
    @NotNull(message = "결제 금액은 필수입니다.")
    @DecimalMin(value = "0.01", message = "결제 금액은 0보다 커야 합니다.")
    private BigDecimal amount;
    
    @NotNull(message = "결제 수단은 필수입니다.")
    private PaymentMethod paymentMethod;
    
    @Size(max = 100, message = "메모는 100자 이하여야 합니다.")
    private String memo;
    
    private String productName;
    private String orderNumber;
    
    // 카드 결제 정보
    private String cardNumber;
    private String cardExpiryDate;
    private String cardCvv;
    
    // 계좌이체 정보
    private String bankCode;
    private String accountNumber;
}
