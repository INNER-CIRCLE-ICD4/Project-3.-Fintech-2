package fintech2.easypay.payment.external;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * PG API 응답 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PgApiResponse {
    private String paymentId;           // 결제 ID
    private String pgTransactionId;     // PG사 거래 ID
    private PgApiStatus status;         // 처리 상태
    private String message;             // 응답 메시지
    private LocalDateTime processedAt;  // 처리 시간
    private BigDecimal approvedAmount;  // 승인 금액
    private String approvalNumber;      // 승인 번호
    private String cardCompany;         // 카드사 (카드 결제인 경우)
    private String cardNumber;          // 마스킹된 카드번호
    private String bankName;            // 은행명 (계좌이체인 경우)
    private String errorCode;           // 오류 코드
    private String errorMessage;        // 오류 메시지
    private String rawResponse;         // 원본 응답 (JSON)
}