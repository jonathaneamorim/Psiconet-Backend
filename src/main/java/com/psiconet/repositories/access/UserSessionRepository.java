package com.psiconet.repositories.access;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.access.UserSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    // Serializa renovações concorrentes da mesma sessão (várias abas/requisições ao mesmo tempo).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM UserSession s JOIN FETCH s.user WHERE s.id = :id")
    Optional<UserSession> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByIdAndUser_IdAndRevokedAtIsNullAndAbsoluteExpiresAtAfter(UUID id, UUID userId, Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now, s.revokedReason = :reason WHERE s.user = :user AND s.revokedAt IS NULL")
    int revokeAllActive(@Param("user") User user, @Param("now") Instant now,
                        @Param("reason") com.psiconet.model.enums.access.SessionRevocationReasonEnum reason);
}
