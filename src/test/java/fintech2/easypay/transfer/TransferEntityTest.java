package fintech2.easypay.transfer;

import fintech2.easypay.member.entity.Member;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.entity.TransferStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Transfer 엔티티 테스트
 * 
 * 이 테스트는 Transfer 엔티티의 주요 기능들을 검증합니다:
 * - 송금 생성
 * - 상태 변경
 * - 송금 완료/실패/취소 처리
 * - 송금 상태 확인
 */
@DisplayName("송금 엔티티 테스트")
class TransferEntityTest {

    private Member sender;
    private Member receiver;
    private Transfer transfer;

    @BeforeEach
    void setUp() {
        // 송금자 생성
        sender = Member.builder()
                .phoneNumber("01012345678")
                .password("password123")
                .name("송금자")
                .build();

        // 수신자 생성
        receiver = Member.builder()
                .phoneNumber("01087654321")
                .password("password123")
                .name("수신자")
                .build();

        // 송금 생성
        transfer = Transfer.builder()
                .transactionId("TRF123456789")
                .sender(sender)
                .receiver(receiver)
                .senderAccountNumber("1111111111")
                .receiverAccountNumber("2222222222")
                .amount(BigDecimal.valueOf(10000))
                .status(TransferStatus.REQUESTED)
                .memo("테스트 송금")
                .build();
    }

    @Test
    @DisplayName("송금 생성 테스트")
    void createTransfer() {
        // given & when: 송금 생성 (setUp에서 생성됨)
        
        // then: 송금이 올바르게 생성되었는지 확인
        assertThat(transfer.getTransactionId()).isEqualTo("TRF123456789");
        assertThat(transfer.getSender()).isEqualTo(sender);
        assertThat(transfer.getReceiver()).isEqualTo(receiver);
        assertThat(transfer.getSenderAccountNumber()).isEqualTo("1111111111");
        assertThat(transfer.getReceiverAccountNumber()).isEqualTo("2222222222");
        assertThat(transfer.getAmount()).isEqualTo(BigDecimal.valueOf(10000));
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REQUESTED);
        assertThat(transfer.getMemo()).isEqualTo("테스트 송금");
    }

    @Test
    @DisplayName("송금 처리 중 상태 변경 테스트")
    void markAsProcessing() {
        // given: 요청 상태의 송금
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REQUESTED);

        // when: 처리 중 상태로 변경
        transfer.markAsProcessing();

        // then: 상태가 PROCESSING으로 변경
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PROCESSING);
    }

    @Test
    @DisplayName("송금 완료 상태 변경 테스트")
    void markAsCompleted() {
        // given: 처리 중 상태의 송금
        transfer.markAsProcessing();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PROCESSING);

        // when: 완료 상태로 변경
        transfer.markAsCompleted();

        // then: 상태가 COMPLETED로 변경되고 처리 시간이 설정됨
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(transfer.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("송금 실패 상태 변경 테스트")
    void markAsFailed() {
        // given: 처리 중 상태의 송금
        transfer.markAsProcessing();
        String failReason = "잔액 부족";

        // when: 실패 상태로 변경
        transfer.markAsFailed(failReason);

        // then: 상태가 FAILED로 변경되고 실패 이유가 설정됨
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.FAILED);
        assertThat(transfer.getFailedReason()).isEqualTo(failReason);
        assertThat(transfer.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("송금 취소 상태 변경 테스트")
    void markAsCancelled() {
        // given: 요청 상태의 송금
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REQUESTED);

        // when: 취소 상태로 변경
        transfer.markAsCancelled();

        // then: 상태가 CANCELLED로 변경되고 처리 시간이 설정됨
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELLED);
        assertThat(transfer.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("송금 완료 여부 확인 테스트")
    void isCompleted() {
        // given: 요청 상태의 송금
        assertThat(transfer.isCompleted()).isFalse();

        // when: 완료 상태로 변경
        transfer.markAsProcessing();
        transfer.markAsCompleted();

        // then: 완료 상태 확인
        assertThat(transfer.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("송금 실패 여부 확인 테스트")
    void isFailed() {
        // given: 요청 상태의 송금
        assertThat(transfer.isFailed()).isFalse();

        // when: 실패 상태로 변경
        transfer.markAsFailed("시스템 오류");

        // then: 실패 상태 확인
        assertThat(transfer.isFailed()).isTrue();
    }

    @Test
    @DisplayName("송금 취소 여부 확인 테스트")
    void isCancelled() {
        // given: 요청 상태의 송금
        assertThat(transfer.isCancelled()).isFalse();

        // when: 취소 상태로 변경
        transfer.markAsCancelled();

        // then: 취소 상태 확인
        assertThat(transfer.isCancelled()).isTrue();
    }

    @Test
    @DisplayName("송금 상태 변경 이력 테스트")
    void statusChangeHistory() {
        // given: 요청 상태의 송금
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REQUESTED);

        // when: 상태 변경 시퀀스 실행
        transfer.markAsProcessing();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PROCESSING);

        transfer.markAsCompleted();
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.COMPLETED);

        // then: 최종 상태 확인
        assertThat(transfer.isCompleted()).isTrue();
        assertThat(transfer.getProcessedAt()).isNotNull();
    }

    @Test
    @DisplayName("송금 실패 후 재처리 테스트")
    void failedTransferRetry() {
        // given: 실패한 송금
        transfer.markAsProcessing();
        transfer.markAsFailed("임시 오류");
        
        assertThat(transfer.isFailed()).isTrue();
        assertThat(transfer.getFailedReason()).isEqualTo("임시 오류");

        // when: 재처리 시도 (새로운 상태로 변경)
        transfer.markAsProcessing();

        // then: 다시 처리 중 상태가 됨
        assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PROCESSING);
        assertThat(transfer.isFailed()).isFalse();
    }

    @Test
    @DisplayName("송금 금액 검증 테스트")
    void transferAmountValidation() {
        // given: 송금 객체
        assertThat(transfer.getAmount()).isEqualTo(BigDecimal.valueOf(10000));

        // when & then: 금액이 양수인지 확인
        assertThat(transfer.getAmount().compareTo(BigDecimal.ZERO)).isGreaterThan(0);
    }

    @Test
    @DisplayName("송금 계좌 정보 검증 테스트")
    void transferAccountValidation() {
        // given: 송금 객체
        
        // when & then: 송금자와 수신자 계좌가 다른지 확인
        assertThat(transfer.getSenderAccountNumber()).isNotEqualTo(transfer.getReceiverAccountNumber());
        assertThat(transfer.getSender()).isNotEqualTo(transfer.getReceiver());
    }

    @Test
    @DisplayName("송금 처리 시간 검증 테스트")
    void transferProcessingTimeValidation() {
        // given: 초기 송금 (처리 시간 없음)
        assertThat(transfer.getProcessedAt()).isNull();

        // when: 완료 처리
        transfer.markAsProcessing();
        transfer.markAsCompleted();

        // then: 처리 시간이 설정됨
        assertThat(transfer.getProcessedAt()).isNotNull();
    }
}