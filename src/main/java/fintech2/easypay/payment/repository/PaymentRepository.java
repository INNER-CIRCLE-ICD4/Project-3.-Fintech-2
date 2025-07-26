package fintech2.easypay.payment.repository;

import fintech2.easypay.payment.entity.Payment;
import fintech2.easypay.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 정보 관리를 위한 데이터 접근 계층
 * 결제 내역 조회, 상태별 검색, 통계 정보 제공
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    Optional<Payment> findByPaymentId(String paymentId);
    
    Page<Payment> findByMemberIdOrderByCreatedAtDesc(Long memberId, Pageable pageable);
    
    @Query("SELECT p FROM Payment p WHERE p.member.phoneNumber = :phoneNumber ORDER BY p.createdAt DESC")
    Page<Payment> findByPhoneNumberOrderByCreatedAtDesc(@Param("phoneNumber") String phoneNumber, Pageable pageable);
    
    Page<Payment> findByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, PaymentStatus status, Pageable pageable);
    
    Page<Payment> findByMerchantIdOrderByCreatedAtDesc(String merchantId, Pageable pageable);
    
    Page<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status, Pageable pageable);
    
    @Query("SELECT p FROM Payment p WHERE p.createdAt BETWEEN :startDate AND :endDate ORDER BY p.createdAt DESC")
    Page<Payment> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                       @Param("endDate") LocalDateTime endDate, 
                                       Pageable pageable);
    
    @Query("SELECT p FROM Payment p WHERE p.member.id = :memberId AND p.status = :status ORDER BY p.createdAt DESC")
    List<Payment> findByMemberIdAndStatus(@Param("memberId") Long memberId, 
                                        @Param("status") PaymentStatus status);
    
    boolean existsByPaymentId(String paymentId);
    
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.member.id = :memberId AND p.status = :status")
    BigDecimal getTotalAmountByMemberIdAndStatus(@Param("memberId") Long memberId, 
                                               @Param("status") PaymentStatus status);
    
    Optional<Payment> findByPaymentIdAndMemberId(String paymentId, Long memberId);
}
