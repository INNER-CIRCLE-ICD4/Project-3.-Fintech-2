package fintech2.easypay.payment.service.impl;

import fintech2.easypay.payment.service.PaymentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 결제 모듈용 알림 서비스 구현체
 * 비동기로 알림을 처리하며, 실제 알림 발송 로직을 포함
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationServiceImpl implements PaymentNotificationService {
    
    @Override
    @Async("notificationExecutor")
    public void sendPaymentActivityNotification(Long memberId, String phoneNumber, String message) {
        log.info("결제 활동 알림 전송 - 회원ID: {}, 전화번호: {}, 메시지: {}", memberId, phoneNumber, message);
        
        // 실제 알림 전송 로직 (SMS, Push 등)을 여기에 구현
        // 현재는 로그만 출력
        simulateNotificationSending("PAYMENT_ACTIVITY", phoneNumber, message);
    }
    
    @Override
    @Async("notificationExecutor")
    public void sendPaymentFailureNotification(Long memberId, String phoneNumber, String message) {
        log.info("결제 실패 알림 전송 - 회원ID: {}, 전화번호: {}, 메시지: {}", memberId, phoneNumber, message);
        
        simulateNotificationSending("PAYMENT_FAILURE", phoneNumber, message);
    }
    
    @Override
    @Async("notificationExecutor")
    public void sendRefundNotification(Long memberId, String phoneNumber, String message) {
        log.info("환불 알림 전송 - 회원ID: {}, 전화번호: {}, 메시지: {}", memberId, phoneNumber, message);
        
        simulateNotificationSending("REFUND", phoneNumber, message);
    }
    
    /**
     * 알림 전송 시뮬레이션
     */
    private void simulateNotificationSending(String type, String phoneNumber, String message) {
        try {
            // 알림 전송 시뮬레이션 (100ms 지연)
            Thread.sleep(100);
            log.info("알림 전송 완료 - 타입: {}, 전화번호: {}", type, phoneNumber);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("알림 전송 중 인터럽트 발생: {}", e.getMessage());
        }
    }
}