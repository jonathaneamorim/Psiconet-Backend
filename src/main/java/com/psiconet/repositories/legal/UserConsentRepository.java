package com.psiconet.repositories.legal;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.legal.UserConsent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserConsentRepository extends JpaRepository<UserConsent, UUID> {
    List<UserConsent> findByUserOrderByAcceptedAtDesc(User user);
}
