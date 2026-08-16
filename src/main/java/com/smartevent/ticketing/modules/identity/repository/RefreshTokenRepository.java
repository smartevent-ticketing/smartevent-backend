package com.smartevent.ticketing.modules.identity.repository;

import com.smartevent.ticketing.modules.identity.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Query("SELECT r FROM RefreshToken r JOIN FETCH r.user u LEFT JOIN FETCH u.userRoles ur LEFT JOIN FETCH ur.role WHERE r.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :revokedAt WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllUserTokens(@Param("userId") UUID userId, @Param("revokedAt") Instant revokedAt);
}
