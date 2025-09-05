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
import fintech2.easypay.transfer.action.command.ExternalTransferCommand;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.external.BankingApiRequest;
import fintech2.easypay.transfer.external.BankingApiResponse;
import fintech2.easypay.transfer.external.BankingApiService;
import fintech2.easypay.transfer.external.BankingApiStatus;
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
 * 외부 은행 송금 액션
 * 외부 은행 시스템으로의 송금을 처리
 * 외부 API 호출 및 다양한 상태(성공/실패/타임아웃/알 수 없음) 처리
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
    private final ApplicationContext applicationContext;
    
    @Override
    public Class<ExternalTransferCommand> commandType() {
        return ExternalTransferCommand.class;
    }
    
    @Override
    public boolean validate(ExternalTransferCommand command) {
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
            
            if (command.getReceiverBankCode() == null || command.getReceiverBankCode().trim().isEmpty()) {
                log.warn("Receiver bank code is required");
                return false;
            }
            
            // 송금자 존재 확인
            User sender = userRepository.findByPhoneNumber(command.getSenderPhoneNumber())
                    .orElse(null);
            if (sender == null) {
                log.warn("Sender not found: {}", command.getSenderPhoneNumber());
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
    public void savePending(ExternalTransferCommand command) {
        try {
            // 거래 ID가 없으면 생성
            if (command.getTransactionId() == null || command.getTransactionId().trim().isEmpty()) {
                command.setTransactionId(generateTransactionId());
            }
            
            // 송금자 정보 조회
            User sender = userRepository.findByPhoneNumber(command.getSenderPhoneNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            
            Account senderAccount = resolveSenderAccount(sender, command.getSenderAccountNumber());
            
            // 외부 송금의 경우 수신자 정보가 외부 시스템에 있으므로 Transfer 엔티티에서는 null 사용
            Transfer transfer = Transfer.builder()
                    .transactionId(command.getTransactionId())
                    .sender(sender)
                    .senderAccountNumber(senderAccount.getAccountNumber())
                    .receiver(null) // 외부 수신자
                    .receiverAccountNumber(command.getReceiverAccountNumber())
                    .amount(command.getAmount())
                    .memo(command.getMemo())
                    .build();
            
            // 처리중 상태로 설정
            transfer.markAsProcessing();
            
            transferRepository.save(transfer);
            
            log.info("External transfer saved as pending: {}", command.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to save pending external transfer: {}", command, e);
            throw new BusinessException(ErrorCode.TRANSACTION_FAILED, 
                    "외부 송금 요청 저장 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    @Override
    public ActionResult execute(ExternalTransferCommand command) {
        try {
            log.info("Executing external transfer: {}", command.getTransactionId());
            
            // 사용자 및 계좌 정보 조회
            User sender = userRepository.findByPhoneNumber(command.getSenderPhoneNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            
            Account senderAccount = resolveSenderAccount(sender, command.getSenderAccountNumber());
            
            // 잔액 최종 확인
            if (!balanceService.hasSufficientBalance(senderAccount.getAccountNumber(), command.getAmount())) {
                return ActionResult.failure("INSUFFICIENT_BALANCE", 
                        "잔액이 부족합니다", createResultData(command));
            }
            
            // 외부 뱅킹 API 요청 생성
            BankingApiRequest apiRequest = BankingApiRequest.builder()
                    .transactionId(command.getTransactionId())
                    .senderAccountNumber(senderAccount.getAccountNumber())
                    .senderBankCode("EASYPAY")
                    .receiverAccountNumber(command.getReceiverAccountNumber())
                    .receiverBankCode(command.getReceiverBankCode())
                    .amount(command.getAmount())
                    .currency(command.getCurrency())
                    .memo(command.getMemo())
                    .build();
            
            log.info("Calling external banking API: {}", command.getTransactionId());
            BankingApiResponse apiResponse = bankingApiService.processTransfer(apiRequest);
            
            // API 응답 상태별 처리
            return processApiResponse(command, apiResponse, senderAccount, sender);
            
        } catch (BusinessException e) {
            log.error("Business error in external transfer execution: {}", command.getTransactionId(), e);
            return ActionResult.failure(e.getErrorCode().name(), e.getMessage(), createResultData(command));
        } catch (Exception e) {
            log.error("Unexpected error in external transfer execution: {}", command.getTransactionId(), e);
            return ActionResult.failure("EXTERNAL_API_ERROR", 
                    "외부 은행 API 호출 중 오류가 발생했습니다", createResultData(command));
        }
    }
    
    @Override
    public void updateFromResult(ExternalTransferCommand command, ActionResult result) {
        try {
            // Transfer 엔티티 조회 및 상태 업데이트
            Transfer transfer = transferRepository.findByTransactionId(command.getTransactionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
            
            User sender = userRepository.findByPhoneNumber(command.getSenderPhoneNumber())
                    .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
            
            if (result.isSuccess()) {
                // 성공 시 상태 업데이트
                transfer.markAsCompleted();
                if (command.getBankTransactionId() != null) {
                    transfer.setBankTransactionId(command.getBankTransactionId());
                }
                
                // 감사 로그 기록
                auditLogService.logSuccess(
                        sender.getId(),
                        command.getSenderPhoneNumber(),
                        AuditEventType.TRANSFER_SUCCESS,
                        String.format("외부 송금 완료: %s -> %s:%s (%s원)",
                                transfer.getSenderAccountNumber(),
                                command.getReceiverBankCode(),
                                command.getReceiverAccountNumber(),
                                command.getAmount()),
                        null, null,
                        String.format("amount: %s, memo: %s, bankCode: %s", 
                                command.getAmount(), command.getMemo(), command.getReceiverBankCode()),
                        String.format("transactionId: %s, bankTransactionId: %s", 
                                command.getTransactionId(), command.getBankTransactionId())
                );
                
                // 송금자에게 알림 전송
                notificationService.sendTransferActivityNotification(
                        sender.getId(),
                        command.getSenderPhoneNumber(),
                        String.format("%s원이 %s 은행 %s로 송금되었습니다.", 
                                command.getAmount(), command.getReceiverBankCode(), 
                                command.getReceiverAccountNumber())
                );
                
            } else if (result.isTimeout()) {
                // 타임아웃 상태 처리
                transfer.markAsTimeout(result.getMessage());
                
            } else if (result.isUnknown()) {
                // 알 수 없음 상태 처리
                transfer.markAsUnknown(result.getMessage());
                
            } else if (result.isPending()) {
                // 처리중 상태 유지 (별도 처리 불필요)
                log.info("External transfer still pending: {}", command.getTransactionId());
                
            } else {
                // 실패 시 상태 업데이트
                transfer.markAsFailed(result.getMessage());
                
                // 실패 감사 로그 기록
                auditLogService.logFailure(
                        sender.getId(),
                        command.getSenderPhoneNumber(),
                        AuditEventType.TRANSFER_FAILED,
                        "외부 송금 실패: " + result.getMessage(),
                        null, null,
                        String.format("amount: %s, memo: %s, bankCode: %s", 
                                command.getAmount(), command.getMemo(), command.getReceiverBankCode()),
                        result.getMessage()
                );
            }
            
            transferRepository.save(transfer);
            
            log.info("External transfer result updated: transactionId={}, status={}", 
                    command.getTransactionId(), result.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to update external transfer result: {}", command.getTransactionId(), e);
            // 결과 업데이트 실패는 예외를 던지지 않음
        }
    }
    
    /**
     * API 응답 상태별 처리
     */
    private ActionResult processApiResponse(ExternalTransferCommand command, BankingApiResponse apiResponse, 
                                          Account senderAccount, User sender) {
        
        Map<String, Object> resultData = createResultData(command);
        if (apiResponse.getBankTransactionId() != null) {
            command.setBankTransactionId(apiResponse.getBankTransactionId());
            resultData.put("bankTransactionId", apiResponse.getBankTransactionId());
        }
        
        switch (apiResponse.getStatus()) {
            case SUCCESS -> {
                // 성공 시 잔액 차감
                balanceService.decrease(senderAccount.getAccountNumber(), command.getAmount(),
                        TransactionType.TRANSFER_OUT, "외부 송금 출금: " + command.getMemo(),
                        command.getTransactionId(), sender.getId().toString());
                
                log.info("External transfer successful: {}", command.getTransactionId());
                return ActionResult.success("외부 송금이 완료되었습니다", resultData);
            }
            
            case TIMEOUT -> {
                // 타임아웃 - 잔액 차감하지 않고 상태만 기록
                String timeoutMessage = String.format("외부 API 타임아웃: %s", apiResponse.getErrorMessage());
                log.warn("External transfer timeout: {}", command.getTransactionId());
                return ActionResult.timeout(timeoutMessage, resultData);
            }
            
            case UNKNOWN -> {
                // 알 수 없음 상태 - 별도 확인 필요
                String unknownMessage = "외부 API 응답 상태를 확인할 수 없습니다";
                log.warn("External transfer status unknown: {}", command.getTransactionId());
                return ActionResult.unknown(unknownMessage, resultData);
            }
            
            case PENDING -> {
                // 처리중 상태 - 스케줄러가 나중에 상태 확인
                log.info("External transfer still pending: {}", command.getTransactionId());
                return ActionResult.pending("외부 송금이 처리 중입니다", resultData);
            }
            
            default -> {
                // 기타 실패
                String failureMessage = String.format("외부 API 오류: %s - %s", 
                        apiResponse.getStatus().getDescription(), apiResponse.getErrorMessage());
                log.error("External transfer failed: {} - {}", command.getTransactionId(), failureMessage);
                return ActionResult.failure("EXTERNAL_API_FAILED", failureMessage, resultData);
            }
        }
    }
    
    /**
     * 송금자 계좌 결정
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
    private Map<String, Object> createResultData(ExternalTransferCommand command) {
        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", command.getTransactionId());
        data.put("amount", command.getAmount());
        data.put("senderAccountNumber", command.getSenderAccountNumber());
        data.put("receiverAccountNumber", command.getReceiverAccountNumber());
        data.put("receiverBankCode", command.getReceiverBankCode());
        data.put("memo", command.getMemo());
        data.put("currency", command.getCurrency());
        data.put("transferType", "EXTERNAL");
        return data;
    }
}