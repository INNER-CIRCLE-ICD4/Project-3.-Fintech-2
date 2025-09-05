package fintech2.easypay.transfer.service;

import fintech2.easypay.auth.repository.UserRepository;
import fintech2.easypay.transfer.action.ActionResult;
import fintech2.easypay.transfer.action.TransferActionProcessor;
import fintech2.easypay.transfer.dto.TransferRequest;
import fintech2.easypay.transfer.dto.TransferResponse;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.repository.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("송금 서비스 Action Pattern 테스트")
class TransferServiceScenarioTest {

    @Mock private TransferRepository transferRepository;
    @Mock private UserRepository userRepository;
    @Mock private TransferActionProcessor transferActionProcessor;

    private TransferService transferService;

    @BeforeEach
    void setUp() {
        transferService = new TransferService(
            transferRepository,
            userRepository,
            transferActionProcessor
        );
    }

    @Test
    @DisplayName("일반 송금 성공 시나리오 - Action Pattern 적용")
    void successfulTransferWithActionPattern() {
        // Given
        TransferRequest request = new TransferRequest(
            "123456789002", // receiverAccountNumber
            BigDecimal.valueOf(100000), // amount
            "테스트 송금", // memo
            null // senderAccountNumber (기본 계좌 사용)
        );
        
        String senderPhoneNumber = "01012345678";
        
        // Mock ActionResult
        ActionResult successResult = ActionResult.success("송금이 완료되었습니다", null);
        when(transferActionProcessor.process(any())).thenReturn(successResult);
        
        // Mock Transfer entity
        Transfer mockTransfer = Transfer.builder()
            .transactionId("TXN123456789")
            .senderAccountNumber("123456789001")
            .receiverAccountNumber("123456789002")
            .amount(BigDecimal.valueOf(100000))
            .memo("테스트 송금")
            .build();
        mockTransfer.markAsCompleted();
        
        when(transferRepository.findByTransactionId(anyString()))
            .thenReturn(java.util.Optional.of(mockTransfer));

        // When
        TransferResponse response = transferService.transfer(senderPhoneNumber, request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getAmount()).isEqualTo(BigDecimal.valueOf(100000));
        assertThat(response.getMemo()).isEqualTo("테스트 송금");
    }

    @Test
    @DisplayName("송금 실패 시나리오 - Action Pattern 적용")
    void failedTransferWithActionPattern() {
        // Given
        TransferRequest request = new TransferRequest(
            "123456789002",
            BigDecimal.valueOf(100000),
            "테스트 송금",
            null
        );
        
        String senderPhoneNumber = "01012345678";
        
        // Mock failure ActionResult
        ActionResult failureResult = ActionResult.failure("INSUFFICIENT_BALANCE", 
            "잔액이 부족합니다", null);
        when(transferActionProcessor.process(any())).thenReturn(failureResult);
        
        // Mock Transfer entity (실패 상태)
        Transfer mockTransfer = Transfer.builder()
            .transactionId("TXN123456789")
            .senderAccountNumber("123456789001")
            .receiverAccountNumber("123456789002")
            .amount(BigDecimal.valueOf(100000))
            .memo("테스트 송금")
            .build();
        mockTransfer.markAsFailed("잔액이 부족합니다");
        
        when(transferRepository.findByTransactionId(anyString()))
            .thenReturn(java.util.Optional.of(mockTransfer));

        // When & Then
        try {
            transferService.transfer(senderPhoneNumber, request);
        } catch (Exception e) {
            assertThat(e).hasMessageContaining("잔액이 부족합니다");
        }
    }
}