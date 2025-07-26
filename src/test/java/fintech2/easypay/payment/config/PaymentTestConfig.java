package fintech2.easypay.payment.config;

import fintech2.easypay.payment.external.PaymentGatewayService;
import fintech2.easypay.payment.mock.MockAccountService;
import fintech2.easypay.payment.mock.MockMemberService;
import fintech2.easypay.payment.mock.MockPaymentAuditService;
import fintech2.easypay.payment.mock.MockPaymentNotificationService;
import fintech2.easypay.payment.repository.PaymentRepository;
import fintech2.easypay.payment.service.AccountService;
import fintech2.easypay.payment.service.MemberService;
import fintech2.easypay.payment.service.PaymentAuditService;
import fintech2.easypay.payment.service.PaymentNotificationService;
import fintech2.easypay.payment.service.RefactoredPaymentService;
import org.springframework.boot.test.context.TestConfiguration;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 결제 모듈 독립 테스트용 설정
 * 외부 의존성을 모두 Mock으로 대체하여 결제 로직만 테스트 가능
 */
@TestConfiguration
public class PaymentTestConfig {
    
    @Bean
    @Primary
    public PaymentRepository paymentRepository() {
        return Mockito.mock(PaymentRepository.class);
    }
    
    @Bean
    @Primary
    public PaymentGatewayService paymentGatewayService() {
        return Mockito.mock(PaymentGatewayService.class);
    }
    
    /**
     * Mock 계좌 서비스 Bean 등록
     */
    @Bean
    @Primary
    public AccountService accountService() {
        return new MockAccountService();
    }
    
    /**
     * Mock 회원 서비스 Bean 등록
     */
    @Bean
    @Primary
    public MemberService memberService() {
        return new MockMemberService();
    }
    
    /**
     * Mock 감사 로그 서비스 Bean 등록
     */
    @Bean
    @Primary
    public PaymentAuditService paymentAuditService() {
        return new MockPaymentAuditService();
    }
    
    /**
     * Mock 알림 서비스 Bean 등록
     */
    @Bean
    @Primary
    public PaymentNotificationService paymentNotificationService() {
        return new MockPaymentNotificationService();
    }
    
    /**
     * 리팩토링된 결제 서비스 Bean 등록
     */
    @Bean
    public RefactoredPaymentService refactoredPaymentService(
            PaymentRepository paymentRepository,
            PaymentGatewayService paymentGatewayService,
            AccountService accountService,
            MemberService memberService,
            PaymentAuditService auditService,
            PaymentNotificationService notificationService) {
        
        return new RefactoredPaymentService(
                paymentRepository,
                paymentGatewayService,
                accountService,
                memberService,
                auditService,
                notificationService
        );
    }
}