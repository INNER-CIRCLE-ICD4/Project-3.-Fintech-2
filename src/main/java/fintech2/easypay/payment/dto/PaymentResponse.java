package fintech2.easypay.payment.dto;

import fintech2.easypay.payment.entity.Payment;
import fintech2.easypay.payment.entity.PaymentMethod;
import fintech2.easypay.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private Long id;
    private String paymentId;
    private String phoneNumber;
    private String accountNumber;
    private String merchantId;
    private String merchantName;
    private BigDecimal amount;
    private String memo;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime refundedAt;
    private String failedReason;
    private String pgTransactionId;
    
    public static PaymentResponse from(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentId(payment.getPaymentId())
                .phoneNumber(payment.getMember().getPhoneNumber())
                .accountNumber(payment.getAccountNumber())
                .merchantId(payment.getMerchantId())
                .merchantName(payment.getMerchantName())
                .amount(payment.getAmount())
                .memo(payment.getMemo())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .approvedAt(payment.getApprovedAt())
                .cancelledAt(payment.getCancelledAt())
                .refundedAt(payment.getRefundedAt())
                .failedReason(payment.getFailedReason())
                .pgTransactionId(payment.getPgTransactionId())
                .build();
    }
}
