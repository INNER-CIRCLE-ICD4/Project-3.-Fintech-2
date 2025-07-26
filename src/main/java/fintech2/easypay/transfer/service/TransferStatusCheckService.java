package fintech2.easypay.transfer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.audit.entity.AuditEventType;
import fintech2.easypay.audit.service.AuditLogService;
import fintech2.easypay.audit.service.NotificationService;
import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.entity.TransferStatus;
import fintech2.easypay.transfer.external.BankingApiResponse;
import fintech2.easypay.transfer.external.BankingApiService;
import fintech2.easypay.transfer.external.BankingApiStatus;
import fintech2.easypay.transfer.repository.TransferRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 거래 상태 확인 스케줄러
 * 타임아웃 또는 UNKNOWN 상태의 거래들을 주기적으로 확인하여 최종 상태를 업데이트
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TransferStatusCheckService {
    
    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final BankingApiService bankingApiService;
    private final AuditLogService auditLogService;
    private final NotificationService notificationService;
    
    /**
     * 5분마다 확인 대상 거래들의 상태를 체크
     */
    @Scheduled(fixedDelay = 300000) // 5분 = 300,000ms
    @Async("taskExecutor")
    @Transactional
    public void checkPendingTransferStatus() {
        log.info("거래 상태 확인 스케줄러 시작");
        
        // 10분 이전에 생성된 TIMEOUT, UNKNOWN 상태의 거래들 조회
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(10);
        List<Transfer> pendingTransfers = transferRepository
            .findByStatusInAndCreatedAtBefore(
                List.of(TransferStatus.TIMEOUT, TransferStatus.UNKNOWN, TransferStatus.PROCESSING), 
                cutoffTime);
        
        if (pendingTransfers.isEmpty()) {
            log.info("확인할 대기 중인 거래가 없습니다.");
            return;
        }
        
        log.info("확인할 거래 수: {}", pendingTransfers.size());
        
        for (Transfer transfer : pendingTransfers) {
            try {
                checkAndUpdateTransferStatus(transfer);
            } catch (Exception e) {
                log.error("거래 상태 확인 중 오류 발생: {} - {}", transfer.getTransactionId(), e.getMessage());
            }
        }
        
        log.info("거래 상태 확인 스케줄러 완료");
    }
    
    /**
     * 개별 거래 상태 확인 및 업데이트
     */
    private void checkAndUpdateTransferStatus(Transfer transfer) {
        log.info("거래 상태 확인 시작: {} (현재상태: {})", 
                transfer.getTransactionId(), transfer.getStatus());
        
        try {
            // 외부 API로 실제 거래 상태 확인
            BankingApiResponse statusResponse = bankingApiService
                .getTransferStatus(transfer.getTransactionId());
            
            if (statusResponse.getStatus() == BankingApiStatus.SUCCESS) {
                // 성공: 잔액 이동 및 상태 업데이트
                handleSuccessfulTransfer(transfer, statusResponse);
                
            } else if (statusResponse.getStatus() == BankingApiStatus.FAILED ||
                       statusResponse.getStatus() == BankingApiStatus.SYSTEM_ERROR ||
                       statusResponse.getStatus() == BankingApiStatus.INSUFFICIENT_BALANCE ||
                       statusResponse.getStatus() == BankingApiStatus.INVALID_ACCOUNT) {
                // 실패: 상태만 업데이트 (잔액 이동 없음)
                handleFailedTransfer(transfer, statusResponse);
                
            } else {
                // 여전히 처리중이거나 알 수 없음: 상태 유지
                log.info("거래 여전히 처리중: {} - {}", 
                        transfer.getTransactionId(), statusResponse.getStatus());
            }
            
        } catch (Exception e) {
            log.error("외부 API 호출 실패: {} - {}", transfer.getTransactionId(), e.getMessage());
            
            // 24시간 이상 확인되지 않은 거래는 실패 처리
            if (transfer.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24))) {
                markAsSystemFailure(transfer);
            }
        }
    }
    
    /**
     * 성공한 거래 처리
     */
    private void handleSuccessfulTransfer(Transfer transfer, BankingApiResponse response) {
        log.info("지연 처리 거래 성공 확인: {}", transfer.getTransactionId());
        
        // 계좌 락 획득하여 잔액 이동
        Account senderAccount = accountRepository.findByIdWithLock(
            transfer.getSender().getId()).orElse(null);
        Account receiverAccount = accountRepository.findByIdWithLock(
            transfer.getReceiver().getId()).orElse(null);
        
        if (senderAccount != null && receiverAccount != null) {
            // 송금자 잔액이 충분한지 다시 확인
            if (senderAccount.hasEnoughBalance(transfer.getAmount())) {
                senderAccount.withdraw(transfer.getAmount());
                receiverAccount.deposit(transfer.getAmount());
                
                transfer.markAsCompleted();
                transfer.setBankTransactionId(response.getBankTransactionId());
                
                // 감사 로그 기록
                auditLogService.logSuccess(
                    transfer.getSender().getId(),
                    transfer.getSender().getPhoneNumber(),
                    AuditEventType.TRANSFER_SUCCESS,
                    String.format("지연 처리 송금 완료: %s (%s원)", 
                        transfer.getTransactionId(), transfer.getAmount()),
                    null, null,
                    String.format("amount: %s", transfer.getAmount()),
                    response.getMessage()
                );
                
                // 알림 전송
                notificationService.sendTransferActivityNotification(
                    transfer.getSender().getId(),
                    transfer.getSender().getPhoneNumber(),
                    String.format("송금이 완료되었습니다. %s원이 %s로 송금되었습니다.", 
                        transfer.getAmount(), transfer.getReceiverAccountNumber())
                );
                
                notificationService.sendTransferActivityNotification(
                    transfer.getReceiver().getId(),
                    transfer.getReceiver().getPhoneNumber(),
                    String.format("입금이 완료되었습니다. %s원이 %s로부터 입금되었습니다.", 
                        transfer.getAmount(), transfer.getSenderAccountNumber())
                );
                
            } else {
                // 잔액 부족 시 실패 처리
                transfer.markAsFailed("잔액 부족으로 인한 거래 실패");
                log.warn("지연 처리 중 잔액 부족 발견: {}", transfer.getTransactionId());
            }
        }
    }
    
    /**
     * 실패한 거래 처리
     */
    private void handleFailedTransfer(Transfer transfer, BankingApiResponse response) {
        log.info("거래 실패 확인: {} - {}", transfer.getTransactionId(), response.getStatus());
        
        String failureReason = String.format("외부 API 확인 결과 실패: %s - %s", 
            response.getStatus().getDescription(), response.getErrorMessage());
        transfer.markAsFailed(failureReason);
        
        // 감사 로그 기록
        auditLogService.logFailure(
            transfer.getSender().getId(),
            transfer.getSender().getPhoneNumber(),
            AuditEventType.TRANSFER_FAILED,
            String.format("거래 실패 확인: %s", transfer.getTransactionId()),
            null, null,
            String.format("amount: %s", transfer.getAmount()),
            failureReason
        );
        
        // 알림 전송
        notificationService.sendTransferActivityNotification(
            transfer.getSender().getId(),
            transfer.getSender().getPhoneNumber(),
            String.format("송금이 실패했습니다. %s원 송금 요청이 처리되지 않았습니다.", 
                transfer.getAmount())
        );
    }
    
    /**
     * 시스템 실패로 처리 (24시간 이상 확인되지 않은 경우)
     */
    private void markAsSystemFailure(Transfer transfer) {
        log.warn("24시간 이상 확인되지 않은 거래를 시스템 실패로 처리: {}", 
                transfer.getTransactionId());
        
        transfer.markAsFailed("시스템 오류로 인한 거래 실패 (24시간 경과)");
        
        // 감사 로그 기록
        auditLogService.logError(
            transfer.getSender().getId(),
            transfer.getSender().getPhoneNumber(),
            AuditEventType.TRANSFER_FAILED,
            String.format("시스템 실패 처리: %s", transfer.getTransactionId()),
            null, null,
            String.format("amount: %s", transfer.getAmount()),
            "24시간 이상 상태 확인 불가"
        );
        
        // 보안 알림 전송 (운영팀)
        notificationService.sendSecurityAlert(
            transfer.getSender().getId(),
            transfer.getSender().getPhoneNumber(),
            String.format("거래 상태 확인 불가: %s (24시간 경과)", 
                transfer.getTransactionId())
        );
    }
}