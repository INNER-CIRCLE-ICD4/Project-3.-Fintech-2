package fintech2.easypay.transfer.action;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.account.service.BalanceService;
import fintech2.easypay.audit.service.AuditLogService;
import fintech2.easypay.audit.service.NotificationService;
import fintech2.easypay.auth.entity.User;
import fintech2.easypay.auth.repository.UserRepository;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.common.enums.AuditEventType;
import fintech2.easypay.common.enums.TransactionType;
import fintech2.easypay.transfer.command.ExternalTransferCommand;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.external.BankingApiRequest;
import fintech2.easypay.transfer.external.BankingApiResponse;
import fintech2.easypay.transfer.external.BankingApiService;
import fintech2.easypay.transfer.external.BankingApiStatus;
import fintech2.easypay.transfer.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 외부 은행 송금 처리 액션
 * EasyPay에서 다른 은행으로의 송금을 담당
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalTransferAction implements TransferAction<ExternalTransferCommand> {
    
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final BankingApiService bankingApiService;
    
    @Override
    public Class<ExternalTransferCommand> getCommandType() {
        return ExternalTransferCommand.class;
    }
    
    @Override
    public boolean validate(ExternalTransferCommand command) {
        log.debug("외부 송금 검증 시작: {}", command.getTransactionId());
        
        // 기본 유효성 검증
        if (command.getAmount() == null || command.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            log.warn("송금 금액이 유효하지 않음: {}", command.getAmount());
            return false;
        }
        
        if (command.getSenderAccountNumber() == null || command.getSenderAccountNumber().isBlank()) {
            log.warn("송금자 계좌번호가 유효하지 않음");
            return false;
        }
        
        if (command.getReceiverAccountNumber() == null || command.getReceiverAccountNumber().isBlank()) {
            log.warn("수신자 계좌번호가 유효하지 않음");
            return false;
        }
        
        if (command.getReceiverBankCode() == null || command.getReceiverBankCode().isBlank()) {
            log.warn("수신자 은행코드가 유효하지 않음");
            return false;
        }
        
        // 계좌 존재 여부 및 소유권 확인
        Account senderAccount = accountRepository.findByAccountNumber(command.getSenderAccountNumber())
                .orElse(null);
        
        if (senderAccount == null) {
            log.warn("송금자 계좌를 찾을 수 없음: {}", command.getSenderAccountNumber());
            return false;
        }
        
        if (!senderAccount.getUserId().equals(command.getSenderId())) {
            log.warn("송금자가 계좌 소유자가 아님: userId={}, accountUserId={}", 
                    command.getSenderId(), senderAccount.getUserId());
            return false;
        }
        
        // 잔액 충분성 검증
        if (!balanceService.hasSufficientBalance(command.getSenderAccountNumber(), command.getAmount())) {
            log.warn("잔액 부족: account={}, amount={}", command.getSenderAccountNumber(), command.getAmount());
            return false;
        }
        
        // TODO: 외부 송금 한도 체크, 제재 대상 확인 등 추가 검증
        
        log.debug("외부 송금 검증 완료: {}", command.getTransactionId());
        return true;
    }
    
    @Override
    public void savePending(ExternalTransferCommand command) {
        log.debug("외부 송금 보류 저장 시작: {}", command.getTransactionId());
        
        User sender = userRepository.findById(command.getSenderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        // 외부 송금의 경우 수신자는 임시로 생성하지 않고 null로 설정
        Transfer transfer = Transfer.builder()
                .transactionId(command.getTransactionId())
                .sender(sender)
                .senderAccountNumber(command.getSenderAccountNumber())
                .receiver(null) // 외부 송금은 수신자 엔티티가 없음
                .receiverAccountNumber(command.getReceiverAccountNumber())
                .amount(command.getAmount())
                .memo(command.getMemo())
                .build();
        
        transferRepository.save(transfer);
        log.debug("외부 송금 보류 저장 완료: {}", command.getTransactionId());
    }
    
    @Override
    public ActionResult execute(ExternalTransferCommand command) {
        log.info("외부 송금 실행 시작: {}", command.getTransactionId());
        
        try {
            // 송금 처리 중 상태로 변경
            Transfer transfer = transferRepository.findByTransactionId(command.getTransactionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
            transfer.markAsProcessing();
            transferRepository.save(transfer);
            
            // 송금자 계좌 락 획득
            Account senderAccount = accountRepository.findByAccountNumber(command.getSenderAccountNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
            accountRepository.findByIdWithLock(senderAccount.getId());
            
            // 외부 뱅킹 API 호출
            BankingApiRequest apiRequest = BankingApiRequest.builder()
                    .transactionId(command.getTransactionId())
                    .senderAccountNumber(command.getSenderAccountNumber())
                    .senderBankCode("EASYPAY")
                    .receiverAccountNumber(command.getReceiverAccountNumber())
                    .receiverBankCode(command.getReceiverBankCode())
                    .amount(command.getAmount())
                    .currency("KRW")
                    .memo(command.getMemo())
                    .build();
            
            log.info("외부 뱅킹 API 호출: {}", command.getTransactionId());
            BankingApiResponse apiResponse = bankingApiService.processTransfer(apiRequest);
            
            Map<String, Object> data = new HashMap<>();
            data.put("amount", command.getAmount());
            data.put("senderAccount", command.getSenderAccountNumber());
            data.put("receiverAccount", command.getReceiverAccountNumber());
            data.put("receiverBank", command.getReceiverBankName());
            data.put("bankTransactionId", apiResponse.getBankTransactionId());
            
            // API 응답에 따른 처리
            if (apiResponse.getStatus() == BankingApiStatus.SUCCESS) {
                // 성공 시 송금자 계좌에서 출금 처리
                balanceService.decrease(command.getSenderAccountNumber(), command.getAmount(),
                        TransactionType.TRANSFER_OUT, "외부 송금 출금: " + command.getMemo(),
                        command.getTransactionId(), command.getSenderId().toString());
                
                return ActionResult.success("외부 송금이 완료되었습니다.", command.getTransactionId(), data);
                
            } else if (apiResponse.getStatus() == BankingApiStatus.PENDING) {
                return ActionResult.pending("외부 송금이 처리 중입니다.", command.getTransactionId(), data);
                
            } else if (apiResponse.getStatus() == BankingApiStatus.TIMEOUT) {
                return ActionResult.pending("외부 송금 응답 대기 중입니다.", command.getTransactionId(), data);
                
            } else {
                String errorMsg = String.format("외부 API 오류: %s - %s", 
                        apiResponse.getStatus().getDescription(), apiResponse.getErrorMessage());
                return ActionResult.failure("EXTERNAL_API_ERROR", errorMsg, command.getTransactionId(), data);
            }
            
        } catch (Exception e) {
            log.error("외부 송금 실행 실패: {} - {}", command.getTransactionId(), e.getMessage(), e);
            
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", e.getMessage());
            
            return ActionResult.failure("EXTERNAL_TRANSFER_ERROR", "외부 송금 처리 중 오류가 발생했습니다: " + e.getMessage(),
                    command.getTransactionId(), errorData);
        }
    }
    
    @Override
    public void updateFromResult(ExternalTransferCommand command, ActionResult result) {
        log.debug("외부 송금 결과 업데이트 시작: {} - {}", command.getTransactionId(), result.getStatus());
        
        Transfer transfer = transferRepository.findByTransactionId(command.getTransactionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        
        User sender = userRepository.findById(command.getSenderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        if (result.isSuccess()) {
            // 성공 처리
            transfer.markAsCompleted();
            transfer.setBankTransactionId(result.getBankTransactionId());
            
            // 감사 로그
            auditLogService.logSuccess(
                    command.getSenderId(),
                    sender.getPhoneNumber(),
                    AuditEventType.TRANSFER_SUCCESS,
                    String.format("외부 송금 완료: %s -> %s:%s (%s원)",
                            command.getSenderAccountNumber(),
                            command.getReceiverBankName(),
                            command.getReceiverAccountNumber(),
                            command.getAmount()),
                    null, null,
                    String.format("amount: %s, memo: %s, bank: %s", 
                            command.getAmount(), command.getMemo(), command.getReceiverBankName()),
                    command.getTransactionId()
            );
            
            // 알림 전송
            notificationService.sendTransferActivityNotification(
                    command.getSenderId(),
                    sender.getPhoneNumber(),
                    String.format("%s원이 %s은행 %s로 송금되었습니다.", 
                            command.getAmount(), command.getReceiverBankName(), command.getReceiverAccountNumber())
            );
            
        } else if (result.isPending()) {
            // 처리 중 상태 유지
            if ("TIMEOUT".equals(result.getCode())) {
                transfer.markAsTimeout(result.getMessage());
            } else {
                // PENDING 상태 유지 (별도 스케줄러가 상태 업데이트 예정)
            }
            
        } else {
            // 실패 처리
            transfer.markAsFailed(result.getMessage());
            
            // 감사 로그
            auditLogService.logFailure(
                    command.getSenderId(),
                    sender.getPhoneNumber(),
                    AuditEventType.TRANSFER_FAILED,
                    "외부 송금 실패: " + result.getMessage(),
                    null, null,
                    String.format("amount: %s, memo: %s, bank: %s", 
                            command.getAmount(), command.getMemo(), command.getReceiverBankName()),
                    result.getMessage()
            );
        }
        
        transferRepository.save(transfer);
        log.debug("외부 송금 결과 업데이트 완료: {}", command.getTransactionId());
    }
}