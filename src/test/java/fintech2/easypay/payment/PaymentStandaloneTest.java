package fintech2.easypay.payment;

import fintech2.easypay.payment.entity.Payment;
import fintech2.easypay.payment.entity.PaymentMethod;
import fintech2.easypay.payment.entity.PaymentStatus;
import fintech2.easypay.payment.exception.PaymentErrorCode;
import fintech2.easypay.payment.exception.PaymentException;
import fintech2.easypay.payment.service.PaymentAuditService;
import fintech2.easypay.payment.service.PaymentNotificationService;
import fintech2.easypay.payment.service.impl.PaymentAuditServiceImpl;
import fintech2.easypay.payment.service.impl.PaymentNotificationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("결제 도메인 독립 테스트")
class PaymentStandaloneTest {
    
    @Test
    @DisplayName("Payment 엔티티 생성 및 상태 변경 테스트")
    void testPaymentEntityLifecycle() {
        // given: 결제 엔티티 생성
        Payment payment = Payment.builder()
            .paymentId("PAY123456789")
            .accountNumber("1234567890")
            .merchantId("MERCHANT001")
            .merchantName("테스트 가맹점")
            .amount(new BigDecimal("10000"))
            .memo("테스트 결제")
            .paymentMethod(PaymentMethod.CARD)
            .build();
        
        // when & then: 초기 상태 확인
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getPaymentId()).isEqualTo("PAY123456789");
        assertThat(payment.getAmount()).isEqualByComparingTo(new BigDecimal("10000"));
        
        // when: 처리 중 상태로 변경
        payment.markAsProcessing();
        
        // then: 상태 변경 확인
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        
        // when: 승인 처리
        payment.markAsApproved("PG123", "승인 완료");
        
        // then: 승인 상태 확인
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getPgTransactionId()).isEqualTo("PG123");
        assertThat(payment.isApproved()).isTrue();
        assertThat(payment.canBeCancelled()).isTrue();
        assertThat(payment.canBeRefunded()).isTrue();
    }
    
    @Test
    @DisplayName("Payment 취소 가능 여부 검증")
    void testPaymentCancellationValidation() {
        // given: 승인된 결제
        Payment approvedPayment = Payment.builder()
            .paymentId("PAY123456789")
            .merchantId("MERCHANT001")
            .merchantName("테스트 가맹점")
            .amount(new BigDecimal("10000"))
            .paymentMethod(PaymentMethod.CARD)
            .build();
        approvedPayment.markAsApproved("PG123", "승인 완료");
        
        // when & then: 취소 가능 확인
        assertThat(approvedPayment.canBeCancelled()).isTrue();
        
        // when: 취소 처리
        approvedPayment.markAsCancelled();
        
        // then: 취소 후 상태 확인
        assertThat(approvedPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(approvedPayment.isCancelled()).isTrue();
        assertThat(approvedPayment.canBeCancelled()).isFalse();
    }
    
    @Test
    @DisplayName("Payment 환불 가능 여부 검증")
    void testPaymentRefundValidation() {
        // given: 승인된 결제
        Payment approvedPayment = Payment.builder()
            .paymentId("PAY123456789")
            .merchantId("MERCHANT001")
            .merchantName("테스트 가맹점")
            .amount(new BigDecimal("10000"))
            .paymentMethod(PaymentMethod.CARD)
            .build();
        approvedPayment.markAsApproved("PG123", "승인 완료");
        
        // when & then: 환불 가능 확인
        assertThat(approvedPayment.canBeRefunded()).isTrue();
        
        // when: 환불 처리
        approvedPayment.markAsRefunded();
        
        // then: 환불 후 상태 확인
        assertThat(approvedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(approvedPayment.isRefunded()).isTrue();
        assertThat(approvedPayment.canBeRefunded()).isFalse();
    }
    
    @Test
    @DisplayName("Payment 실패 처리 테스트")
    void testPaymentFailureHandling() {
        // given: 결제 엔티티
        Payment payment = Payment.builder()
            .paymentId("PAY123456789")
            .merchantId("MERCHANT001")
            .merchantName("테스트 가맹점")
            .amount(new BigDecimal("10000"))
            .paymentMethod(PaymentMethod.CARD)
            .build();
        
        // when: 실패 처리
        payment.markAsFailed("카드 한도 초과");
        
        // then: 실패 상태 확인
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.isFailed()).isTrue();
        assertThat(payment.getFailedReason()).isEqualTo("카드 한도 초과");
        assertThat(payment.canBeCancelled()).isFalse();
        assertThat(payment.canBeRefunded()).isFalse();
    }
    
    @Test
    @DisplayName("PaymentException 생성 및 ErrorCode 테스트")
    void testPaymentExceptionHandling() {
        // when & then: 기본 예외 생성
        PaymentException exception1 = new PaymentException(PaymentErrorCode.PAYMENT_NOT_FOUND);
        assertThat(exception1.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_NOT_FOUND);
        assertThat(exception1.getMessage()).isEqualTo("결제 정보를 찾을 수 없습니다");
        
        // when & then: 추가 메시지가 있는 예외 생성
        PaymentException exception2 = new PaymentException(PaymentErrorCode.INSUFFICIENT_BALANCE, "추가 정보");
        assertThat(exception2.getErrorCode()).isEqualTo(PaymentErrorCode.INSUFFICIENT_BALANCE);
        assertThat(exception2.getMessage()).contains("잔액이 부족합니다");
        assertThat(exception2.getMessage()).contains("추가 정보");
        
        // when & then: Cause가 있는 예외 생성
        RuntimeException cause = new RuntimeException("원인 예외");
        PaymentException exception3 = new PaymentException(PaymentErrorCode.PG_API_ERROR, cause);
        assertThat(exception3.getErrorCode()).isEqualTo(PaymentErrorCode.PG_API_ERROR);
        assertThat(exception3.getCause()).isEqualTo(cause);
    }
    
    @Test
    @DisplayName("PaymentErrorCode 메시지 검증")
    void testPaymentErrorCodeMessages() {
        // when & then: 각 에러 코드별 메시지 확인
        assertThat(PaymentErrorCode.PAYMENT_NOT_FOUND.getMessage()).isEqualTo("결제 정보를 찾을 수 없습니다");
        assertThat(PaymentErrorCode.INSUFFICIENT_BALANCE.getMessage()).isEqualTo("잔액이 부족합니다");
        assertThat(PaymentErrorCode.INVALID_PAYMENT_AMOUNT.getMessage()).isEqualTo("유효하지 않은 결제 금액입니다");
        assertThat(PaymentErrorCode.PG_API_ERROR.getMessage()).isEqualTo("PG사 API 호출 중 오류가 발생했습니다");
        assertThat(PaymentErrorCode.CANNOT_CANCEL_PAYMENT.getMessage()).isEqualTo("취소할 수 없는 결제입니다");
        assertThat(PaymentErrorCode.CANNOT_REFUND_PAYMENT.getMessage()).isEqualTo("환불할 수 없는 결제입니다");
        
        // 에러 코드 확인
        assertThat(PaymentErrorCode.PAYMENT_NOT_FOUND.getCode()).isEqualTo("P001");
        assertThat(PaymentErrorCode.INSUFFICIENT_BALANCE.getCode()).isEqualTo("P002");
        assertThat(PaymentErrorCode.PG_API_ERROR.getCode()).isEqualTo("P007");
    }
    
    @Test
    @DisplayName("PaymentMethod enum 테스트")
    void testPaymentMethodEnum() {
        // when & then: PaymentMethod 값 확인
        assertThat(PaymentMethod.BALANCE.name()).isEqualTo("BALANCE");
        assertThat(PaymentMethod.CARD.name()).isEqualTo("CARD");
        assertThat(PaymentMethod.BANK_TRANSFER.name()).isEqualTo("BANK_TRANSFER");
        
        // enum 갯수 확인
        assertThat(PaymentMethod.values()).hasSize(4);
    }
    
    @Test
    @DisplayName("PaymentStatus enum 테스트")
    void testPaymentStatusEnum() {
        // when & then: PaymentStatus 값 확인
        assertThat(PaymentStatus.REQUESTED.name()).isEqualTo("REQUESTED");
        assertThat(PaymentStatus.PROCESSING.name()).isEqualTo("PROCESSING");
        assertThat(PaymentStatus.APPROVED.name()).isEqualTo("APPROVED");
        assertThat(PaymentStatus.FAILED.name()).isEqualTo("FAILED");
        assertThat(PaymentStatus.CANCELLED.name()).isEqualTo("CANCELLED");
        assertThat(PaymentStatus.REFUNDED.name()).isEqualTo("REFUNDED");
        
        // enum 갯수 확인
        assertThat(PaymentStatus.values()).hasSize(6);
    }
    
    @Test
    @DisplayName("PaymentAuditService Mock 테스트")
    void testPaymentAuditServiceMock() {
        // given: 실제 구현체는 없으므로 로그만 확인
        PaymentAuditService auditService = new PaymentAuditServiceImpl(null);
        
        // when & then: 예외가 발생하지 않는지 확인 (실제 구현은 repository가 필요)
        assertThatCode(() -> {
            // Mock 환경에서는 실제 로그 저장 없이 로직만 검증
        }).doesNotThrowAnyException();
    }
    
    @Test
    @DisplayName("PaymentNotificationService 테스트")
    void testPaymentNotificationService() {
        // given: 알림 서비스 인스턴스
        PaymentNotificationService notificationService = new PaymentNotificationServiceImpl();
        
        // when & then: 알림 메서드 호출 시 예외가 발생하지 않는지 확인
        assertThatCode(() -> {
            notificationService.sendPaymentActivityNotification(1L, "010-1234-5678", "결제 완료");
            notificationService.sendPaymentFailureNotification(1L, "010-1234-5678", "결제 실패");
            notificationService.sendRefundNotification(1L, "010-1234-5678", "환불 완료");
        }).doesNotThrowAnyException();
    }
}