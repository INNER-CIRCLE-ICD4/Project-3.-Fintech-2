package fintech2.easypay.transfer.action.command;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * 보안 송금 명령 (PIN 검증 포함)
 * PIN 인증을 통한 보안 송금 처리를 위한 명령 객체
 * PIN 세션 토큰 검증 후 내부/외부 송금으로 위임
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public final class SecureTransferCommand implements TransferActionCommand {
    
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
     * 수신자 은행 코드 (내부 송금인 경우 "EASYPAY")
     */
    @Builder.Default
    private String receiverBankCode = "EASYPAY";
    
    /**
     * 송금 금액
     */
    private BigDecimal amount;
    
    /**
     * 송금 메모
     */
    private String memo;
    
    /**
     * PIN 세션 토큰
     */
    private String pinSessionToken;
    
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
    
    /**
     * 내부 송금 여부 판단
     * @return EASYPAY 은행 코드인 경우 true
     */
    public boolean isInternalTransfer() {
        return "EASYPAY".equals(receiverBankCode);
    }
    
    /**
     * 내부 송금 명령으로 변환
     * @return InternalTransferCommand 인스턴스
     */
    public InternalTransferCommand toInternalTransferCommand() {
        return InternalTransferCommand.builder()
                .senderPhoneNumber(senderPhoneNumber)
                .senderAccountNumber(senderAccountNumber)
                .receiverAccountNumber(receiverAccountNumber)
                .amount(amount)
                .memo(memo)
                .transactionId(transactionId)
                .build();
    }
    
    /**
     * 외부 송금 명령으로 변환
     * @return ExternalTransferCommand 인스턴스
     */
    public ExternalTransferCommand toExternalTransferCommand() {
        return ExternalTransferCommand.builder()
                .senderPhoneNumber(senderPhoneNumber)
                .senderAccountNumber(senderAccountNumber)
                .receiverAccountNumber(receiverAccountNumber)
                .receiverBankCode(receiverBankCode)
                .amount(amount)
                .memo(memo)
                .currency(currency)
                .transactionId(transactionId)
                .bankTransactionId(bankTransactionId)
                .build();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecureTransferCommand that)) return false;
        return Objects.equals(senderPhoneNumber, that.senderPhoneNumber) &&
               Objects.equals(senderAccountNumber, that.senderAccountNumber) &&
               Objects.equals(receiverAccountNumber, that.receiverAccountNumber) &&
               Objects.equals(receiverBankCode, that.receiverBankCode) &&
               Objects.equals(amount, that.amount) &&
               Objects.equals(memo, that.memo) &&
               Objects.equals(pinSessionToken, that.pinSessionToken) &&
               Objects.equals(currency, that.currency) &&
               Objects.equals(transactionId, that.transactionId) &&
               Objects.equals(bankTransactionId, that.bankTransactionId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(senderPhoneNumber, senderAccountNumber, receiverAccountNumber,
                          receiverBankCode, amount, memo, pinSessionToken, currency, 
                          transactionId, bankTransactionId);
    }
    
    @Override
    public String toString() {
        return "SecureTransferCommand{" +
               "senderPhone=" + senderPhoneNumber +
               ", senderAccount=" + senderAccountNumber +
               ", receiverAccount=" + receiverAccountNumber +
               ", receiverBank=" + receiverBankCode +
               ", amount=" + amount +
               ", memo='" + memo + '\'' +
               ", pinSessionToken='***'" + // 보안을 위해 토큰 값은 숨김
               ", currency='" + currency + '\'' +
               ", transactionId='" + transactionId + '\'' +
               ", bankTransactionId='" + bankTransactionId + '\'' +
               '}';
    }
}