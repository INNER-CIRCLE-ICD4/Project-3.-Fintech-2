package fintech2.easypay.payment.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * PG API 요청 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PgApiRequest {
    private String paymentId;           // 결제 ID
    private String merchantId;          // 가맹점 ID
    private String merchantName;        // 가맹점명
    private BigDecimal amount;          // 결제 금액
    private String currency;            // 통화 (KRW)
    private String paymentMethod;       // 결제 수단 (CARD, BANK_TRANSFER, etc.)
    private String customerName;        // 고객명
    private String customerPhone;       // 고객 전화번호
    private String productName;         // 상품명
    private String orderNumber;         // 주문번호
    private Map<String, String> cardInfo; // 카드 정보 (카드번호, 유효기간, CVV 등)
    private Map<String, String> accountInfo; // 계좌 정보 (은행코드, 계좌번호 등)
    private String returnUrl;           // 결제 완료 후 리턴 URL
    private String callbackUrl;         // 결제 결과 콜백 URL
}