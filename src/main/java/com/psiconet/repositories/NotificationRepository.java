package com.psiconet.repositories;

import com.psiconet.model.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);
    List<Notification> findTop100ByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            "UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.recipient.id = :userId AND n.isRead = false")
    int markAllAsRead(@org.springframework.data.repository.query.Param("userId") UUID userId,
                      @org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
    Long countByRecipientIdAndIsReadFalse(UUID recipientId);
}