package fintech2.easypay.transfer.action.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 외부 은행 송금 명령
 * 외부 은행 시스템으로의 송금 처리를 위한 명령 객체
 * 외부 API 호출이 필요한 송금 처리
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public final class ExternalTransferCommand implements TransferActionCommand {
    
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
     * 수신자 은행 코드
     */
    private String receiverBankCode;
    
    /**
     * 송금 금액
     */
    private BigDecimal amount;
    
    /**
     * 송금 메모
     */
    private String memo;
    
    /**
     * 통화 코드 (기본값: KRW)
     */
    @Builder.Default
    private String currency = "KRW";
    
    /**
     * 거래 ID (생성되는 경우)
     */
    private String transactionId;
    
    /**
     * 외부 은행 거래 ID
     */
    private String bankTransactionId;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExternalTransferCommand that)) return false;
        return Objects.equals(senderPhoneNumber, that.senderPhoneNumber) &&
               Objects.equals(senderAccountNumber, that.senderAccountNumber) &&
               Objects.equals(receiverAccountNumber, that.receiverAccountNumber) &&
               Objects.equals(receiverBankCode, that.receiverBankCode) &&
               Objects.equals(amount, that.amount) &&
               Objects.equals(memo, that.memo) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(transactionId, that.transactionId) &&
               Objects.equals(bankTransactionId, that.bankTransactionId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(senderPhoneNumber, senderAccountNumber, receiverAccountNumber,
                          receiverBankCode, amount, memo, currency, transactionId, bankTransactionId);
    }
    
    @Override
    public String toString() {
        return "ExternalTransferCommand{" +
               "senderPhone=" + senderPhoneNumber +
               ", senderAccount=" + senderAccountNumber +
               ", receiverAccount=" + receiverAccountNumber +
               ", receiverBank=" + receiverBankCode +
               ", amount=" + amount +
               ", memo='" + memo + '\'' +
               ", currency='" + currency + '\'' +
               ", transactionId='" + transactionId + '\'' +
               ", bankTransactionId='" + bankTransactionId + '\'' +
               '}';
    }
}