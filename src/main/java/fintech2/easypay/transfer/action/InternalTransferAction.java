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
import fintech2.easypay.transfer.command.InternalTransferCommand;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.repository.TransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 내부 계좌 간 송금 처리 액션
 * EasyPay 플랫폼 내부에서의 송금을 담당
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
    
    @Override
    public Class<InternalTransferCommand> getCommandType() {
        return InternalTransferCommand.class;
    }
    
    @Override
    public boolean validate(InternalTransferCommand command) {
        log.debug("내부 송금 검증 시작: {}", command.getTransactionId());
        
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
        
        // 자기 자신에게 송금 방지
        if (command.getSenderId().equals(command.getReceiverId())) {
            log.warn("자기 자신에게 송금 시도: senderId={}", command.getSenderId());
            return false;
        }
        
        // 계좌 존재 여부 확인
        Account senderAccount = accountRepository.findByAccountNumber(command.getSenderAccountNumber())
                .orElse(null);
        Account receiverAccount = accountRepository.findByAccountNumber(command.getReceiverAccountNumber())
                .orElse(null);
        
        if (senderAccount == null) {
            log.warn("송금자 계좌를 찾을 수 없음: {}", command.getSenderAccountNumber());
            return false;
        }
        
        if (receiverAccount == null) {
            log.warn("수신자 계좌를 찾을 수 없음: {}", command.getReceiverAccountNumber());
            return false;
        }
        
        // 송금자가 해당 계좌의 소유자인지 확인
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
        
        log.debug("내부 송금 검증 완료: {}", command.getTransactionId());
        return true;
    }
    
    @Override
    public void savePending(InternalTransferCommand command) {
        log.debug("내부 송금 보류 저장 시작: {}", command.getTransactionId());
        
        User sender = userRepository.findById(command.getSenderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        User receiver = userRepository.findById(command.getReceiverId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        Transfer transfer = Transfer.builder()
                .transactionId(command.getTransactionId())
                .sender(sender)
                .senderAccountNumber(command.getSenderAccountNumber())
                .receiver(receiver)
                .receiverAccountNumber(command.getReceiverAccountNumber())
                .amount(command.getAmount())
                .memo(command.getMemo())
                .build();
        
        transferRepository.save(transfer);
        log.debug("내부 송금 보류 저장 완료: {}", command.getTransactionId());
    }
    
    @Override
    public ActionResult execute(InternalTransferCommand command) {
        log.info("내부 송금 실행 시작: {}", command.getTransactionId());
        
        try {
            // 송금 처리 중 상태로 변경
            Transfer transfer = transferRepository.findByTransactionId(command.getTransactionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
            transfer.markAsProcessing();
            transferRepository.save(transfer);
            
            // 계좌 잠금 순서 결정 (데드락 방지)
            Account senderAccount = accountRepository.findByAccountNumber(command.getSenderAccountNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
            Account receiverAccount = accountRepository.findByAccountNumber(command.getReceiverAccountNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER));
            
            // ID 순서로 락 획득
            if (senderAccount.getId().compareTo(receiverAccount.getId()) < 0) {
                accountRepository.findByIdWithLock(senderAccount.getId());
                accountRepository.findByIdWithLock(receiverAccount.getId());
            } else {
                accountRepository.findByIdWithLock(receiverAccount.getId());
                accountRepository.findByIdWithLock(senderAccount.getId());
            }
            
            // 잔액 이동 (내부 송금이므로 외부 API 호출 없이 직접 처리)
            balanceService.decrease(command.getSenderAccountNumber(), command.getAmount(),
                    TransactionType.TRANSFER_OUT, "내부 송금 출금: " + command.getMemo(),
                    command.getTransactionId(), command.getSenderId().toString());
            
            balanceService.increase(command.getReceiverAccountNumber(), command.getAmount(),
                    TransactionType.TRANSFER_IN, "내부 송금 입금: " + command.getMemo(),
                    command.getTransactionId(), command.getReceiverId().toString());
            
            Map<String, Object> data = new HashMap<>();
            data.put("amount", command.getAmount());
            data.put("senderAccount", command.getSenderAccountNumber());
            data.put("receiverAccount", command.getReceiverAccountNumber());
            
            log.info("내부 송금 실행 완료: {}", command.getTransactionId());
            return ActionResult.success("내부 송금이 완료되었습니다.", command.getTransactionId(), data);
            
        } catch (Exception e) {
            log.error("내부 송금 실행 실패: {} - {}", command.getTransactionId(), e.getMessage(), e);
            
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", e.getMessage());
            
            return ActionResult.failure("INTERNAL_TRANSFER_ERROR", "내부 송금 처리 중 오류가 발생했습니다: " + e.getMessage(),
                    command.getTransactionId(), errorData);
        }
    }
    
    @Override
    public void updateFromResult(InternalTransferCommand command, ActionResult result) {
        log.debug("내부 송금 결과 업데이트 시작: {} - {}", command.getTransactionId(), result.getStatus());
        
        Transfer transfer = transferRepository.findByTransactionId(command.getTransactionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
        
        User sender = userRepository.findById(command.getSenderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        User receiver = userRepository.findById(command.getReceiverId())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        
        if (result.isSuccess()) {
            // 성공 처리
            transfer.markAsCompleted();
            
            // 감사 로그
            auditLogService.logSuccess(
                    command.getSenderId(),
                    sender.getPhoneNumber(),
                    AuditEventType.TRANSFER_SUCCESS,
                    String.format("내부 송금 완료: %s -> %s (%s원)",
                            command.getSenderAccountNumber(),
                            command.getReceiverAccountNumber(),
                            command.getAmount()),
                    null, null,
                    String.format("amount: %s, memo: %s", command.getAmount(), command.getMemo()),
                    command.getTransactionId()
            );
            
            // 알림 전송
            notificationService.sendTransferActivityNotification(
                    command.getSenderId(),
                    sender.getPhoneNumber(),
                    String.format("%s원이 %s로 송금되었습니다.", command.getAmount(), command.getReceiverAccountNumber())
            );
            
            notificationService.sendTransferActivityNotification(
                    command.getReceiverId(),
                    receiver.getPhoneNumber(),
                    String.format("%s원이 %s로부터 입금되었습니다.", command.getAmount(), command.getSenderAccountNumber())
            );
            
        } else {
            // 실패 처리
            transfer.markAsFailed(result.getMessage());
            
            // 감사 로그
            auditLogService.logFailure(
                    command.getSenderId(),
                    sender.getPhoneNumber(),
                    AuditEventType.TRANSFER_FAILED,
                    "내부 송금 실패: " + result.getMessage(),
                    null, null,
                    String.format("amount: %s, memo: %s", command.getAmount(), command.getMemo()),
                    result.getMessage()
            );
        }
        
        transferRepository.save(transfer);
        log.debug("내부 송금 결과 업데이트 완료: {}", command.getTransactionId());
    }
}