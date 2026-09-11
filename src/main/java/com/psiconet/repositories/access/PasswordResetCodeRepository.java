package com.psiconet.repositories.access;

import com.psiconet.model.entities.access.PasswordResetCode;
import com.psiconet.model.entities.access.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    Optional<PasswordResetCode> findByTokenHashAndUsedFalse(String tokenHash);

    @Modifying
    @Query("DELETE FROM PasswordResetCode c WHERE c.user = :user AND c.used = false")
    void deleteUnusedByUser(User user);
}
