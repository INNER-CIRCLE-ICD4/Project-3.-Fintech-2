package fintech2.easypay.transfer.action;

import fintech2.easypay.transfer.action.command.TransferActionCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송금 액션 프로세서
 * Action Pattern의 중앙 오케스트레이터
 * 표준 흐름(validate → savePending → execute → updateFromResult)을 관리하고
 * 트랜잭션 경계와 공통 오류 처리를 제공
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferActionProcessor {
    
    private final TransferActionResolver resolver;
    
    /**
     * 송금 명령을 처리하는 메인 메서드
     * 표준 Action Pattern 흐름을 orchestrate
     * 
     * @param command 처리할 송금 명령
     * @param <C> 명령 타입
     * @return 처리 결과
     */
    public <C extends TransferActionCommand> ActionResult process(C command) {
        if (command == null) {
            log.error("Transfer command cannot be null");
            return ActionResult.failure("INVALID_COMMAND", "송금 명령이 유효하지 않습니다", null);
        }
        
        long startTime = System.currentTimeMillis();
        log.info("Starting transfer action processing: commandType={}", command.getClass().getSimpleName());
        
        try {
            // 1. Action 해결 (Resolve)
            TransferAction<C> action = resolver.resolve(command);
            log.debug("Resolved action: {} for command: {}", 
                    action.getClass().getSimpleName(), command.getClass().getSimpleName());
            
            // 2. 검증 (Validate)
            ActionResult result = validateCommand(action, command);
            if (!result.isSuccess()) {
                return result;
            }
            
            // 3. 대기 상태 저장 (Save Pending) - 별도 트랜잭션
            savePendingWithTransaction(action, command);
            
            // 4. 실행 (Execute) - 별도 트랜잭션
            result = executeWithTransaction(action, command);
            
            // 5. 결과 업데이트 (Update From Result) - 별도 트랜잭션
            updateResultWithTransaction(action, command, result);
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Transfer action processing completed: commandType={}, status={}, duration={}ms",
                    command.getClass().getSimpleName(), result.getStatus(), processingTime);
            
            return result;
            
        } catch (Exception e) {
            long processingTime = System.currentTimeMillis() - startTime;
            log.error("Unexpected error during transfer action processing: commandType={}, duration={}ms", 
                    command.getClass().getSimpleName(), processingTime, e);
            
            return ActionResult.failure("PROCESSING_ERROR", 
                    "송금 처리 중 예상치 못한 오류가 발생했습니다: " + e.getMessage(), null);
        }
    }
    
    /**
     * 명령 검증 단계
     */
    private <C extends TransferActionCommand> ActionResult validateCommand(TransferAction<C> action, C command) {
        try {
            log.debug("Validating command: {}", command.getClass().getSimpleName());
            
            if (!action.validate(command)) {
                log.warn("Command validation failed: {}", command.getClass().getSimpleName());
                return ActionResult.failure("VALIDATION_FAILED", 
                        "송금 요청 검증에 실패했습니다", null);
            }
            
            log.debug("Command validation successful: {}", command.getClass().getSimpleName());
            return ActionResult.success("검증 완료", null);
            
        } catch (Exception e) {
            log.error("Error during command validation: {}", command.getClass().getSimpleName(), e);
            return ActionResult.failure("VALIDATION_ERROR", 
                    "검증 중 오류가 발생했습니다: " + e.getMessage(), null);
        }
    }
    
    /**
     * 대기 상태 저장 - 별도 트랜잭션으로 처리
     * 실행 실패 시에도 요청 기록이 남도록 보장
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <C extends TransferActionCommand> void savePendingWithTransaction(TransferAction<C> action, C command) {
        try {
            log.debug("Saving pending state: {}", command.getClass().getSimpleName());
            action.savePending(command);
            log.debug("Pending state saved successfully: {}", command.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Failed to save pending state: {}", command.getClass().getSimpleName(), e);
            throw new RuntimeException("대기 상태 저장 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 명령 실행 - 별도 트랜잭션으로 처리
     * 실제 비즈니스 로직 수행
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <C extends TransferActionCommand> ActionResult executeWithTransaction(TransferAction<C> action, C command) {
        try {
            log.info("Executing command: {}", command.getClass().getSimpleName());
            
            ActionResult result = action.execute(command);
            
            log.info("Command execution completed: commandType={}, status={}", 
                    command.getClass().getSimpleName(), result.getStatus());
            
            return result;
            
        } catch (Exception e) {
            log.error("Command execution failed: {}", command.getClass().getSimpleName(), e);
            return ActionResult.failure("EXECUTION_FAILED", 
                    "명령 실행 중 오류가 발생했습니다: " + e.getMessage(), null);
        }
    }
    
    /**
     * 결과 업데이트 - 별도 트랜잭션으로 처리
     * 감사 로그, 알림 등 후처리 작업 수행
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <C extends TransferActionCommand> void updateResultWithTransaction(TransferAction<C> action, C command, ActionResult result) {
        try {
            log.debug("Updating result: commandType={}, status={}", 
                    command.getClass().getSimpleName(), result.getStatus());
            
            action.updateFromResult(command, result);
            
            log.debug("Result updated successfully: commandType={}, status={}", 
                    command.getClass().getSimpleName(), result.getStatus());
            
        } catch (Exception e) {
            log.error("Failed to update result: commandType={}, status={}", 
                    command.getClass().getSimpleName(), result.getStatus(), e);
            // 결과 업데이트 실패는 전체 프로세스를 실패시키지 않음
            // 실제 송금은 이미 처리되었을 수 있으므로 로깅만 수행
        }
    }
    
    /**
     * 비동기 처리용 메서드 (향후 확장 가능성)
     * 큐 기반 처리나 스케줄러 연동 시 사용 가능
     */
    public <C extends TransferActionCommand> void processAsync(C command) {
        log.info("Starting async transfer processing: {}", command.getClass().getSimpleName());
        
        // 현재는 동기 처리와 동일하지만, 향후 메시지 큐나 별도 스레드풀로 확장 가능
        try {
            ActionResult result = process(command);
            log.info("Async transfer processing completed: commandType={}, status={}", 
                    command.getClass().getSimpleName(), result.getStatus());
        } catch (Exception e) {
            log.error("Async transfer processing failed: {}", command.getClass().getSimpleName(), e);
        }
    }
    
    /**
     * 특정 Action이 지원되는지 확인
     */
    public boolean canProcess(Class<? extends TransferActionCommand> commandType) {
        return resolver.hasActionFor(commandType);
    }
    
    /**
     * 등록된 Action 개수 반환 (모니터링용)
     */
    public int getRegisteredActionCount() {
        return resolver.getRegisteredActionCount();
    }
    
    /**
     * 등록된 모든 Command 타입 반환 (디버깅용)
     */
    public java.util.Set<Class<? extends TransferActionCommand>> getSupportedCommandTypes() {
        return resolver.getRegisteredCommandTypes();
    }
}