package fintech2.easypay.transfer.action;

import fintech2.easypay.transfer.command.TransferActionCommand;

/**
 * 송금 액션 인터페이스
 * 모든 송금 처리 로직의 표준 인터페이스
 * 
 * @param <C> 처리할 커맨드 타입
 */
public interface TransferAction<C extends TransferActionCommand> {
    
    /**
     * 이 액션이 처리할 커맨드 타입 반환
     * ActionResolver가 매핑에 사용
     * 
     * @return 커맨드 클래스 타입
     */
    Class<C> getCommandType();
    
    /**
     * 송금 요청 사전 검증
     * 잔액, 한도, 계좌 상태 등을 확인
     * 
     * @param command 검증할 커맨드
     * @return 검증 성공 여부
     */
    boolean validate(C command);
    
    /**
     * 거래를 보류 상태로 저장
     * 실제 송금 처리 전 데이터베이스에 거래 기록 생성
     * 중복 방지를 위한 비즈니스 키 설정 포함
     * 
     * @param command 처리할 커맨드
     */
    void savePending(C command);
    
    /**
     * 실제 송금 수행
     * 외부 API 호출 또는 내부 잔액 이동 처리
     * 
     * @param command 실행할 커맨드
     * @return 송금 실행 결과
     */
    ActionResult execute(C command);
    
    /**
     * 송금 결과 반영 및 후처리
     * 성공/실패에 따른 상태 업데이트, 알림 전송 등
     * 
     * @param command 처리된 커맨드
     * @param result 실행 결과
     */
    void updateFromResult(C command, ActionResult result);
    
    /**
     * 액션 타입 반환 (로깅 및 모니터링용)
     * 
     * @return 액션 타입 문자열
     */
    default String getActionType() {
        return this.getClass().getSimpleName();
    }
}