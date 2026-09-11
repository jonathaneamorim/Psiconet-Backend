package com.psiconet.services.interfaces;

import com.psiconet.model.dtos.NotificationDTO;
import com.psiconet.model.dtos.NotificationPreferenceDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.NotificationType;

import java.util.List;
import java.util.UUID;

public interface NotificationService {

    void create(
            User recipient,
            User actor,
            NotificationType type,
            String title,
            String msg,
            String refType,
            UUID refId
    );

    List<NotificationDTO> listForUser(UUID userId);

    Long countUnread(UUID userId);

    void markAsRead(User user, UUID id);

    NotificationPreferenceDTO getPreferences(User user);

    NotificationPreferenceDTO updatePreferences(User user, NotificationPreferenceDTO dto);
}
