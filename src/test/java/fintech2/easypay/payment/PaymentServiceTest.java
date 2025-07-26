package fintech2.easypay.payment;

import fintech2.easypay.account.entity.Account;
import fintech2.easypay.account.entity.AccountStatus;
import fintech2.easypay.account.repository.AccountRepository;
import fintech2.easypay.audit.service.AuditLogService;
import fintech2.easypay.audit.service.NotificationService;
import fintech2.easypay.common.BusinessException;
import fintech2.easypay.member.entity.Member;
import fintech2.easypay.member.repository.MemberRepository;
import fintech2.easypay.payment.dto.PaymentRequest;
import fintech2.easypay.payment.dto.PaymentResponse;
import fintech2.easypay.payment.entity.Payment;
import fintech2.easypay.payment.entity.PaymentMethod;
import fintech2.easypay.payment.entity.PaymentStatus;
import fintech2.easypay.payment.external.*;
import fintech2.easypay.payment.repository.PaymentRepository;
import fintech2.easypay.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 결제 서비스 테스트
 * 외부 PG API 호출 및 실패 시나리오 테스트 포함
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("결제 서비스 테스트")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private AccountRepository accountRepository;
    
    @Mock
    private MemberRepository memberRepository;
    
    @Mock
    private AuditLogService auditLogService;
    
    @Mock
    private NotificationService notificationService;
    
    @Mock
    private PaymentGatewayService paymentGatewayService;
    
    @InjectMocks
    private PaymentService paymentService;
    
    private Member member;
    private Account account;
    private PaymentRequest cardPaymentRequest;
    private PaymentRequest balancePaymentRequest;
    
    @BeforeEach
    void setUp() {
        // 회원 설정
        member = Member.builder()
                .id(1L)
                .phoneNumber("01012345678")
                .name("테스트사용자")
                .build();
        
        // 계좌 설정
        account = Account.builder()
                .id(1L)
                .accountNumber("1111111111")
                .member(member)
                .balance(BigDecimal.valueOf(100000))
                .status(AccountStatus.ACTIVE)
                .build();
        
        // 카드 결제 요청 설정
        cardPaymentRequest = new PaymentRequest();
        cardPaymentRequest.setMerchantId("MERCHANT001");
        cardPaymentRequest.setMerchantName("테스트 상점");
        cardPaymentRequest.setAmount(BigDecimal.valueOf(50000));
        cardPaymentRequest.setPaymentMethod(PaymentMethod.CARD);
        cardPaymentRequest.setMemo("카드 결제 테스트");
        cardPaymentRequest.setProductName("테스트 상품");
        cardPaymentRequest.setOrderNumber("ORDER001");
        cardPaymentRequest.setCardNumber("1234567890123456");
        cardPaymentRequest.setCardExpiryDate("1225");
        cardPaymentRequest.setCardCvv("123");
        
        // 잔액 결제 요청 설정
        balancePaymentRequest = new PaymentRequest();
        balancePaymentRequest.setMerchantId("MERCHANT001");
        balancePaymentRequest.setMerchantName("테스트 상점");
        balancePaymentRequest.setAmount(BigDecimal.valueOf(30000));
        balancePaymentRequest.setPaymentMethod(PaymentMethod.BALANCE);
        balancePaymentRequest.setMemo("잔액 결제 테스트");
        balancePaymentRequest.setProductName("테스트 상품");
        balancePaymentRequest.setOrderNumber("ORDER002");
    }
    
    @Test
    @DisplayName("카드 결제 성공 - 외부 PG API 성공")
    void processCardPaymentSuccess() {
        // given
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(member));
        when(accountRepository.findByMemberId(1L)).thenReturn(Optional.of(account));
        when(paymentRepository.existsByPaymentId(anyString())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // 외부 PG API 성공 응답 설정
        PgApiResponse pgResponse = PgApiResponse.builder()
                .paymentId("PAY123456789012")
                .pgTransactionId("PG-12345")
                .status(PgApiStatus.SUCCESS)
                .message("결제가 정상적으로 승인되었습니다.")
                .processedAt(LocalDateTime.now())
                .approvedAmount(BigDecimal.valueOf(50000))
                .approvalNumber("12345678")
                .cardCompany("신한카드")
                .cardNumber("1234-****-****-3456")
                .build();
        when(paymentGatewayService.processPayment(any(PgApiRequest.class))).thenReturn(pgResponse);
        
        // when
        PaymentResponse response = paymentService.processPayment("01012345678", cardPaymentRequest);
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        
        // 외부 PG API 호출 검증
        verify(paymentGatewayService, times(1)).processPayment(any(PgApiRequest.class));
        
        // 잔액은 변경되지 않아야 함 (카드 결제)
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100000));
    }
    
    @Test
    @DisplayName("잔액 결제 성공 - 외부 API 호출 없음")
    void processBalancePaymentSuccess() {
        // given
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(member));
        when(accountRepository.findByMemberId(1L)).thenReturn(Optional.of(account));
        when(paymentRepository.existsByPaymentId(anyString())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        PaymentResponse response = paymentService.processPayment("01012345678", balancePaymentRequest);
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        
        // 외부 PG API 호출되지 않음 검증
        verify(paymentGatewayService, never()).processPayment(any(PgApiRequest.class));
        
        // 잔액 차감 검증
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(70000));
    }
    
    @Test
    @DisplayName("카드 결제 실패 - 외부 PG API 실패")
    void processCardPaymentFailByPgError() {
        // given
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(member));
        when(accountRepository.findByMemberId(1L)).thenReturn(Optional.of(account));
        when(paymentRepository.existsByPaymentId(anyString())).thenReturn(false);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // 외부 PG API 실패 응답 설정
        PgApiResponse pgResponse = PgApiResponse.builder()
                .paymentId("PAY123456789012")
                .status(PgApiStatus.INVALID_CARD)
                .errorCode("E101")
                .errorMessage("유효하지 않은 카드번호입니다.")
                .processedAt(LocalDateTime.now())
                .build();
        when(paymentGatewayService.processPayment(any(PgApiRequest.class))).thenReturn(pgResponse);
        
        // when & then
        assertThatThrownBy(() -> paymentService.processPayment("01012345678", cardPaymentRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PG 오류: 유효하지 않은 카드");
        
        // 외부 PG API 호출 검증
        verify(paymentGatewayService, times(1)).processPayment(any(PgApiRequest.class));
        
        // 잔액이 변경되지 않았는지 검증
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(100000));
    }
    
    @Test
    @DisplayName("잔액 결제 실패 - 잔액 부족")
    void processBalancePaymentFailByInsufficientBalance() {
        // given
        // 잔액을 10,000원으로 설정
        account = Account.builder()
                .id(1L)
                .accountNumber("1111111111")
                .member(member)
                .balance(BigDecimal.valueOf(10000))
                .status(AccountStatus.ACTIVE)
                .build();
        
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(member));
        when(accountRepository.findByMemberId(1L)).thenReturn(Optional.of(account));
        
        // when & then
        assertThatThrownBy(() -> paymentService.processPayment("01012345678", balancePaymentRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("잔액이 부족합니다");
        
        // 외부 PG API 호출되지 않음 검증
        verify(paymentGatewayService, never()).processPayment(any(PgApiRequest.class));
    }
    
    @Test
    @DisplayName("결제 취소 성공 - 카드 결제")
    void cancelCardPaymentSuccess() {
        // given
        Payment payment = Payment.builder()
                .paymentId("PAY123456789012")
                .member(member)
                .accountNumber("1111111111")
                .merchantId("MERCHANT001")
                .merchantName("테스트 거래처")
                .amount(BigDecimal.valueOf(50000))
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.APPROVED)
                .pgTransactionId("PG-12345")
                .build();
        
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(member));
        when(paymentRepository.findByPaymentIdAndMemberId("PAY123456789012", 1L))
                .thenReturn(Optional.of(payment));
        
        // 외부 PG API 취소 성공 응답
        PgApiResponse cancelResponse = PgApiResponse.builder()
                .paymentId("PAY123456789012")
                .status(PgApiStatus.CANCELLED)
                .message("결제가 취소되었습니다.")
                .processedAt(LocalDateTime.now())
                .build();
        when(paymentGatewayService.cancelPayment("PAY123456789012", "고객 요청"))
                .thenReturn(cancelResponse);
        
        // when
        PaymentResponse response = paymentService.cancelPayment("01012345678", "PAY123456789012", "고객 요청");
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        
        // 외부 PG API 호출 검증
        verify(paymentGatewayService, times(1)).cancelPayment("PAY123456789012", "고객 요청");
    }
    
    @Test
    @DisplayName("결제 취소 성공 - 잔액 결제 (잔액 복원)")
    void cancelBalancePaymentSuccess() {
        // given
        Payment payment = Payment.builder()
                .paymentId("PAY123456789012")
                .member(member)
                .accountNumber("1111111111")
                .merchantId("MERCHANT001")
                .merchantName("테스트 거래처")
                .amount(BigDecimal.valueOf(30000))
                .paymentMethod(PaymentMethod.BALANCE)
                .status(PaymentStatus.APPROVED)
                .build();
        
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(member));
        when(paymentRepository.findByPaymentIdAndMemberId("PAY123456789012", 1L))
                .thenReturn(Optional.of(payment));
        when(accountRepository.findByMemberId(1L)).thenReturn(Optional.of(account));
        
        // when
        PaymentResponse response = paymentService.cancelPayment("01012345678", "PAY123456789012", "고객 요청");
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        
        // 외부 PG API 호출되지 않음 검증
        verify(paymentGatewayService, never()).cancelPayment(anyString(), anyString());
        
        // 잔액 복원 검증
        assertThat(account.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(130000));
    }
    
    @Test
    @DisplayName("결제 환불 성공")
    void refundPaymentSuccess() {
        // given
        Payment payment = Payment.builder()
                .paymentId("PAY123456789012")
                .member(member)
                .accountNumber("1111111111")
                .merchantId("MERCHANT001")
                .merchantName("테스트 거래처")
                .amount(BigDecimal.valueOf(50000))
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.APPROVED)
                .pgTransactionId("PG-12345")
                .build();
        
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(member));
        when(paymentRepository.findByPaymentIdAndMemberId("PAY123456789012", 1L))
                .thenReturn(Optional.of(payment));
        
        // 외부 PG API 환불 성공 응답
        PgApiResponse refundResponse = PgApiResponse.builder()
                .paymentId("PAY123456789012")
                .status(PgApiStatus.REFUNDED)
                .message("환불이 완료되었습니다.")
                .processedAt(LocalDateTime.now())
                .approvedAmount(BigDecimal.valueOf(-20000))
                .build();
        when(paymentGatewayService.refundPayment("PAY123456789012", BigDecimal.valueOf(20000), "부분 환불"))
                .thenReturn(refundResponse);
        
        // when
        PaymentResponse response = paymentService.refundPayment("01012345678", "PAY123456789012", 
                BigDecimal.valueOf(20000), "부분 환불");
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        
        // 외부 PG API 호출 검증
        verify(paymentGatewayService, times(1))
                .refundPayment("PAY123456789012", BigDecimal.valueOf(20000), "부분 환불");
    }
    
    @Test
    @DisplayName("결제 상세 조회")
    void getPaymentDetail() {
        // given
        Payment payment = Payment.builder()
                .paymentId("PAY123456789012")
                .member(member)
                .accountNumber("1111111111")
                .merchantId("MERCHANT001")
                .merchantName("테스트 거래처")
                .amount(BigDecimal.valueOf(50000))
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.APPROVED)
                .pgTransactionId("PG-12345")
                .build();
        
        when(memberRepository.findByPhoneNumber("01012345678")).thenReturn(Optional.of(member));
        when(paymentRepository.findByPaymentIdAndMemberId("PAY123456789012", 1L))
                .thenReturn(Optional.of(payment));
        
        // when
        PaymentResponse response = paymentService.getPayment("01012345678", "PAY123456789012");
        
        // then
        assertThat(response).isNotNull();
        assertThat(response.getPaymentId()).isEqualTo("PAY123456789012");
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
    }
}