package fintech2.easypay.transfer.action.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 내부 계좌 간 송금 명령
 * EasyPay 시스템 내부 계좌 간의 송금 처리를 위한 명령 객체
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public final class InternalTransferCommand implements TransferActionCommand {
    
    /**
     * 송금자 휴대폰 번호
     */
    private String senderPhoneNumber;
    
    /**
     * 송금자 계좌번호 (지정된 경우만, null이면 기본 계좌 사용)
     */
    private String senderAccountNumber;
    
    /**
     * 수신자 계좌번호
     */
    private String receiverAccountNumber;
    
    /**
     * 송금 금액
     */
    private BigDecimal amount;
    
    /**
     * 송금 메모
     */
    private String memo;
    
    /**
     * 거래 ID (생성되는 경우)
     */
    private String transactionId;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InternalTransferCommand that)) return false;
        return Objects.equals(senderPhoneNumber, that.senderPhoneNumber) &&
               Objects.equals(senderAccountNumber, that.senderAccountNumber) &&
               Objects.equals(receiverAccountNumber, that.receiverAccountNumber) &&
               Objects.equals(amount, that.amount) &&
               Objects.equals(memo, that.memo) &&
               Objects.equals(transactionId, that.transactionId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(senderPhoneNumber, senderAccountNumber, 
                          receiverAccountNumber, amount, memo, transactionId);
    }
    
    @Override
    public String toString() {
        return "InternalTransferCommand{" +
               "senderPhone=" + senderPhoneNumber +
               ", senderAccount=" + senderAccountNumber +
               ", receiverAccount=" + receiverAccountNumber +
               ", amount=" + amount +
               ", memo='" + memo + '\'' +
               ", transactionId='" + transactionId + '\'' +
               '}';
    }
}