package fintech2.easypay.transfer.action.impl;

import fintech2.easypay.auth.service.PinService;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.transfer.action.ActionResult;
import fintech2.easypay.transfer.action.TransferAction;
import fintech2.easypay.transfer.action.impl.InternalTransferAction;
import fintech2.easypay.transfer.action.impl.ExternalTransferAction;
import fintech2.easypay.transfer.action.command.SecureTransferCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 보안 송금 액션 (PIN 검증 포함)
 * PIN 세션 토큰 검증 후 내부/외부 송금으로 위임 처리
 * 실제 송금 처리는 InternalTransferAction 또는 ExternalTransferAction에 위임
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecureTransferAction implements TransferAction<SecureTransferCommand> {
    
    private final PinService pinService;
    private final InternalTransferAction internalTransferAction;
    private final ExternalTransferAction externalTransferAction;
    
    @Override
    public Class<SecureTransferCommand> commandType() {
        return SecureTransferCommand.class;
    }
    
    @Override
    public boolean validate(SecureTransferCommand command) {
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
            
            if (command.getPinSessionToken() == null || command.getPinSessionToken().trim().isEmpty()) {
                log.warn("PIN session token is required");
                return false;
            }
            
            // PIN 세션 토큰 검증
            if (!pinService.validatePinSessionToken(command.getPinSessionToken(), "transfer")) {
                log.warn("Invalid PIN session token for secure transfer");
                return false;
            }
            
            // 수신자 은행 코드 검증 (기본값 설정)
            if (command.getReceiverBankCode() == null || command.getReceiverBankCode().trim().isEmpty()) {
                command.setReceiverBankCode("EASYPAY"); // 기본값으로 내부 송금 처리
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Validation error for secure transfer command: {}", command, e);
            return false;
        }
    }
    
    @Override
    public void savePending(SecureTransferCommand command) {
        // PIN 검증이 완료되었으므로 실제 송금 액션에 위임하기 전에는 별도 저장하지 않음
        // 실제 위임받은 액션이 저장을 담당
        log.info("Secure transfer PIN validation completed, delegating to actual transfer action: {}", 
                command.getTransactionId());
    }
    
    @Override
    public ActionResult execute(SecureTransferCommand command) {
        try {
            log.info("Executing secure transfer: transactionId={}, internal={}", 
                    command.getTransactionId(), command.isInternalTransfer());
            
            // PIN 검증 재확인 (보안 강화)
            if (!pinService.validatePinSessionToken(command.getPinSessionToken(), "transfer")) {
                return ActionResult.failure("INVALID_PIN_SESSION", 
                        "PIN 인증이 유효하지 않습니다", createResultData(command));
            }
            
            // 내부/외부 송금 여부에 따라 적절한 Command로 변환하여 위임
            if (command.isInternalTransfer()) {
                // 내부 송금으로 위임
                return delegateToInternalTransfer(command);
            } else {
                // 외부 송금으로 위임
                return delegateToExternalTransfer(command);
            }
            
        } catch (Exception e) {
            log.error("Unexpected error in secure transfer execution: {}", command.getTransactionId(), e);
            return ActionResult.failure("SECURE_TRANSFER_ERROR", 
                    "보안 송금 처리 중 오류가 발생했습니다: " + e.getMessage(), createResultData(command));
        }
    }
    
    @Override
    public void updateFromResult(SecureTransferCommand command, ActionResult result) {
        // 실제 송금 처리는 위임받은 액션에서 수행하므로 별도 업데이트 불필요
        // PIN 세션 토큰 무효화만 수행
        try {
            // PIN 세션 토큰 무효화는 현재 PinService에서 지원하지 않음
            // 향후 필요 시 구현 예정
            // pinService.invalidatePinSessionToken(command.getPinSessionToken());
            
            log.info("Secure transfer completed: transactionId={}, status={}", 
                    command.getTransactionId(), result.getStatus());
            
        } catch (Exception e) {
            log.error("Error in secure transfer post-processing: {}", command.getTransactionId(), e);
            // 후처리 실패는 크리티컬하지 않으므로 예외를 던지지 않음
        }
    }
    
    /**
     * 내부 송금으로 위임
     */
    private ActionResult delegateToInternalTransfer(SecureTransferCommand command) {
        try {
            // SecureTransferCommand를 InternalTransferCommand로 변환
            var internalCommand = command.toInternalTransferCommand();
            
            log.info("Delegating to internal transfer: {}", command.getTransactionId());
            
            // 직접 InternalTransferAction 호출하여 처리
            if (!internalTransferAction.validate(internalCommand)) {
                return ActionResult.failure("VALIDATION_FAILED", "내부 송금 검증에 실패했습니다", createResultData(command));
            }
            
            internalTransferAction.savePending(internalCommand);
            ActionResult result = internalTransferAction.execute(internalCommand);
            internalTransferAction.updateFromResult(internalCommand, result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to delegate to internal transfer: {}", command.getTransactionId(), e);
            return ActionResult.failure("INTERNAL_DELEGATION_FAILED", 
                    "내부 송금 처리 위임 중 오류가 발생했습니다: " + e.getMessage(), createResultData(command));
        }
    }
    
    /**
     * 외부 송금으로 위임
     */
    private ActionResult delegateToExternalTransfer(SecureTransferCommand command) {
        try {
            // SecureTransferCommand를 ExternalTransferCommand로 변환
            var externalCommand = command.toExternalTransferCommand();
            
            log.info("Delegating to external transfer: transactionId={}, bankCode={}", 
                    command.getTransactionId(), command.getReceiverBankCode());
            
            // 직접 ExternalTransferAction 호출하여 처리
            if (!externalTransferAction.validate(externalCommand)) {
                return ActionResult.failure("VALIDATION_FAILED", "외부 송금 검증에 실패했습니다", createResultData(command));
            }
            
            externalTransferAction.savePending(externalCommand);
            ActionResult result = externalTransferAction.execute(externalCommand);
            externalTransferAction.updateFromResult(externalCommand, result);
            
            return result;
            
        } catch (Exception e) {
            log.error("Failed to delegate to external transfer: {}", command.getTransactionId(), e);
            return ActionResult.failure("EXTERNAL_DELEGATION_FAILED", 
                    "외부 송금 처리 위임 중 오류가 발생했습니다: " + e.getMessage(), createResultData(command));
        }
    }
    
    /**
     * 결과 데이터 생성
     */
    private Map<String, Object> createResultData(SecureTransferCommand command) {
        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", command.getTransactionId());
        data.put("amount", command.getAmount());
        data.put("senderAccountNumber", command.getSenderAccountNumber());
        data.put("receiverAccountNumber", command.getReceiverAccountNumber());
        data.put("receiverBankCode", command.getReceiverBankCode());
        data.put("memo", command.getMemo());
        data.put("currency", command.getCurrency());
        data.put("transferType", "SECURE");
        data.put("isInternal", command.isInternalTransfer());
        // PIN 세션 토큰은 보안상 결과 데이터에 포함하지 않음
        return data;
    }
}