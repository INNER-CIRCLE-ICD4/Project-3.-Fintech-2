package fintech2.easypay.transfer.dto;

import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.entity.TransferStatus;
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
public class TransferResponse {
    private Long id;
    private String transactionId;
    private String senderPhoneNumber;
    private String senderAccountNumber;
    private String receiverPhoneNumber;
    private String receiverAccountNumber;
    private BigDecimal amount;
    private String memo;
    private TransferStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String failedReason;
    private String bankTransactionId;
    
    public static TransferResponse from(Transfer transfer) {
        return TransferResponse.builder()
                .id(transfer.getId())
                .transactionId(transfer.getTransactionId())
                .senderPhoneNumber(transfer.getSender().getPhoneNumber())
                .senderAccountNumber(transfer.getSenderAccountNumber())
                .receiverPhoneNumber(transfer.getReceiver().getPhoneNumber())
                .receiverAccountNumber(transfer.getReceiverAccountNumber())
                .amount(transfer.getAmount())
                .memo(transfer.getMemo())
                .status(transfer.getStatus())
                .createdAt(transfer.getCreatedAt())
                .processedAt(transfer.getProcessedAt())
                .failedReason(transfer.getFailedReason())
                .bankTransactionId(transfer.getBankTransactionId())
                .build();
    }
}
