package fintech2.easypay.transfer.action;

import fintech2.easypay.transfer.action.command.TransferActionCommand;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 송금 액션 리졸버
 * Command 타입별로 적절한 Action을 매핑하고 해결하는 역할
 * Spring 컨테이너에서 모든 TransferAction 구현체를 자동 수집하여 매핑
 */
@Component
@Slf4j
public class TransferActionResolver {
    
    /**
     * Command 클래스와 Action 인스턴스 간의 매핑 맵
     */
    private final Map<Class<? extends TransferActionCommand>, 
                      TransferAction<? extends TransferActionCommand>> actionMap = new HashMap<>();
    
    /**
     * 생성자를 통한 의존성 주입
     * Spring이 모든 TransferAction 구현체를 자동으로 주입
     * 
     * @param actions Spring 컨테이너에서 수집된 모든 TransferAction 구현체
     */
    public TransferActionResolver(List<TransferAction<? extends TransferActionCommand>> actions) {
        log.info("Initializing TransferActionResolver with {} actions", actions.size());
        
        for (TransferAction<? extends TransferActionCommand> action : actions) {
            Class<? extends TransferActionCommand> commandType = action.commandType();
            
            // 중복 매핑 검사
            if (actionMap.containsKey(commandType)) {
                log.warn("Duplicate action mapping detected for command type: {}. " +
                        "Existing: {}, New: {}", 
                        commandType.getSimpleName(),
                        actionMap.get(commandType).getClass().getSimpleName(),
                        action.getClass().getSimpleName());
                        
                throw new IllegalStateException(
                    String.format("Multiple actions found for command type: %s", 
                    commandType.getSimpleName()));
            }
            
            actionMap.put(commandType, action);
            
            log.debug("Registered action: {} -> {}", 
                    commandType.getSimpleName(), 
                    action.getClass().getSimpleName());
        }
        
        log.info("TransferActionResolver initialization complete. Registered mappings:");
        actionMap.forEach((commandType, action) -> 
            log.info("  {} -> {}", commandType.getSimpleName(), action.getClass().getSimpleName()));
    }
    
    /**
     * Command 타입에 해당하는 Action을 해결(resolve)
     * 
     * @param command 처리할 명령 객체
     * @param <C> 명령 타입
     * @return 해당 명령을 처리할 Action 인스턴스
     * @throws IllegalArgumentException 해당 명령 타입에 대한 Action이 없는 경우
     */
    @SuppressWarnings("unchecked")
    public <C extends TransferActionCommand> TransferAction<C> resolve(C command) {
        if (command == null) {
            throw new IllegalArgumentException("Command cannot be null");
        }
        
        Class<?> commandType = command.getClass();
        
        TransferAction<C> action = (TransferAction<C>) actionMap.get(commandType);
        
        if (action == null) {
            log.error("No action found for command type: {}. Available actions: {}", 
                    commandType.getSimpleName(), 
                    actionMap.keySet().stream()
                           .map(Class::getSimpleName)
                           .toList());
                           
            throw new IllegalArgumentException(
                String.format("No action found for command type: %s. " +
                            "Make sure the corresponding TransferAction is implemented and registered.", 
                            commandType.getSimpleName()));
        }
        
        log.debug("Resolved action for command {}: {}", 
                commandType.getSimpleName(), action.getClass().getSimpleName());
                
        return action;
    }
    
    /**
     * 특정 Command 타입에 대한 Action이 등록되어 있는지 확인
     * 
     * @param commandType 확인할 명령 타입
     * @return 등록 여부
     */
    public boolean hasActionFor(Class<? extends TransferActionCommand> commandType) {
        return actionMap.containsKey(commandType);
    }
    
    /**
     * 등록된 모든 Command 타입 반환
     * 
     * @return 등록된 Command 타입들의 집합
     */
    public java.util.Set<Class<? extends TransferActionCommand>> getRegisteredCommandTypes() {
        return java.util.Collections.unmodifiableSet(actionMap.keySet());
    }
    
    /**
     * 등록된 Action 개수 반환
     * 
     * @return 등록된 Action의 개수
     */
    public int getRegisteredActionCount() {
        return actionMap.size();
    }
    
    /**
     * 특정 Command 타입에 대한 Action 클래스명 반환 (디버깅용)
     * 
     * @param commandType 명령 타입
     * @return Action 클래스명 (등록되지 않은 경우 null)
     */
    public String getActionClassName(Class<? extends TransferActionCommand> commandType) {
        TransferAction<? extends TransferActionCommand> action = actionMap.get(commandType);
        return action != null ? action.getClass().getSimpleName() : null;
    }
}