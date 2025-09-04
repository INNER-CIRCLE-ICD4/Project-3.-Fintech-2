package fintech2.easypay.transfer.action;

import fintech2.easypay.transfer.command.TransferActionCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 송금 액션 리졸버
 * 커맨드 타입에 따라 적절한 액션을 매핑하고 반환
 * Spring의 의존성 주입을 통해 모든 액션들을 자동으로 등록
 */
@Component
@Slf4j
public class TransferActionResolver {
    
    private final Map<Class<? extends TransferActionCommand>, TransferAction<? extends TransferActionCommand>> actionMap;
    
    /**
     * 생성자에서 모든 TransferAction 구현체들을 자동으로 등록
     * Spring이 TransferAction 타입의 모든 빈들을 주입
     */
    public TransferActionResolver(List<TransferAction<? extends TransferActionCommand>> actions) {
        this.actionMap = new HashMap<>();
        
        for (TransferAction<? extends TransferActionCommand> action : actions) {
            Class<? extends TransferActionCommand> commandType = action.getCommandType();
            actionMap.put(commandType, action);
            log.info("송금 액션 등록: {} -> {}", commandType.getSimpleName(), action.getClass().getSimpleName());
        }
        
        log.info("총 {}개의 송금 액션이 등록되었습니다.", actionMap.size());
    }
    
    /**
     * 커맨드에 해당하는 액션을 찾아서 반환
     * 
     * @param command 처리할 커맨드
     * @param <C> 커맨드 타입
     * @return 해당 커맨드를 처리할 수 있는 액션
     * @throws IllegalArgumentException 해당 커맨드를 처리할 액션이 없는 경우
     */
    @SuppressWarnings("unchecked")
    public <C extends TransferActionCommand> TransferAction<C> resolve(C command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        
        Class<? extends TransferActionCommand> commandType = command.getClass();
        TransferAction<C> action = (TransferAction<C>) actionMap.get(commandType);
        
        if (action == null) {
            log.error("지원되지 않는 커맨드 타입: {}", commandType.getName());
            throw new IllegalArgumentException("No action found for command: " + commandType.getName());
        }
        
        log.debug("액션 해결됨: {} -> {}", commandType.getSimpleName(), action.getClass().getSimpleName());
        return action;
    }
    
    /**
     * 등록된 모든 액션 타입 반환 (디버깅 및 모니터링용)
     */
    public Map<String, String> getRegisteredActions() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<Class<? extends TransferActionCommand>, TransferAction<? extends TransferActionCommand>> entry : actionMap.entrySet()) {
            result.put(entry.getKey().getSimpleName(), entry.getValue().getClass().getSimpleName());
        }
        return result;
    }
    
    /**
     * 특정 커맨드 타입이 지원되는지 확인
     */
    public boolean isSupported(Class<? extends TransferActionCommand> commandType) {
        return actionMap.containsKey(commandType);
    }
    
    /**
     * 등록된 액션 개수 반환
     */
    public int getActionCount() {
        return actionMap.size();
    }
}