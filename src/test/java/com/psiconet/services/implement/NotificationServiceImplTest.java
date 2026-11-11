package com.psiconet.services.implement;

import com.psiconet.mapper.NotificationMapper;
import com.psiconet.model.entities.Notification;
import com.psiconet.model.entities.NotificationPreference;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.repositories.NotificationPreferenceRepository;
import com.psiconet.repositories.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceImplTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationPreferenceRepository preferenceRepository = mock(NotificationPreferenceRepository.class);
    private final NotificationServiceImpl service =
            new NotificationServiceImpl(repository, preferenceRepository, mock(NotificationMapper.class));

    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(UUID.randomUUID());
        // Usuário desligou TODAS as categorias configuráveis
        when(preferenceRepository.findByUser(user)).thenReturn(Optional.of(NotificationPreference.builder()
                .user(user).appointmentEnabled(false).connectionEnabled(false).paymentEnabled(false).build()));
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {
            "TICKET_REPLIED", "TICKET_CLOSED", "ADMIN_ANNOUNCEMENT",
            "MEDICAL_RECORD_ACCESS_REQUESTED", "MEDICAL_RECORD_ACCESS_GRANTED", "MEDICAL_RECORD_ACCESS_DENIED"})
    void notificacoesObrigatoriasIgnoramPreferencias(NotificationType type) {
        service.create(user, null, type, "t", "m", "TICKET", UUID.randomUUID());
        verify(repository).save(any(Notification.class));
    }

    @Test
    void categoriaDesativadaNaoGeraNotificacao() {
        service.create(user, null, NotificationType.PAYMENT_PENDING, "t", "m", "PAYMENT", UUID.randomUUID());
        verify(repository, never()).save(any());
    }
}
