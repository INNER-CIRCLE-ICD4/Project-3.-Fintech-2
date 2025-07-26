package fintech2.easypay.payment.controller;

import fintech2.easypay.auth.CustomUserDetails;
import fintech2.easypay.common.ApiResponse;
import fintech2.easypay.payment.dto.PaymentRequest;
import fintech2.easypay.payment.dto.PaymentResponse;
import fintech2.easypay.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    
    private final PaymentService paymentService;
    
    /**
     * 결제 처리
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody PaymentRequest request) {
        
        log.info("결제 요청: 사용자={}, 가맹점={}, 금액={}", 
                user.getMember().getPhoneNumber(), request.getMerchantName(), request.getAmount());
        
        PaymentResponse response = paymentService.processPayment(user.getMember().getPhoneNumber(), request);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 결제 취소
     */
    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String paymentId,
            @RequestParam(required = false) String reason) {
        
        log.info("결제 취소 요청: 사용자={}, 결제ID={}, 사유={}", 
                user.getMember().getPhoneNumber(), paymentId, reason);
        
        PaymentResponse response = paymentService.cancelPayment(
                user.getMember().getPhoneNumber(), paymentId, reason);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 결제 환불
     */
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<ApiResponse<PaymentResponse>> refundPayment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String paymentId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String reason) {
        
        log.info("결제 환불 요청: 사용자={}, 결제ID={}, 금액={}, 사유={}", 
                user.getMember().getPhoneNumber(), paymentId, amount, reason);
        
        PaymentResponse response = paymentService.refundPayment(
                user.getMember().getPhoneNumber(), paymentId, amount, reason);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
    
    /**
     * 결제 내역 조회
     */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> getPaymentHistory(
            @AuthenticationPrincipal CustomUserDetails user,
            Pageable pageable) {
        
        log.info("결제 내역 조회: 사용자={}", user.getMember().getPhoneNumber());
        
        Page<PaymentResponse> payments = paymentService.getPaymentHistory(
                user.getMember().getPhoneNumber(), pageable);
        
        return ResponseEntity.ok(ApiResponse.success(payments));
    }
    
    /**
     * 결제 상세 조회
     */
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable String paymentId) {
        
        log.info("결제 상세 조회: 사용자={}, 결제ID={}", 
                user.getMember().getPhoneNumber(), paymentId);
        
        PaymentResponse response = paymentService.getPayment(
                user.getMember().getPhoneNumber(), paymentId);
        
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}