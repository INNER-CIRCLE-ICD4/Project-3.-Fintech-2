package fintech2.easypay.transfer.command;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 내부 계좌 간 송금 커맨드
 * EasyPay 플랫폼 내부에서 발생하는 송금을 처리
 */
public final class InternalTransferCommand implements TransferActionCommand {
    
    private final Long senderId;
    private final Long receiverId;
    private final String senderAccountNumber;
    private final String receiverAccountNumber;
    private final BigDecimal amount;
    private final String memo;
    private final String transactionId;
    
    public InternalTransferCommand(Long senderId, Long receiverId, String senderAccountNumber, 
                                 String receiverAccountNumber, BigDecimal amount, String memo, 
                                 String transactionId) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.senderAccountNumber = senderAccountNumber;
        this.receiverAccountNumber = receiverAccountNumber;
        this.amount = amount;
        this.memo = memo;
        this.transactionId = transactionId;
    }
    
    // Getters
    public Long getSenderId() { return senderId; }
    public Long getReceiverId() { return receiverId; }
    public String getSenderAccountNumber() { return senderAccountNumber; }
    public String getReceiverAccountNumber() { return receiverAccountNumber; }
    public BigDecimal getAmount() { return amount; }
    public String getMemo() { return memo; }
    public String getTransactionId() { return transactionId; }
    
    @Override
    public String toString() {
        return "InternalTransferCommand{" +
               "senderId=" + senderId +
               ", receiverId=" + receiverId +
               ", senderAccount='" + senderAccountNumber + '\'' +
               ", receiverAccount='" + receiverAccountNumber + '\'' +
               ", amount=" + amount +
               ", transactionId='" + transactionId + '\'' +
               '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InternalTransferCommand)) return false;
        InternalTransferCommand that = (InternalTransferCommand) o;
        return Objects.equals(senderId, that.senderId) &&
               Objects.equals(receiverId, that.receiverId) &&
               Objects.equals(senderAccountNumber, that.senderAccountNumber) &&
               Objects.equals(receiverAccountNumber, that.receiverAccountNumber) &&
               Objects.equals(amount, that.amount) &&
               Objects.equals(transactionId, that.transactionId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(senderId, receiverId, senderAccountNumber, receiverAccountNumber, 
                          amount, transactionId);
    }
}