package fintech2.easypay.payment.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.common.ErrorCode;
import fintech2.easypay.member.entity.Member;
import fintech2.easypay.payment.config.PaymentTestConfig;
import fintech2.easypay.payment.dto.PaymentRequest;
import fintech2.easypay.payment.dto.PaymentResponse;
import fintech2.easypay.payment.entity.Payment;
import fintech2.easypay.payment.entity.PaymentMethod;
import fintech2.easypay.payment.entity.PaymentStatus;
import fintech2.easypay.payment.external.PaymentGatewayService;
import fintech2.easypay.payment.external.PgApiRequest;
import fintech2.easypay.payment.external.PgApiResponse;
import fintech2.easypay.payment.external.PgApiStatus;
import fintech2.easypay.payment.mock.MockAccountService;
import fintech2.easypay.payment.mock.MockMemberService;
import fintech2.easypay.payment.mock.MockPaymentAuditService;
import fintech2.easypay.payment.mock.MockPaymentNotificationService;
import fintech2.easypay.payment.repository.PaymentRepository;

/**
 * 결제 서비스 독립 테스트
 * Mock을 사용하여 외부 의존성 없이 결제 로직만 단독으로 테스트
 */
@ContextConfiguration(classes = PaymentTestConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RefactoredPaymentServiceTest {

    @Autowired
    private RefactoredPaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentGatewayService paymentGatewayService;

    @Autowired
    private MockAccountService mockAccountService;

    @Autowired
    private MockMemberService mockMemberService;

    @Autowired
    private MockPaymentAuditService mockAuditService;

    @Autowired
    private MockPaymentNotificationService mockNotificationService;

    private Member testMember;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        // Mock 데이터 초기화
        mockAccountService.clearTestData();
        mockMemberService.clearTestData();
        mockAuditService.clearAuditLogs();
        mockNotificationService.clearNotificationLogs();

        // 테스트용 회원 데이터 설정
        testMember = Member.builder()
                .id(1L)
                .phoneNumber("010-1234-5678")
                .name("홍길동")
                .password("password123")
                .build();
        mockMemberService.addTestMember(testMember);

        // 테스트용 계좌 데이터 설정
        testAccount = Account.builder()
                .id(1L)
                .accountNumber("1234567890")
                .balance(new BigDecimal("1000000")) // 100만원
                .member(testMember)
                .build();
        mockAccountService.addTestAccount(testAccount);
    }

    @Test
    @DisplayName("잔액 결제 성공 테스트")
    void testBalancePaymentSuccess() {
        // Given: 잔액 결제 요청 데이터
        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("50000"),
                PaymentMethod.BALANCE,
                "잔액 결제 테스트",
                "테스트 상품",
                "ORDER001",
                null, null, null, null, null
        );

        Payment savedPayment = Payment.builder()
                .paymentId("PAY123456789ABC")
                .member(testMember)
                .status(PaymentStatus.REQUESTED)
                .amount(request.getAmount())
                .merchantName(request.getMerchantName())
                .build();

        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);

        // When: 결제 처리 실행
        PaymentResponse response = paymentService.processPayment("010-1234-5678", request);

        // Then: 결제 결과 검증
        assertThat(response.getPaymentId()).isEqualTo("PAY123456789ABC");
        
        // 계좌 잔액 확인 (100만원 - 5만원 = 95만원)
        assertThat(testAccount.getBalance()).isEqualByComparingTo(new BigDecimal("950000"));
        
        // 감사 로그 확인
        var auditLogs = mockAuditService.getAuditLogsByType("PAYMENT_SUCCESS");
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getMemberId()).isEqualTo(1L);
        assertThat(auditLogs.get(0).getEventDescription()).contains("잔액 결제 승인");
        
        // 알림 확인
        var notifications = mockNotificationService.getNotificationLogsByType("PAYMENT_ACTIVITY");
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getMessage()).contains("결제되었습니다");
    }

    @Test
    @DisplayName("잔액 부족 시 결제 실패 테스트")
    void testInsufficientBalancePaymentFailure() {
        // Given: 잔액보다 큰 금액 결제 요청
        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("2000000"), // 200만원 (잔액 100만원보다 큼)
                PaymentMethod.BALANCE,
                "잔액 부족 테스트",
                "테스트 상품",
                "ORDER001",
                null, null, null, null, null
        );

        // When & Then: 잔액 부족 예외 발생 확인
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            paymentService.processPayment("010-1234-5678", request);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
        
        // 계좌 잔액이 변경되지 않았는지 확인
        assertThat(testAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1000000"));
    }

    @Test
    @DisplayName("카드 결제 성공 테스트")
    void testCardPaymentSuccess() {
        // Given: 카드 결제 요청 데이터
        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("30000"),
                PaymentMethod.CARD,
                "카드 결제 테스트",
                "테스트 상품",
                "ORDER001",
                "1234-5678-9012-3456", "12/25", "123", null, null
        );

        Payment savedPayment = Payment.builder()
                .paymentId("PAY123456789ABC")
                .member(testMember)
                .status(PaymentStatus.REQUESTED)
                .amount(request.getAmount())
                .merchantName(request.getMerchantName())
                .build();

        // PG API 성공 응답 설정
        PgApiResponse pgResponse = new PgApiResponse();
        pgResponse.setStatus(PgApiStatus.SUCCESS);
        pgResponse.setPgTransactionId("PG_TXN_123");
        pgResponse.setRawResponse("카드 결제 승인");

        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(paymentGatewayService.processPayment(any(PgApiRequest.class))).thenReturn(pgResponse);

        // When: 결제 처리 실행
        PaymentResponse response = paymentService.processPayment("010-1234-5678", request);

        // Then: 결제 결과 검증
        assertThat(response.getPaymentId()).isEqualTo("PAY123456789ABC");
        
        // PG API 호출 확인
        verify(paymentGatewayService, times(1)).processPayment(any(PgApiRequest.class));
        
        // 계좌 잔액은 변경되지 않음 (카드 결제)
        assertThat(testAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1000000"));
        
        // 감사 로그 확인
        var auditLogs = mockAuditService.getAuditLogsByType("PAYMENT_SUCCESS");
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getEventDescription()).contains("결제 승인");
    }

    @Test
    @DisplayName("카드 결제 실패 테스트")
    void testCardPaymentFailure() {
        // Given: 카드 결제 요청 데이터
        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("30000"),
                PaymentMethod.CARD,
                "카드 결제 실패 테스트",
                "테스트 상품",
                "ORDER001",
                "1234-5678-9012-3456", "12/25", "123", null, null
        );

        Payment savedPayment = Payment.builder()
                .paymentId("PAY123456789ABC")
                .member(testMember)
                .status(PaymentStatus.REQUESTED)
                .amount(request.getAmount())
                .merchantName(request.getMerchantName())
                .build();

        // PG API 실패 응답 설정
        PgApiResponse pgResponse = new PgApiResponse();
        pgResponse.setStatus(PgApiStatus.FAILED);
        pgResponse.setErrorMessage("카드 승인 거절");

        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        when(paymentGatewayService.processPayment(any(PgApiRequest.class))).thenReturn(pgResponse);

        // When & Then: 결제 실패 예외 발생 확인
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            paymentService.processPayment("010-1234-5678", request);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PAYMENT_FAILED);
        
        // 감사 로그 확인 (실패)
        var auditLogs = mockAuditService.getAuditLogsByType("PAYMENT_FAILURE");
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getEventDescription()).contains("결제 실패");
        
        // 실패 알림 확인
        var notifications = mockNotificationService.getNotificationLogsByType("PAYMENT_FAILURE");
        assertThat(notifications).hasSize(1);
    }

    @Test
    @DisplayName("존재하지 않는 회원 결제 실패 테스트")
    void testPaymentWithNonExistentMember() {
        // Given: 존재하지 않는 회원 번호
        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("30000"),
                PaymentMethod.BALANCE,
                "테스트",
                "테스트 상품",
                "ORDER001",
                null, null, null, null, null
        );

        // When & Then: 회원 없음 예외 발생 확인
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            paymentService.processPayment("010-9999-9999", request);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("계좌 없는 회원 결제 실패 테스트")
    void testPaymentWithMemberWithoutAccount() {
        // Given: 계좌가 없는 회원 생성
        Member memberWithoutAccount = Member.builder()
                .id(999L)
                .phoneNumber("010-9999-8888")
                .name("계좌없음")
                .password("password123")
                .build();
        mockMemberService.addTestMember(memberWithoutAccount);

        PaymentRequest request = new PaymentRequest(
                "MERCHANT001",
                "테스트 상점",
                new BigDecimal("30000"),
                PaymentMethod.BALANCE,
                "테스트",
                "테스트 상품",
                "ORDER001",
                null, null, null, null, null
        );

        // When & Then: 계좌 없음 예외 발생 확인
        BusinessException exception = assertThrows(BusinessException.class, () -> {
            paymentService.processPayment("010-9999-8888", request);
        });

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("결제 취소 성공 테스트")
    void testPaymentCancelSuccess() {
        // Given: 취소 가능한 결제 생성
        Payment payment = Payment.builder()
                .paymentId("PAY123456789ABC")
                .member(testMember)
                .status(PaymentStatus.APPROVED)
                .amount(new BigDecimal("50000"))
                .merchantName("테스트 거래처")
                .paymentMethod(PaymentMethod.BALANCE)
                .build();

        when(paymentRepository.findByPaymentIdAndMemberId("PAY123456789ABC", 1L))
                .thenReturn(Optional.of(payment));

        // When: 결제 취소 실행
        PaymentResponse response = paymentService.cancelPayment("010-1234-5678", "PAY123456789ABC", "고객 요청");

        // Then: 취소 결과 검증
        assertThat(response.getPaymentId()).isEqualTo("PAY123456789ABC");
        
        // 잔액 복원 확인 (100만원 + 5만원 = 105만원)
        assertThat(testAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1050000"));
        
        // 감사 로그 확인
        var auditLogs = mockAuditService.getAuditLogsByType("PAYMENT_CANCEL");
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getEventDescription()).contains("결제 취소");
    }

    @Test
    @DisplayName("결제 환불 성공 테스트")
    void testPaymentRefundSuccess() {
        // Given: 환불 가능한 결제 생성
        Payment payment = Payment.builder()
                .paymentId("PAY123456789ABC")
                .member(testMember)
                .status(PaymentStatus.APPROVED)
                .amount(new BigDecimal("50000"))
                .merchantName("테스트 거래처")
                .paymentMethod(PaymentMethod.BALANCE)
                .build();

        when(paymentRepository.findByPaymentIdAndMemberId("PAY123456789ABC", 1L))
                .thenReturn(Optional.of(payment));

        // When: 결제 환불 실행
        PaymentResponse response = paymentService.refundPayment(
                "010-1234-5678", 
                "PAY123456789ABC", 
                new BigDecimal("30000"), 
                "부분 환불"
        );

        // Then: 환불 결과 검증
        assertThat(response.getPaymentId()).isEqualTo("PAY123456789ABC");
        
        // 잔액 복원 확인 (100만원 + 3만원 = 103만원)
        assertThat(testAccount.getBalance()).isEqualByComparingTo(new BigDecimal("1030000"));
        
        // 감사 로그 확인
        var auditLogs = mockAuditService.getAuditLogsByType("PAYMENT_REFUND");
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getEventDescription()).contains("결제 환불");
        
        // 환불 알림 확인
        var notifications = mockNotificationService.getNotificationLogsByType("REFUND");
        assertThat(notifications).hasSize(1);
    }
}