package fintech2.easypay.transfer.command;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 외부 은행으로의 송금 커맨드
 * EasyPay에서 다른 은행으로의 송금을 처리
 */
public final class ExternalTransferCommand implements TransferActionCommand {
    
    private final Long senderId;
    private final String senderAccountNumber;
    private final String receiverAccountNumber;
    private final String receiverBankCode;
    private final String receiverBankName;
    private final BigDecimal amount;
    private final String memo;
    private final String transactionId;
    
    public ExternalTransferCommand(Long senderId, String senderAccountNumber, String receiverAccountNumber,
                                 String receiverBankCode, String receiverBankName, BigDecimal amount, 
                                 String memo, String transactionId) {
        this.senderId = senderId;
        this.senderAccountNumber = senderAccountNumber;
        this.receiverAccountNumber = receiverAccountNumber;
        this.receiverBankCode = receiverBankCode;
        this.receiverBankName = receiverBankName;
        this.amount = amount;
        this.memo = memo;
        this.transactionId = transactionId;
    }
    
    // Getters
    public Long getSenderId() { return senderId; }
    public String getSenderAccountNumber() { return senderAccountNumber; }
    public String getReceiverAccountNumber() { return receiverAccountNumber; }
    public String getReceiverBankCode() { return receiverBankCode; }
    public String getReceiverBankName() { return receiverBankName; }
    public BigDecimal getAmount() { return amount; }
    public String getMemo() { return memo; }
    public String getTransactionId() { return transactionId; }
    
    @Override
    public String toString() {
        return "ExternalTransferCommand{" +
               "senderId=" + senderId +
               ", senderAccount='" + senderAccountNumber + '\'' +
               ", receiverAccount='" + receiverAccountNumber + '\'' +
               ", receiverBank='" + receiverBankName + '\'' +
               ", amount=" + amount +
               ", transactionId='" + transactionId + '\'' +
               '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExternalTransferCommand)) return false;
        ExternalTransferCommand that = (ExternalTransferCommand) o;
        return Objects.equals(senderId, that.senderId) &&
               Objects.equals(senderAccountNumber, that.senderAccountNumber) &&
               Objects.equals(receiverAccountNumber, that.receiverAccountNumber) &&
               Objects.equals(receiverBankCode, that.receiverBankCode) &&
               Objects.equals(amount, that.amount) &&
               Objects.equals(transactionId, that.transactionId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(senderId, senderAccountNumber, receiverAccountNumber, 
                          receiverBankCode, amount, transactionId);
    }
}