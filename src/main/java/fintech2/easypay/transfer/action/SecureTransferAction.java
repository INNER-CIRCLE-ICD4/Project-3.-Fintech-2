package fintech2.easypay.transfer.action;

import fintech2.easypay.auth.service.PinService;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.transfer.command.SecureTransferCommand;
import fintech2.easypay.transfer.command.InternalTransferCommand;
import fintech2.easypay.transfer.command.ExternalTransferCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * PIN 검증이 필요한 보안 송금 처리 액션
 * PIN 인증 후 적절한 송금 액션으로 위임
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SecureTransferAction implements TransferAction<SecureTransferCommand> {
    
    private final PinService pinService;
    private final ApplicationContext applicationContext;
    
    @Override
    public Class<SecureTransferCommand> getCommandType() {
        return SecureTransferCommand.class;
    }
    
    @Override
    public boolean validate(SecureTransferCommand command) {
        log.debug("보안 송금 검증 시작: {}", command.getTransactionId());
        
        // PIN 세션 토큰 검증
        if (command.getPinSessionToken() == null || command.getPinSessionToken().isBlank()) {
            log.warn("PIN 세션 토큰이 없음");
            return false;
        }
        
        if (!pinService.validatePinSessionToken(command.getPinSessionToken(), "transfer")) {
            log.warn("PIN 세션 토큰이 유효하지 않음");
            return false;
        }
        
        // 기본 송금 검증은 위임할 액션에서 수행
        log.debug("보안 송금 PIN 검증 완료: {}", command.getTransactionId());
        return true;
    }
    
    @Override
    public void savePending(SecureTransferCommand command) {
        log.debug("보안 송금 보류 저장: {}", command.getTransactionId());
        
        // 적절한 액션에 위임
        TransferAction<?> delegateAction = getDelegateAction(command);
        if (command.isExternal()) {
            ExternalTransferCommand externalCommand = new ExternalTransferCommand(
                    command.getSenderId(),
                    command.getSenderAccountNumber(),
                    command.getReceiverAccountNumber(),
                    command.getReceiverBankCode(),
                    "외부은행", // 실제로는 은행명 조회 필요
                    command.getAmount(),
                    command.getMemo(),
                    command.getTransactionId()
            );
            ((TransferAction<ExternalTransferCommand>) delegateAction).savePending(externalCommand);
        } else {
            InternalTransferCommand internalCommand = new InternalTransferCommand(
                    command.getSenderId(),
                    command.getReceiverId(),
                    command.getSenderAccountNumber(),
                    command.getReceiverAccountNumber(),
                    command.getAmount(),
                    command.getMemo(),
                    command.getTransactionId()
            );
            ((TransferAction<InternalTransferCommand>) delegateAction).savePending(internalCommand);
        }
    }
    
    @Override
    public ActionResult execute(SecureTransferCommand command) {
        log.info("보안 송금 실행 시작: {}", command.getTransactionId());
        
        try {
            // PIN 세션 토큰 재검증
            if (!pinService.validatePinSessionToken(command.getPinSessionToken(), "transfer")) {
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("error", "PIN 세션이 만료되었습니다.");
                return ActionResult.failure("PIN_SESSION_EXPIRED", 
                        "PIN 인증이 만료되었습니다. 다시 인증해주세요.", 
                        command.getTransactionId(), errorData);
            }
            
            // 적절한 액션으로 위임하여 실행
            if (command.isExternal()) {
                ExternalTransferCommand externalCommand = new ExternalTransferCommand(
                        command.getSenderId(),
                        command.getSenderAccountNumber(),
                        command.getReceiverAccountNumber(),
                        command.getReceiverBankCode(),
                        "외부은행", // 실제로는 은행명 조회 필요
                        command.getAmount(),
                        command.getMemo(),
                        command.getTransactionId()
                );
                
                ExternalTransferAction externalAction = applicationContext.getBean(ExternalTransferAction.class);
                return externalAction.execute(externalCommand);
                
            } else {
                InternalTransferCommand internalCommand = new InternalTransferCommand(
                        command.getSenderId(),
                        command.getReceiverId(),
                        command.getSenderAccountNumber(),
                        command.getReceiverAccountNumber(),
                        command.getAmount(),
                        command.getMemo(),
                        command.getTransactionId()
                );
                
                InternalTransferAction internalAction = applicationContext.getBean(InternalTransferAction.class);
                return internalAction.execute(internalCommand);
            }
            
        } catch (Exception e) {
            log.error("보안 송금 실행 실패: {} - {}", command.getTransactionId(), e.getMessage(), e);
            
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", e.getMessage());
            
            return ActionResult.failure("SECURE_TRANSFER_ERROR", 
                    "보안 송금 처리 중 오류가 발생했습니다: " + e.getMessage(),
                    command.getTransactionId(), errorData);
        }
    }
    
    @Override
    public void updateFromResult(SecureTransferCommand command, ActionResult result) {
        log.debug("보안 송금 결과 업데이트: {} - {}", command.getTransactionId(), result.getStatus());
        
        // 적절한 액션에 위임하여 결과 업데이트
        if (command.isExternal()) {
            ExternalTransferCommand externalCommand = new ExternalTransferCommand(
                    command.getSenderId(),
                    command.getSenderAccountNumber(),
                    command.getReceiverAccountNumber(),
                    command.getReceiverBankCode(),
                    "외부은행", // 실제로는 은행명 조회 필요
                    command.getAmount(),
                    command.getMemo(),
                    command.getTransactionId()
            );
            
            ExternalTransferAction externalAction = applicationContext.getBean(ExternalTransferAction.class);
            externalAction.updateFromResult(externalCommand, result);
            
        } else {
            InternalTransferCommand internalCommand = new InternalTransferCommand(
                    command.getSenderId(),
                    command.getReceiverId(),
                    command.getSenderAccountNumber(),
                    command.getReceiverAccountNumber(),
                    command.getAmount(),
                    command.getMemo(),
                    command.getTransactionId()
            );
            
            InternalTransferAction internalAction = applicationContext.getBean(InternalTransferAction.class);
            internalAction.updateFromResult(internalCommand, result);
        }
    }
    
    /**
     * 위임할 액션을 결정
     */
    private TransferAction<?> getDelegateAction(SecureTransferCommand command) {
        if (command.isExternal()) {
            return applicationContext.getBean(ExternalTransferAction.class);
        } else {
            return applicationContext.getBean(InternalTransferAction.class);
        }
    }
}