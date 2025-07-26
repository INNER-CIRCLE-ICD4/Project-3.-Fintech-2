package fintech2.easypay.payment.service.impl;

import fintech2.easypay.audit.entity.AuditEventType;
import fintech2.easypay.audit.entity.AuditLog;
import fintech2.easypay.audit.entity.AuditStatus;
import fintech2.easypay.audit.repository.AuditLogRepository;
import fintech2.easypay.payment.service.PaymentAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 모듈용 감사 로그 서비스 구현체
 * 실제 AuditLogRepository를 사용하여 감사 로그 기능 제공
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentAuditServiceImpl implements PaymentAuditService {
    
    private final AuditLogRepository auditLogRepository;
    
    @Override
    public void logPaymentSuccess(Long memberId, String phoneNumber, String eventDescription,
                                 String requestData, String responseData) {
        AuditLog auditLog = AuditLog.builder()
                .eventType(AuditEventType.PAYMENT_SUCCESS)
                .status(AuditStatus.SUCCESS)
                .memberId(memberId)
                .phoneNumber(phoneNumber)
                .eventDescription(eventDescription)
                .requestData(requestData)
                .responseData(responseData)
                .build();
        
        auditLogRepository.save(auditLog);
    }
    
    @Override
    public void logPaymentFailure(Long memberId, String phoneNumber, String eventDescription,
                                 String requestData, String errorMessage) {
        AuditLog auditLog = AuditLog.builder()
                .eventType(AuditEventType.PAYMENT_FAILED)
                .status(AuditStatus.FAILED)
                .memberId(memberId)
                .phoneNumber(phoneNumber)
                .eventDescription(eventDescription)
                .requestData(requestData)
                .errorMessage(errorMessage)
                .build();
        
        auditLogRepository.save(auditLog);
    }
    
    @Override
    public void logPaymentCancel(Long memberId, String phoneNumber, String eventDescription,
                                String requestData, String responseData) {
        AuditLog auditLog = AuditLog.builder()
                .eventType(AuditEventType.PAYMENT_CANCEL)
                .status(AuditStatus.SUCCESS)
                .memberId(memberId)
                .phoneNumber(phoneNumber)
                .eventDescription(eventDescription)
                .requestData(requestData)
                .responseData(responseData)
                .build();
        
        auditLogRepository.save(auditLog);
    }
    
    @Override
    public void logPaymentRefund(Long memberId, String phoneNumber, String eventDescription,
                                String requestData, String responseData) {
        AuditLog auditLog = AuditLog.builder()
                .eventType(AuditEventType.PAYMENT_REFUND)
                .status(AuditStatus.SUCCESS)
                .memberId(memberId)
                .phoneNumber(phoneNumber)
                .eventDescription(eventDescription)
                .requestData(requestData)
                .responseData(responseData)
                .build();
        
        auditLogRepository.save(auditLog);
    }
}