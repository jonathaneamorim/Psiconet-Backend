package com.psiconet.repositories;

import com.psiconet.model.entities.NotificationPreference;
import com.psiconet.model.entities.access.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    Optional<NotificationPreference> findByUser(User user);
}
