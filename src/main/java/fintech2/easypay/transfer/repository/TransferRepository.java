package fintech2.easypay.transfer.repository;

import fintech2.easypay.transfer.entity.Transfer;
import fintech2.easypay.transfer.entity.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {
    
    Optional<Transfer> findByTransactionId(String transactionId);
    
    @Query("SELECT t FROM Transfer t WHERE t.sender.id = :memberId OR t.receiver.id = :memberId ORDER BY t.createdAt DESC")
    Page<Transfer> findByMemberIdOrderByCreatedAtDesc(@Param("memberId") Long memberId, Pageable pageable);
    
    @Query("SELECT t FROM Transfer t WHERE t.sender.phoneNumber = :phoneNumber OR t.receiver.phoneNumber = :phoneNumber ORDER BY t.createdAt DESC")
    Page<Transfer> findByPhoneNumberOrderByCreatedAtDesc(@Param("phoneNumber") String phoneNumber, Pageable pageable);
    
    Page<Transfer> findBySenderIdOrderByCreatedAtDesc(Long senderId, Pageable pageable);
    
    Page<Transfer> findByReceiverIdOrderByCreatedAtDesc(Long receiverId, Pageable pageable);
    
    Page<Transfer> findByStatusOrderByCreatedAtDesc(TransferStatus status, Pageable pageable);
    
    @Query("SELECT t FROM Transfer t WHERE t.createdAt BETWEEN :startDate AND :endDate ORDER BY t.createdAt DESC")
    Page<Transfer> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate, 
                                        @Param("endDate") LocalDateTime endDate, 
                                        Pageable pageable);
    
    @Query("SELECT t FROM Transfer t WHERE t.sender.id = :memberId AND t.status = :status ORDER BY t.createdAt DESC")
    List<Transfer> findBySenderIdAndStatus(@Param("memberId") Long memberId, 
                                         @Param("status") TransferStatus status);
    
    /**
     * 특정 상태들과 생성 시간 조건으로 거래 조회 (스케줄러용)
     */
    List<Transfer> findByStatusInAndCreatedAtBefore(List<TransferStatus> statuses, 
                                                   LocalDateTime createdAt);
    
    boolean existsByTransactionId(String transactionId);
}
