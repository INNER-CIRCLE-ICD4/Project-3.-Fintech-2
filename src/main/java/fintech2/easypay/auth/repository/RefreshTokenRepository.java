package fintech2.easypay.auth.repository;

import fintech2.easypay.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    
    /**
     * 토큰으로 RefreshToken 조회
     */
    Optional<RefreshToken> findByToken(String token);
    
    /**
     * 사용자 ID로 유효한 RefreshToken 조회
     */
    Optional<RefreshToken> findByUserIdAndIsRevokedFalse(Long userId);
    
    /**
     * 사용자 ID로 모든 RefreshToken 폐기
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.isRevoked = true, rt.revokedAt = :revokedAt WHERE rt.userId = :userId")
    void revokeAllByUserId(@Param("userId") Long userId, @Param("revokedAt") LocalDateTime revokedAt);
    
    /**
     * 사용자 ID로 기존 Refresh Token 업데이트 (한 사용자당 하나만 유지)
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.token = :token, rt.expiresAt = :expiresAt, rt.isRevoked = false, rt.revokedAt = null WHERE rt.userId = :userId")
    int updateRefreshTokenForUser(@Param("userId") Long userId, @Param("token") String token, @Param("expiresAt") LocalDateTime expiresAt);
    
    /**
     * 만료된 토큰들 삭제
     *
     * @return
     */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now")
    int deleteExpiredTokens(@Param("now") LocalDateTime now);
} 