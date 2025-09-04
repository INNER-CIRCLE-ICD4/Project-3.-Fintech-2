package fintech2.easypay.transfer.action;

import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.transfer.command.TransferActionCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송금 액션 프로세서
 * 모든 송금 작업의 전체 흐름을 오케스트레이션
 * validate -> savePending -> execute -> updateFromResult 순서로 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransferActionProcessor {
    
    private final TransferActionResolver resolver;
    
    /**
     * 송금 액션의 전체 처리 흐름 실행
     * 
     * @param command 실행할 송금 커맨드
     * @param <C> 커맨드 타입
     * @return 송금 처리 결과
     * @throws BusinessException 처리 중 오류 발생 시
     */
    @Transactional
    public <C extends TransferActionCommand> ActionResult process(C command) {
        if (command == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Command cannot be null");
        }
        
        log.info("송금 처리 시작: {} - {}", command.getClass().getSimpleName(), 
                getTransactionId(command));
        
        // 1. 해당 커맨드를 처리할 액션 찾기
        TransferAction<C> action = resolver.resolve(command);
        log.debug("액션 해결됨: {}", action.getActionType());
        
        ActionResult result = null;
        try {
            // 2. 사전 검증
            log.debug("송금 검증 시작: {}", getTransactionId(command));
            if (!action.validate(command)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST, 
                        "송금 요청 검증에 실패했습니다: " + command);
            }
            log.debug("송금 검증 완료: {}", getTransactionId(command));
            
            // 3. 보류 상태로 저장
            log.debug("송금 보류 저장 시작: {}", getTransactionId(command));
            action.savePending(command);
            log.debug("송금 보류 저장 완료: {}", getTransactionId(command));
            
            // 4. 실제 송금 실행
            log.debug("송금 실행 시작: {}", getTransactionId(command));
            result = action.execute(command);
            log.debug("송금 실행 완료: {} - 상태: {}", getTransactionId(command), result.getStatus());
            
            // 5. 결과 반영 및 후처리
            log.debug("송금 결과 업데이트 시작: {}", getTransactionId(command));
            action.updateFromResult(command, result);
            log.debug("송금 결과 업데이트 완료: {}", getTransactionId(command));
            
            log.info("송금 처리 완료: {} - 상태: {} - 메시지: {}", 
                    getTransactionId(command), result.getStatus(), result.getMessage());
            
            return result;
            
        } catch (BusinessException e) {
            log.error("송금 처리 실패 (비즈니스 예외): {} - {}", getTransactionId(command), e.getMessage());
            
            // 비즈니스 예외의 경우 실패 결과를 생성하여 후처리
            if (result == null) {
                result = ActionResult.failure(
                        e.getErrorCode().name(),
                        e.getMessage(),
                        getTransactionId(command),
                        null
                );
            }
            
            try {
                action.updateFromResult(command, result);
            } catch (Exception updateException) {
                log.error("송금 실패 후처리 중 오류: {} - {}", 
                        getTransactionId(command), updateException.getMessage());
            }
            
            throw e;
            
        } catch (Exception e) {
            log.error("송금 처리 실패 (시스템 오류): {} - {}", getTransactionId(command), e.getMessage(), e);
            
            // 시스템 예외의 경우 실패 결과를 생성하여 후처리
            if (result == null) {
                result = ActionResult.failure(
                        "SYSTEM_ERROR",
                        "시스템 오류가 발생했습니다: " + e.getMessage(),
                        getTransactionId(command),
                        null
                );
            }
            
            try {
                action.updateFromResult(command, result);
            } catch (Exception updateException) {
                log.error("송금 실패 후처리 중 오류: {} - {}", 
                        getTransactionId(command), updateException.getMessage());
            }
            
            throw new BusinessException(ErrorCode.TRANSACTION_FAILED, 
                    "송금 처리 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
    
    /**
     * 비동기 처리용 메서드 (향후 확장용)
     * 외부 API 호출이나 시간이 오래 걸리는 작업에 사용
     */
    @Transactional
    public <C extends TransferActionCommand> void processAsync(C command) {
        // TODO: 비동기 처리 로직 구현
        // 1. validate -> savePending까지만 동기적으로 처리
        // 2. execute -> updateFromResult는 별도 스레드에서 처리
        // 3. 이벤트 발행을 통한 비동기 처리 고려
        
        log.info("비동기 송금 처리 요청: {}", getTransactionId(command));
        throw new UnsupportedOperationException("비동기 처리는 아직 구현되지 않았습니다.");
    }
    
    /**
     * 커맨드에서 거래 ID 추출 (리플렉션 사용)
     * 로깅을 위한 헬퍼 메서드
     */
    private String getTransactionId(TransferActionCommand command) {
        try {
            var field = command.getClass().getDeclaredField("transactionId");
            field.setAccessible(true);
            return (String) field.get(command);
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }
    
    /**
     * 프로세서 상태 정보 반환 (모니터링용)
     */
    public ProcessorStatus getStatus() {
        return new ProcessorStatus(
                resolver.getActionCount(),
                resolver.getRegisteredActions()
        );
    }
    
    /**
     * 프로세서 상태 정보 클래스
     */
    public static class ProcessorStatus {
        private final int registeredActionCount;
        private final java.util.Map<String, String> registeredActions;
        
        public ProcessorStatus(int registeredActionCount, java.util.Map<String, String> registeredActions) {
            this.registeredActionCount = registeredActionCount;
            this.registeredActions = registeredActions;
        }
        
        public int getRegisteredActionCount() { return registeredActionCount; }
        public java.util.Map<String, String> getRegisteredActions() { return registeredActions; }
    }
}