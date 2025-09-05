package fintech2.easypay.transfer.action.impl;

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
import fintech2.easypay.transfer.action.ActionResult;
import fintech2.easypay.transfer.action.TransferAction;
import fintech2.easypay.transfer.action.command.InternalTransferCommand;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 내부 계좌 간 송금 액션
 * EasyPay 시스템 내부 계좌 간의 송금을 처리
 * 데드락 방지 로직과 동시성 제어를 포함한 즉시 잔액 이동
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InternalTransferAction implements TransferAction<InternalTransferCommand> {
    
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final BalanceService balanceService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    private final ApplicationContext applicationContext;
    
    @Override
    public Class<InternalTransferCommand> commandType() {
        return InternalTransferCommand.class;
    }
    
    @Override
    public boolean validate(InternalTransferCommand command) {
        try {
            // 기본 유효성 검사
            if (command.getAmount() == null || command.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Invalid amount: {}", command.getAmount());
                return false;
            }
            
            if (command.getSenderPhoneNumber() == null || command.getSenderPhoneNumber().trim().isEmpty()) {
                log.warn("Sender phone number is required");
                return false;
            }
            
            if (command.getReceiverAccountNumber() == null || command.getReceiverAccountNumber().trim().isEmpty()) {
                log.warn("Receiver account number is required");
                return false;
            }
            
            // 송금자 존재 확인
            User sender = userRepository.findByPhoneNumber(command.getSenderPhoneNumber())
                    .orElse(null);
            if (sender == null) {
                log.warn("Sender not found: {}", command.getSenderPhoneNumber());
                return false;
            }
            
            // 수신자 계좌 존재 확인
            Account receiverAccount = accountRepository
                    .findByAccountNumber(command.getReceiverAccountNumber())
                    .orElse(null);
            if (receiverAccount == null) {
                log.warn("Receiver account not found: {}", command.getReceiverAccountNumber());
                return false;
            }
            
            // 자기 자신에게 송금 방지
            User receiver = userRepository.findById(receiverAccount.getUserId())
                    .orElse(null);
            if (receiver != null && sender.getId().equals(receiver.getId())) {
                log.warn("Same account transfer attempted: {}", sender.getId());
                return false;
            }
            
            // 송금자 계좌 확인 및 잔액 검증
            Account senderAccount = resolveSenderAccount(sender, command.getSenderAccountNumber());
            if (senderAccount == null) {
                log.warn("Sender account not found or invalid");
                return false;
            }
            
            // 잔액 충분성 검증
            if (!balanceService.hasSufficientBalance(senderAccount.getAccountNumber(), command.getAmount())) {
                log.warn("Insufficient balance: account={}, amount={}", 
                        senderAccount.getAccountNumber(), command.getAmount());
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Validation error for command: {}", command, e);
            return false;
        }
    }
    
    @Override
    public void savePending(InternalTransferCommand command) {
        try {
            // 거래 ID가 없으면 생성
            if (command.getTransactionId() == null || command.getTransactionId().trim().isEmpty()) {
                command.setTransactionId(generateTransactionId());
            }
            
            // 송금자 및 수신자 정보 조회
            User sender = userRepository.findByPhoneNumber(command.getSenderPhoneNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            
            Account receiverAccount = accountRepository
                    .findByAccountNumber(command.getReceiverAccountNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
            
            User receiver = userRepository.findById(receiverAccount.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            
            Account senderAccount = resolveSenderAccount(sender, command.getSenderAccountNumber());
            
            // Transfer 엔티티 생성 및 저장
            Transfer transfer = Transfer.builder()
                    .transactionId(command.getTransactionId())
                    .sender(sender)
                    .senderAccountNumber(senderAccount.getAccountNumber())
                    .receiver(receiver)
                    .receiverAccountNumber(receiverAccount.getAccountNumber())
                    .amount(command.getAmount())
                    .memo(command.getMemo())
                    .build();
            
            transferRepository.save(transfer);
            
            log.info("Internal transfer saved as pending: {}", command.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to save pending transfer: {}", command, e);
            throw new BusinessException(ErrorCode.TRANSACTION_FAILED, 
                    "송금 요청 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public ActionResult execute(InternalTransferCommand command) {
        try {
            log.info("Executing internal transfer: {}", command.getTransactionId());
            
            // 사용자 및 계좌 정보 조회
            User sender = userRepository.findByPhoneNumber(command.getSenderPhoneNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            
            Account receiverAccount = accountRepository
                    .findByAccountNumber(command.getReceiverAccountNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
            
            User receiver = userRepository.findById(receiverAccount.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            
            Account senderAccount = resolveSenderAccount(sender, command.getSenderAccountNumber());
            
            // 데드락 방지를 위한 순서대로 락 획득
            Account senderAccountLocked;
            Account receiverAccountLocked;
            
            if (senderAccount.getId().compareTo(receiverAccount.getId()) < 0) {
                senderAccountLocked = accountRepository.findByIdWithLock(senderAccount.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
                receiverAccountLocked = accountRepository.findByIdWithLock(receiverAccount.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
            } else {
                receiverAccountLocked = accountRepository.findByIdWithLock(receiverAccount.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
                senderAccountLocked = accountRepository.findByIdWithLock(senderAccount.getId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
            }
            
            // 잔액 최종 확인
            if (!balanceService.hasSufficientBalance(senderAccountLocked.getAccountNumber(), command.getAmount())) {
                return ActionResult.failure("INSUFFICIENT_BALANCE", 
                        "잔액이 부족합니다", createResultData(command));
            }
            
            // 잔액 이동 (출금 -> 입금)
            balanceService.decrease(senderAccountLocked.getAccountNumber(), command.getAmount(),
                    TransactionType.TRANSFER_OUT, "내부 송금 출금: " + command.getMemo(),
                    command.getTransactionId(), sender.getId().toString());
            
            balanceService.increase(receiverAccountLocked.getAccountNumber(), command.getAmount(),
                    TransactionType.TRANSFER_IN, "내부 송금 입금: " + command.getMemo(),
                    command.getTransactionId(), receiver.getId().toString());
            
            log.info("Internal transfer executed successfully: {}", command.getTransactionId());
            
            return ActionResult.success("내부 송금이 완료되었습니다", createResultData(command));
            
        } catch (BusinessException e) {
            log.error("Business error in internal transfer execution: {}", command.getTransactionId(), e);
            return ActionResult.failure(e.getErrorCode().name(), e.getMessage(), createResultData(command));
        } catch (Exception e) {
            log.error("Unexpected error in internal transfer execution: {}", command.getTransactionId(), e);
            return ActionResult.failure("INTERNAL_ERROR", 
                    "내부 송금 처리 중 오류가 발생했습니다", createResultData(command));
        }
    }
    
    @Override
    public void updateFromResult(InternalTransferCommand command, ActionResult result) {
        try {
            // Transfer 엔티티 조회 및 상태 업데이트
            Transfer transfer = transferRepository.findByTransactionId(command.getTransactionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
            
            User sender = userRepository.findByPhoneNumber(command.getSenderPhoneNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            
            Account receiverAccount = accountRepository
                    .findByAccountNumber(command.getReceiverAccountNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
            
            User receiver = userRepository.findById(receiverAccount.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            
            if (result.isSuccess()) {
                // 성공 시 상태 업데이트
                transfer.markAsCompleted();
                
                // 감사 로그 기록
                auditLogService.logSuccess(
                        sender.getId(),
                        command.getSenderPhoneNumber(),
                        AuditEventType.TRANSFER_SUCCESS,
                        String.format("내부 송금 완료: %s -> %s (%s원)",
                                transfer.getSenderAccountNumber(),
                                transfer.getReceiverAccountNumber(),
                                command.getAmount()),
                        null, null,
                        String.format("amount: %s, memo: %s", command.getAmount(), command.getMemo()),
                        String.format("transactionId: %s", command.getTransactionId())
                );
                
                // 알림 전송
                notificationService.sendTransferActivityNotification(
                        sender.getId(),
                        command.getSenderPhoneNumber(),
                        String.format("%s원이 %s로 송금되었습니다.", 
                                command.getAmount(), command.getReceiverAccountNumber())
                );
                
                notificationService.sendTransferActivityNotification(
                        receiver.getId(),
                        receiver.getPhoneNumber(),
                        String.format("%s원이 %s로부터 입금되었습니다.", 
                                command.getAmount(), transfer.getSenderAccountNumber())
                );
                
            } else {
                // 실패 시 상태 업데이트
                transfer.markAsFailed(result.getMessage());
                
                // 실패 감사 로그 기록
                auditLogService.logFailure(
                        sender.getId(),
                        command.getSenderPhoneNumber(),
                        AuditEventType.TRANSFER_FAILED,
                        "내부 송금 실패: " + result.getMessage(),
                        null, null,
                        String.format("amount: %s, memo: %s", command.getAmount(), command.getMemo()),
                        result.getMessage()
                );
            }
            
            transferRepository.save(transfer);
            
            log.info("Transfer result updated: transactionId={}, status={}", 
                    command.getTransactionId(), result.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to update transfer result: {}", command.getTransactionId(), e);
            // 결과 업데이트 실패는 예외를 던지지 않음 (이미 송금은 처리됨)
        }
    }
    
    /**
     * 송금자 계좌 결정
     * 지정된 계좌가 있으면 해당 계좌, 없으면 기본 계좌 사용
     */
    private Account resolveSenderAccount(User sender, String senderAccountNumber) {
        try {
            if (senderAccountNumber != null && !senderAccountNumber.trim().isEmpty()) {
                // 특정 계좌 지정된 경우
                Account account = accountRepository.findByAccountNumber(senderAccountNumber)
                        .orElse(null);
                
                // 송금자 본인의 계좌인지 확인
                if (account != null && account.getUserId().equals(sender.getId())) {
                    return account;
                }
                return null;
            } else {
                // 기본 계좌 사용
                fintech2.easypay.account.service.UserAccountService userAccountService =
                        applicationContext.getBean(fintech2.easypay.account.service.UserAccountService.class);
                
                fintech2.easypay.account.entity.UserAccount primaryUserAccount = 
                        userAccountService.getPrimaryAccount(sender.getId()).orElse(null);
                
                if (primaryUserAccount != null) {
                    return accountRepository.findByAccountNumber(primaryUserAccount.getAccountNumber())
                            .orElse(null);
                }
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to resolve sender account: userId={}, accountNumber={}", 
                    sender.getId(), senderAccountNumber, e);
            return null;
        }
    }
    
    /**
     * 고유한 거래 ID 생성
     */
    private String generateTransactionId() {
        String transactionId;
        do {
            transactionId = "TXN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        } while (transferRepository.existsByTransactionId(transactionId));
        return transactionId;
    }
    
    /**
     * 결과 데이터 생성
     */
    private Map<String, Object> createResultData(InternalTransferCommand command) {
        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", command.getTransactionId());
        data.put("amount", command.getAmount());
        data.put("senderAccountNumber", command.getSenderAccountNumber());
        data.put("receiverAccountNumber", command.getReceiverAccountNumber());
        data.put("memo", command.getMemo());
        data.put("transferType", "INTERNAL");
        return data;
    }
}