package com.psiconet.model.entities.clinical;

import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.VideoSessionEventEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Log de presença da sessão de vídeo (auditoria e detecção de comparecimento). Append-only e SEM
 * conteúdo: só consulta, participante, evento e horário — nunca mídia, token ou dado clínico.
 * Nesta entrega apenas registra; não altera status de consulta nem pagamentos (ADR 016).
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "log_sessao_video", indexes = {
        @Index(name = "idx_log_sessao_video_agendamento", columnList = "agendamento_id, ocorrido_em")
})
public class VideoSessionLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agendamento_id", nullable = false)
    private UUID appointmentId;

    // Nulo em eventos da sala (ex.: ROOM_FINISHED).
    @Column(name = "usuario_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel", length = 15)
    private RoleEnum role;

    @Enumerated(EnumType.STRING)
    @Column(name = "evento", nullable = false, length = 30)
    private VideoSessionEventEnum event;

    // Id do evento no provedor: torna idempotente a reentrega de webhooks.
    @Column(name = "evento_provedor_id", length = 100, unique = true)
    private String providerEventId;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "registrado_em", nullable = false)
    private LocalDateTime recordedAt;
}
