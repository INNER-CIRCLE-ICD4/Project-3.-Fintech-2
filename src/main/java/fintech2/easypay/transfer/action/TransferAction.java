package fintech2.easypay.transfer.action;

import fintech2.easypay.transfer.action.command.TransferActionCommand;

/**
 * 송금 액션 인터페이스
 * 모든 송금 액션이 구현해야 하는 기본 계약
 * 
 * @param <C> 처리할 명령 타입 (TransferActionCommand의 하위 타입)
 */
public interface TransferAction<C extends TransferActionCommand> {
    
    /**
     * 이 액션이 처리할 커맨드 타입
     * 리졸버가 명령과 액션을 매핑하는 데 사용
     * 
     * @return 처리할 명령 클래스
     */
    Class<C> commandType();
    
    /**
     * 사전 검증 단계
     * 잔액, 한도, 계좌 상태, 입력값 등을 검증
     * 
     * @param command 검증할 명령
     * @return 검증 통과 여부
     */
    boolean validate(C command);
    
    /**
     * 보류 상태 저장 단계
     * 거래 생성, 아웃박스 기록 등 처리 전 상태 저장
     * 중복 처리 방지를 위한 비즈니스 키 생성 포함
     * 
     * @param command 저장할 명령
     */
    void savePending(C command);
    
    /**
     * 실제 수행 단계
     * 외부 API 호출 또는 내부 DB 처리
     * 트랜잭션 외부에서 실행될 수 있음
     * 
     * @param command 실행할 명령
     * @return 실행 결과
     */
    ActionResult execute(C command);
    
    /**
     * 결과 반영 단계
     * 전표/원장 업데이트, 상태 전이, 알림 등
     * 실행 결과에 따른 후속 처리
     * 
     * @param command 처리된 명령
     * @param result 실행 결과
     */
    void updateFromResult(C command, ActionResult result);
}