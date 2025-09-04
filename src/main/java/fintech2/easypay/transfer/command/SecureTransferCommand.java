package fintech2.easypay.transfer.command;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * PIN 검증이 필요한 보안 송금 커맨드
 * 민감한 송금 작업에서 추가 인증이 완료된 경우 사용
 */
public final class SecureTransferCommand implements TransferActionCommand {
    
    private final Long senderId;
    private final Long receiverId;
    private final String senderAccountNumber;
    private final String receiverAccountNumber;
    private final BigDecimal amount;
    private final String memo;
    private final String transactionId;
    private final String pinSessionToken;
    private final boolean isExternal;
    private final String receiverBankCode;
    
    public SecureTransferCommand(Long senderId, Long receiverId, String senderAccountNumber,
                               String receiverAccountNumber, BigDecimal amount, String memo,
                               String transactionId, String pinSessionToken, boolean isExternal,
                               String receiverBankCode) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.senderAccountNumber = senderAccountNumber;
        this.receiverAccountNumber = receiverAccountNumber;
        this.amount = amount;
        this.memo = memo;
        this.transactionId = transactionId;
        this.pinSessionToken = pinSessionToken;
        this.isExternal = isExternal;
        this.receiverBankCode = receiverBankCode;
    }
    
    // Getters
    public Long getSenderId() { return senderId; }
    public Long getReceiverId() { return receiverId; }
    public String getSenderAccountNumber() { return senderAccountNumber; }
    public String getReceiverAccountNumber() { return receiverAccountNumber; }
    public BigDecimal getAmount() { return amount; }
    public String getMemo() { return memo; }
    public String getTransactionId() { return transactionId; }
    public String getPinSessionToken() { return pinSessionToken; }
    public boolean isExternal() { return isExternal; }
    public String getReceiverBankCode() { return receiverBankCode; }
    
    @Override
    public String toString() {
        return "SecureTransferCommand{" +
               "senderId=" + senderId +
               ", receiverId=" + receiverId +
               ", senderAccount='" + senderAccountNumber + '\'' +
               ", receiverAccount='" + receiverAccountNumber + '\'' +
               ", amount=" + amount +
               ", isExternal=" + isExternal +
               ", transactionId='" + transactionId + '\'' +
               '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecureTransferCommand)) return false;
        SecureTransferCommand that = (SecureTransferCommand) o;
        return isExternal == that.isExternal &&
               Objects.equals(senderId, that.senderId) &&
               Objects.equals(receiverId, that.receiverId) &&
               Objects.equals(senderAccountNumber, that.senderAccountNumber) &&
               Objects.equals(receiverAccountNumber, that.receiverAccountNumber) &&
               Objects.equals(amount, that.amount) &&
               Objects.equals(transactionId, that.transactionId) &&
               Objects.equals(receiverBankCode, that.receiverBankCode);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(senderId, receiverId, senderAccountNumber, receiverAccountNumber,
                          amount, transactionId, isExternal, receiverBankCode);
    }
}