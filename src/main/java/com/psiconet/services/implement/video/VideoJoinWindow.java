package com.psiconet.services.implement.video;

import com.psiconet.infra.video.VideoProperties;
import com.psiconet.model.entities.clinical.Appointment;
import com.psiconet.model.enums.clinical.MeetingProviderEnum;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Janela de entrada na sala integrada, calculada SEMPRE pelo servidor. Como o resto do sistema, usa
 * {@link LocalDateTime} no fuso do servidor (America/Sao_Paulo em produção — ADR 016); o frontend só
 * exibe os horários devolvidos aqui.
 */
@Component
@RequiredArgsConstructor
public class VideoJoinWindow {

    private final VideoProperties properties;

    public boolean appliesTo(Appointment appointment) {
        return appointment.getMeetingType() == MeetingTypeEnum.VIDEO_CALL
                && appointment.getMeetingProvider() == MeetingProviderEnum.IN_APP;
    }

    public LocalDateTime opensAt(Appointment appointment) {
        return appointment.getStartDateTime().minus(properties.getJoinOpensBefore());
    }

    public LocalDateTime closesAt(Appointment appointment) {
        return appointment.getEndDateTime().plus(properties.getJoinClosesAfter());
    }

    /** Encerramento forçado da sala (limite de horário); nunca antes de {@link #closesAt}. */
    public LocalDateTime hardStopAt(Appointment appointment) {
        LocalDateTime hardStop = appointment.getEndDateTime().plus(properties.getHardStopAfterEnd());
        LocalDateTime closes = closesAt(appointment);
        return hardStop.isAfter(closes) ? hardStop : closes;
    }

    public boolean isOpen(Appointment appointment, LocalDateTime now) {
        return !now.isBefore(opensAt(appointment)) && !now.isAfter(closesAt(appointment));
    }
}
