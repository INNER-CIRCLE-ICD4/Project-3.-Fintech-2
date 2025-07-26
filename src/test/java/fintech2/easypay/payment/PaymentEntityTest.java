package fintech2.easypay.payment;

import fintech2.easypay.member.entity.Member;
import fintech2.easypay.payment.entity.Payment;
import fintech2.easypay.payment.entity.PaymentMethod;
import fintech2.easypay.payment.entity.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Payment 엔티티 테스트
 * 
 * 이 테스트는 Payment 엔티티의 주요 기능들을 검증합니다:
 * - 결제 생성
 * - 상태 변경
 * - 결제 승인/실패/취소/환불 처리
 * - 결제 상태 확인
 * - 취소/환불 가능 여부 확인
 */
@DisplayName("결제 엔티티 테스트")
class PaymentEntityTest {

    private Member testMember;
    private Payment payment;

    @BeforeEach
    void setUp() {
        // 테스트용 회원 생성
        testMember = Member.builder()
                .phoneNumber("01012345678")
                .password("password123")
                .name("홍길동")
                .build();

        // 테스트용 결제 생성
        payment = Payment.builder()
                .paymentId("PAY123456789")
                .member(testMember)
                .accountNumber("1234567890")
                .merchantId("MERCHANT001")
                .merchantName("테스트 가맹점")
                .amount(BigDecimal.valueOf(10000))
                .paymentMethod(PaymentMethod.BALANCE)
                .status(PaymentStatus.REQUESTED)
                .memo("테스트 결제")
                .build();
    }

    @Test
    @DisplayName("결제 생성 테스트")
    void createPayment() {
        // given & when: 결제 생성 (setUp에서 생성됨)
        
        // then: 결제가 올바르게 생성되었는지 확인
        assertThat(payment.getPaymentId()).isEqualTo("PAY123456789");
        assertThat(payment.getMember()).isEqualTo(testMember);
        assertThat(payment.getAccountNumber()).isEqualTo("1234567890");
        assertThat(payment.getMerchantId()).isEqualTo("MERCHANT001");
        assertThat(payment.getMerchantName()).isEqualTo("테스트 가맹점");
        assertThat(payment.getAmount()).isEqualTo(BigDecimal.valueOf(10000));
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.BALANCE);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);
        assertThat(payment.getMemo()).isEqualTo("테스트 결제");
    }

    @Test
    @DisplayName("결제 처리 중 상태 변경 테스트")
    void markAsProcessing() {
        // given: 요청 상태의 결제
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        // when: 처리 중 상태로 변경
        payment.markAsProcessing();

        // then: 상태가 PROCESSING으로 변경
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    @DisplayName("결제 승인 상태 변경 테스트")
    void markAsApproved() {
        // given: 처리 중 상태의 결제
        payment.markAsProcessing();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        // when: 승인 상태로 변경
        payment.markAsApproved("PG_TXN_123", "{'result':'success'}");

        // then: 상태가 APPROVED로 변경되고 승인 시간이 설정됨
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getApprovedAt()).isNotNull();
        assertThat(payment.getPgTransactionId()).isEqualTo("PG_TXN_123");
        assertThat(payment.getPgResponse()).isEqualTo("{'result':'success'}");
    }

    @Test
    @DisplayName("결제 실패 상태 변경 테스트")
    void markAsFailed() {
        // given: 처리 중 상태의 결제
        payment.markAsProcessing();
        String failReason = "잔액 부족";

        // when: 실패 상태로 변경
        payment.markAsFailed(failReason);

        // then: 상태가 FAILED로 변경되고 실패 이유가 설정됨
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getFailedReason()).isEqualTo(failReason);
    }

    @Test
    @DisplayName("결제 취소 상태 변경 테스트")
    void markAsCancelled() {
        // given: 승인된 결제
        payment.markAsProcessing();
        payment.markAsApproved("PG_TXN_123", "{'result':'success'}");

        // when: 취소 상태로 변경
        payment.markAsCancelled();

        // then: 상태가 CANCELLED로 변경되고 취소 시간이 설정됨
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 환불 상태 변경 테스트")
    void markAsRefunded() {
        // given: 승인된 결제
        payment.markAsProcessing();
        payment.markAsApproved("PG_TXN_123", "{'result':'success'}");

        // when: 환불 상태로 변경
        payment.markAsRefunded();

        // then: 상태가 REFUNDED로 변경되고 환불 시간이 설정됨
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 승인 여부 확인 테스트")
    void isApproved() {
        // given: 요청 상태의 결제
        assertThat(payment.isApproved()).isFalse();

        // when: 승인 상태로 변경
        payment.markAsProcessing();
        payment.markAsApproved("PG_TXN_123", "{'result':'success'}");

        // then: 승인 상태 확인
        assertThat(payment.isApproved()).isTrue();
    }

    @Test
    @DisplayName("결제 실패 여부 확인 테스트")
    void isFailed() {
        // given: 요청 상태의 결제
        assertThat(payment.isFailed()).isFalse();

        // when: 실패 상태로 변경
        payment.markAsFailed("시스템 오류");

        // then: 실패 상태 확인
        assertThat(payment.isFailed()).isTrue();
    }

    @Test
    @DisplayName("결제 취소 여부 확인 테스트")
    void isCancelled() {
        // given: 승인된 결제
        payment.markAsProcessing();
        payment.markAsApproved("PG_TXN_123", "{'result':'success'}");
        assertThat(payment.isCancelled()).isFalse();

        // when: 취소 상태로 변경
        payment.markAsCancelled();

        // then: 취소 상태 확인
        assertThat(payment.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("결제 환불 여부 확인 테스트")
    void isRefunded() {
        // given: 승인된 결제
        payment.markAsProcessing();
        payment.markAsApproved("PG_TXN_123", "{'result':'success'}");
        assertThat(payment.isRefunded()).isFalse();

        // when: 환불 상태로 변경
        payment.markAsRefunded();

        // then: 환불 상태 확인
        assertThat(payment.isRefunded()).isTrue();
    }

    @Test
    @DisplayName("결제 취소 가능 여부 확인 테스트")
    void canBeCancelled() {
        // given: 요청 상태의 결제
        assertThat(payment.canBeCancelled()).isFalse(); // 요청 상태에서는 취소 불가

        // when: 승인 상태로 변경
        payment.markAsProcessing();
        payment.markAsApproved("PG_TXN_123", "{'result':'success'}");

        // then: 승인 상태에서는 취소 가능
        assertThat(payment.canBeCancelled()).isTrue();

        // when: 이미 취소된 상태
        payment.markAsCancelled();

        // then: 이미 취소된 결제는 재취소 불가
        assertThat(payment.canBeCancelled()).isFalse();
    }

    @Test
    @DisplayName("결제 환불 가능 여부 확인 테스트")
    void canBeRefunded() {
        // given: 요청 상태의 결제
        assertThat(payment.canBeRefunded()).isFalse(); // 요청 상태에서는 환불 불가

        // when: 승인 상태로 변경
        payment.markAsProcessing();
        payment.markAsApproved("PG_TXN_123", "{'result':'success'}");

        // then: 승인 상태에서는 환불 가능
        assertThat(payment.canBeRefunded()).isTrue();

        // when: 이미 환불된 상태
        payment.markAsRefunded();

        // then: 이미 환불된 결제는 재환불 불가
        assertThat(payment.canBeRefunded()).isFalse();
    }

    @Test
    @DisplayName("결제 상태 변경 이력 테스트")
    void statusChangeHistory() {
        // given: 요청 상태의 결제
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REQUESTED);

        // when: 상태 변경 시퀀스 실행
        payment.markAsProcessing();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        payment.markAsApproved("PG_TXN_123", "{'result':'success'}");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);

        payment.markAsCancelled();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        // then: 최종 상태 확인
        assertThat(payment.isCancelled()).isTrue();
        assertThat(payment.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("결제 금액 검증 테스트")
    void paymentAmountValidation() {
        // given: 결제 객체
        assertThat(payment.getAmount()).isEqualTo(BigDecimal.valueOf(10000));

        // when & then: 금액이 양수인지 확인
        assertThat(payment.getAmount().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    @DisplayName("결제 방법별 처리 테스트")
    void paymentMethodProcessing() {
        // given: 잔액 결제
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.BALANCE);

        // when: PG 결제로 변경
        Payment pgPayment = Payment.builder()
                .paymentId("PAY987654321")
                .member(testMember)
                .accountNumber("1234567890")
                .merchantId("MERCHANT002")
                .merchantName("PG 테스트 가맹점")
                .amount(BigDecimal.valueOf(20000))
                .paymentMethod(PaymentMethod.PG_GATEWAY)
                .status(PaymentStatus.REQUESTED)
                .build();

        // then: 결제 방법이 올바르게 설정됨
        assertThat(pgPayment.getPaymentMethod()).isEqualTo(PaymentMethod.PG_GATEWAY);
    }

    @Test
    @DisplayName("결제 실패 후 재처리 테스트")
    void failedPaymentRetry() {
        // given: 실패한 결제
        payment.markAsProcessing();
        payment.markAsFailed("임시 오류");
        
        assertThat(payment.isFailed()).isTrue();
        assertThat(payment.getFailedReason()).isEqualTo("임시 오류");

        // when: 재처리 시도 (새로운 상태로 변경)
        payment.markAsProcessing();

        // then: 다시 처리 중 상태가 됨
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(payment.isFailed()).isFalse();
    }
}