package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.VideoSessionLog;
import com.psiconet.model.enums.clinical.VideoSessionEventEnum;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VideoSessionLogRepository extends JpaRepository<VideoSessionLog, UUID> {

    boolean existsByProviderEventId(String providerEventId);

    boolean existsByAppointmentIdAndUserIdAndEvent(UUID appointmentId, UUID userId, VideoSessionEventEnum event);

    List<VideoSessionLog> findByAppointmentIdOrderByOccurredAtAsc(UUID appointmentId);
}
