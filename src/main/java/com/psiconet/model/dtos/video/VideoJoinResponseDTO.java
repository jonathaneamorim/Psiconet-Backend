package com.psiconet.model.dtos.video;

import com.psiconet.model.dtos.clinical.AppointmentDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Credencial de entrada na sala integrada. O token é de vida curta e deve ficar SÓ em memória no
 * navegador. Não traz id da sala nem dado clínico.
 */
@Getter
@Builder
@AllArgsConstructor
public class VideoJoinResponseDTO {
    private final UUID appointmentId;
    private final String provider;
    private final String serverUrl;
    private final String token;
    private final Instant tokenExpiresAt;
    /** Identidade deste participante na sala (id do usuário). */
    private final String identity;
    /** HOST = psicólogo (anfitrião); GUEST = paciente (aguarda o psicólogo). */
    private final String role;
    /** Identidade e dados de exibição da outra parte (o nome não é enviado ao provedor). */
    private final String remoteIdentity;
    private final AppointmentDTO.PersonSummaryDTO remoteParticipant;
    private final LocalDateTime startDateTime;
    private final LocalDateTime endDateTime;
    private final LocalDateTime joinClosesAt;
    /** Quem fica sozinho na sala por este tempo é desconectado (o front avisa antes). */
    private final long aloneTimeoutSeconds;
}
