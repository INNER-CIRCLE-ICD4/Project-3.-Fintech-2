package fintech2.easypay.payment.entity;

import fintech2.easypay.payment.common.PaymentBaseEntity;
import fintech2.easypay.member.entity.Member;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment extends PaymentBaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "payment_id", unique = true, nullable = false)
    private String paymentId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    
    @Column(name = "account_number", nullable = false)
    private String accountNumber;
    
    @Column(name = "merchant_id", nullable = false)
    private String merchantId;
    
    @Column(name = "merchant_name", nullable = false)
    private String merchantName;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "memo")
    private String memo;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.REQUESTED;
    
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
    
    @Column(name = "failed_reason")
    private String failedReason;
    
    @Column(name = "pg_transaction_id")
    private String pgTransactionId;
    
    @Column(name = "pg_response", columnDefinition = "TEXT")
    private String pgResponse;
    
    public void markAsProcessing() {
        this.status = PaymentStatus.PROCESSING;
    }
    
    public void markAsApproved(String pgTransactionId, String pgResponse) {
        this.status = PaymentStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.pgTransactionId = pgTransactionId;
        this.pgResponse = pgResponse;
    }
    
    public void markAsFailed(String reason) {
        this.status = PaymentStatus.FAILED;
        this.failedReason = reason;
    }
    
    public void markAsCancelled() {
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }
    
    public void markAsRefunded() {
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = LocalDateTime.now();
    }
    
    public boolean canBeCancelled() {
        return this.status == PaymentStatus.APPROVED;
    }
    
    public boolean canBeRefunded() {
        return this.status == PaymentStatus.APPROVED;
    }
    
    public boolean isApproved() {
        return this.status == PaymentStatus.APPROVED;
    }
    
    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }
    
    public boolean isCancelled() {
        return this.status == PaymentStatus.CANCELLED;
    }
    
    public boolean isRefunded() {
        return this.status == PaymentStatus.REFUNDED;
    }
}
