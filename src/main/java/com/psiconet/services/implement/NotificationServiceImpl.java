package com.psiconet.services.implement;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.mapper.NotificationMapper;
import com.psiconet.model.dtos.NotificationDTO;
import com.psiconet.model.dtos.NotificationPreferenceDTO;
import com.psiconet.model.entities.Notification;
import com.psiconet.model.entities.NotificationPreference;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.repositories.NotificationPreferenceRepository;
import com.psiconet.repositories.NotificationRepository;
import com.psiconet.services.interfaces.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final Set<NotificationType> APPOINTMENT_TYPES = Set.of(
            NotificationType.APPOINTMENT_SCHEDULED,
            NotificationType.APPOINTMENT_CANCELLED
    );

    private static final Set<NotificationType> CONNECTION_TYPES = Set.of(
            NotificationType.CONNECTION_REQUESTED,
            NotificationType.CONNECTION_ACCEPTED
    );

    private static final Set<NotificationType> PAYMENT_TYPES = Set.of(
            NotificationType.PAYMENT_PENDING,
            NotificationType.PAYMENT_RECEIPT_SUBMITTED,
            NotificationType.PAYMENT_APPROVED,
            NotificationType.PAYMENT_REJECTED,
            NotificationType.PAYMENT_DISPUTED
    );

    private final NotificationRepository repository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationMapper mapper;

    @Override
    @Transactional
    public void create(
            User recipient,
            User actor,
            NotificationType type,
            String title,
            String msg,
            String refType,
            UUID refId
    ) {
        if (!isCategoryEnabled(recipient, type)) {
            return;
        }

        repository.save(Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .title(title)
                .message(msg)
                .referenceType(refType)
                .referenceId(refId)
                .isRead(false)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDTO> listForUser(UUID userId) {
        return repository
                .findByRecipientIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Long countUnread(UUID userId) {
        return repository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public void markAsRead(User user, UUID id) {
        Notification notif = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(Notification.class, id));

        if (!notif.getRecipient().getId().equals(user.getId())) {
            throw new BusinessException("notification", "Você não tem permissão para acessar esta notificação.");
        }

        if (!notif.isRead()) {
            notif.setRead(true);
            notif.setReadAt(LocalDateTime.now());
            repository.save(notif);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceDTO getPreferences(User user) {
        return toDto(findOrDefaultPreference(user));
    }

    @Override
    @Transactional
    public NotificationPreferenceDTO updatePreferences(User user, NotificationPreferenceDTO dto) {
        NotificationPreference preference = preferenceRepository.findByUser(user)
                .orElseGet(() -> NotificationPreference.builder().user(user).build());

        preference.setAppointmentEnabled(dto.isAppointmentEnabled());
        preference.setConnectionEnabled(dto.isConnectionEnabled());
        preference.setPaymentEnabled(dto.isPaymentEnabled());

        return toDto(preferenceRepository.save(preference));
    }

    // Sem preferência salva = tudo habilitado por padrão (usuário ainda não configurou nada).
    private NotificationPreference findOrDefaultPreference(User user) {
        return preferenceRepository.findByUser(user)
                .orElseGet(() -> NotificationPreference.builder()
                        .user(user)
                        .appointmentEnabled(true)
                        .connectionEnabled(true)
                        .paymentEnabled(true)
                        .build());
    }

    private boolean isCategoryEnabled(User recipient, NotificationType type) {
        NotificationPreference preference = findOrDefaultPreference(recipient);

        if (APPOINTMENT_TYPES.contains(type)) return preference.isAppointmentEnabled();
        if (CONNECTION_TYPES.contains(type)) return preference.isConnectionEnabled();
        if (PAYMENT_TYPES.contains(type)) return preference.isPaymentEnabled();

        return true;
    }

    private NotificationPreferenceDTO toDto(NotificationPreference preference) {
        return NotificationPreferenceDTO.builder()
                .appointmentEnabled(preference.isAppointmentEnabled())
                .connectionEnabled(preference.isConnectionEnabled())
                .paymentEnabled(preference.isPaymentEnabled())
                .build();
    }
}
