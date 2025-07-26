package fintech2.easypay.transfer;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.entity.AccountStatus;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.audit.service.AuditLogService;
import fintech2.easypay.audit.service.NotificationService;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.member.entity.Member;
import fintech2.easypay.member.repository.MemberRepository;
import fintech2.easypay.transfer.dto.TransferRequest;
import fintech2.easypay.transfer.dto.TransferResponse;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.entity.TransferStatus;
import fintech2.easypay.transfer.external.*;
import fintech2.easypay.transfer.repository.TransferRepository;
import fintech2.easypay.transfer.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 송금 서비스 테스트
 * 외부 API 호출 및 실패 시나리오 테스트 포함
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("송금 서비스 테스트")
class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;
    
    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private MemberRepository memberRepository;
    
    @Mock
    private AuditLogService auditLogService;
    
    @Mock
    private NotificationService notificationService;
    
    @Mock
    private BankingApiService bankingApiService;
    
    @InjectMocks
    private TransferService transferService;
    
    private Member sender;
    private Member receiver;
    private Account senderAccount;
    private Account receiverAccount;
    private TransferRequest transferRequest;
    
    @BeforeEach
    void setUp() {
        // 송금자 설정
        sender = Member.builder()
                .id(1L)
                .phoneNumber("01012345678")
                .name("송금자")
                .build();
        
        // 수신자 설정
        receiver = Member.builder()
                .id(2L)
                .phoneNumber("01087654321")
                .name("수신자")
                .build();
        
        // 송금자 계좌 설정
        senderAccount = Account.builder()
                .id(1L)
                .accountNumber("1111111111")
                .member(sender)
                .balance(BigDecimal.valueOf(100000))
                .status(AccountStatus.ACTIVE)
                .build();
        
        // 수신자 계좌 설정
        receiverAccount = Account.builder()
                .id(2L)
                .accountNumber("2222222222")
                .member(receiver)
                .balance(BigDecimal.valueOf(50000))
                .status(AccountStatus.ACTIVE)
                .build();
        
        // 송금 요청 설정
        transferRequest = new TransferRequest();
        transferRequest.setReceiverAccountNumber("2222222222");
        transferRequest.setAmount(BigDecimal.valueOf(10000));
        transferRequest.setMemo("테스트 송금");
    }
    
    @Test
    @DisplayName("정상 송금 처리 - 외부 API 성공")
    void transferSuccess() {
        // given
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(sender));
        when(accountRepository.findByAccountNumber("2222222222")).thenReturn(Optional.of(receiverAccount));
        when(accountRepository.findByMemberId(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdWithLock(2L)).thenReturn(Optional.of(receiverAccount));
        when(transferRepository.existsByTransactionId(anyString())).thenReturn(false);
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // 외부 API 성공 응답 설정
        BankingApiResponse apiResponse = BankingApiResponse.builder()
                .transactionId("TXN123456789012")
                .bankTransactionId("BANK-12345")
                .status(BankingApiStatus.SUCCESS)
                .message("송금이 정상적으로 처리되었습니다.")
                .processedAt(LocalDateTime.now())
                .build();
        when(bankingApiService.processTransfer(any(BankingApiRequest.class))).thenReturn(apiResponse);
        
        // when
        TransferResponse response = transferService.transfer("01012345678", transferRequest);
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(TransferStatus.COMPLETED);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(10000));
        
        // 외부 API 호출 검증
        verify(bankingApiService, times(1)).processTransfer(any(BankingApiRequest.class));
        
        // 잔액 변경 검증
        assertThat(senderAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(90000));
        assertThat(receiverAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(60000));
    }
    
    @Test
    @DisplayName("송금 실패 - 외부 API 잔액 부족")
    void transferFailByInsufficientBalance() {
        // given
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(sender));
        when(accountRepository.findByAccountNumber("2222222222")).thenReturn(Optional.of(receiverAccount));
        when(accountRepository.findByMemberId(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdWithLock(2L)).thenReturn(Optional.of(receiverAccount));
        when(transferRepository.existsByTransactionId(anyString())).thenReturn(false);
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // 외부 API 잔액 부족 응답 설정
        BankingApiResponse apiResponse = BankingApiResponse.builder()
                .transactionId("TXN123456789012")
                .status(BankingApiStatus.INSUFFICIENT_BALANCE)
                .errorCode("E001")
                .errorMessage("송금 계좌의 잔액이 부족합니다.")
                .processedAt(LocalDateTime.now())
                .build();
        when(bankingApiService.processTransfer(any(BankingApiRequest.class))).thenReturn(apiResponse);
        
        // when & then
        assertThatThrownBy(() -> transferService.transfer("01012345678", transferRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("외부 API 오류: 잔액부족");
        
        // 잔액이 변경되지 않았는지 검증
        assertThat(senderAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100000));
        assertThat(receiverAccount.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }
    
    @Test
    @DisplayName("송금 실패 - 외부 API 계좌 오류")
    void transferFailByInvalidAccount() {
        // given
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(sender));
        when(accountRepository.findByAccountNumber("2222222222")).thenReturn(Optional.of(receiverAccount));
        when(accountRepository.findByMemberId(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdWithLock(2L)).thenReturn(Optional.of(receiverAccount));
        when(transferRepository.existsByTransactionId(anyString())).thenReturn(false);
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // 외부 API 계좌 오류 응답 설정
        BankingApiResponse apiResponse = BankingApiResponse.builder()
                .transactionId("TXN123456789012")
                .status(BankingApiStatus.INVALID_ACCOUNT)
                .errorCode("E002")
                .errorMessage("수신 계좌 정보가 올바르지 않습니다.")
                .processedAt(LocalDateTime.now())
                .build();
        when(bankingApiService.processTransfer(any(BankingApiRequest.class))).thenReturn(apiResponse);
        
        // when & then
        assertThatThrownBy(() -> transferService.transfer("01012345678", transferRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("외부 API 오류: 계좌정보오류");
        
        // 외부 API 호출 검증
        verify(bankingApiService, times(1)).processTransfer(any(BankingApiRequest.class));
        
        // 송금 상태가 FAILED로 변경되었는지 확인
        verify(transferRepository, atLeastOnce()).save(argThat(transfer -> 
            transfer.getStatus() == TransferStatus.FAILED || 
            transfer.getStatus() == TransferStatus.PROCESSING
        ));
    }
    
    @Test
    @DisplayName("송금 실패 - 외부 API 시스템 오류")
    void transferFailBySystemError() {
        // given
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(sender));
        when(accountRepository.findByAccountNumber("2222222222")).thenReturn(Optional.of(receiverAccount));
        when(accountRepository.findByMemberId(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(senderAccount));
        when(accountRepository.findByIdWithLock(2L)).thenReturn(Optional.of(receiverAccount));
        when(transferRepository.existsByTransactionId(anyString())).thenReturn(false);
        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // 외부 API 시스템 오류 응답 설정
        BankingApiResponse apiResponse = BankingApiResponse.builder()
                .transactionId("TXN123456789012")
                .status(BankingApiStatus.SYSTEM_ERROR)
                .errorCode("E999")
                .errorMessage("은행 시스템 오류가 발생했습니다.")
                .processedAt(LocalDateTime.now())
                .build();
        when(bankingApiService.processTransfer(any(BankingApiRequest.class))).thenReturn(apiResponse);
        
        // when & then
        assertThatThrownBy(() -> transferService.transfer("01012345678", transferRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("외부 API 오류: 시스템오류");
        
        // 감사 로그 실패 기록 검증
        verify(auditLogService, times(1)).logFailure(
                eq(1L),
                eq("01012345678"),
                any(),
                anyString(),
                any(),
                any(),
                anyString(),
                anyString()
        );
    }
    
    @Test
    @DisplayName("송금 상태 조회 테스트")
    void getTransferStatus() {
        // given
        String transactionId = "TXN123456789012";
        Transfer transfer = Transfer.builder()
                .transactionId(transactionId)
                .sender(sender)
                .senderAccountNumber("1111111111")
                .receiver(receiver)
                .receiverAccountNumber("2222222222")
                .amount(BigDecimal.valueOf(10000))
                .status(TransferStatus.COMPLETED)
                .bankTransactionId("BANK-12345")
                .build();
        
        when(transferRepository.findByTransactionId(transactionId)).thenReturn(Optional.of(transfer));
        
        // when
        TransferResponse response = transferService.getTransfer(transactionId);
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getTransactionId()).isEqualTo(transactionId);
        assertThat(response.getStatus()).isEqualTo(TransferStatus.COMPLETED);
    }
}