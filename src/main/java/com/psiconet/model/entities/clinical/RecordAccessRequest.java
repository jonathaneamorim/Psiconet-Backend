package com.psiconet.model.entities.clinical;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.clinical.RecordAccessStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Solicitação do paciente para acessar o próprio prontuário (direito do titular — LGPD art. 18).
 * O psicólogo libera (somente leitura, opcionalmente com prazo e exportação em PDF) ou nega com
 * justificativa registrada (cifrada: pode conter informação clínica).
 */
@Getter
@Setter
@Entity
@Table(name = "solicitacao_acesso_prontuario", indexes = {
        @Index(name = "idx_solicitacao_prontuario", columnList = "prontuario_id, situacao"),
        @Index(name = "idx_solicitacao_paciente", columnList = "paciente_usuario_id")
})
public class RecordAccessRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prontuario_id", nullable = false)
    private MedicalRecord medicalRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "paciente_usuario_id", nullable = false)
    private User patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "psicologo_usuario_id", nullable = false)
    private User psychologist;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao", nullable = false, length = 10)
    private RecordAccessStatusEnum status = RecordAccessStatusEnum.PENDING;

    @CreationTimestamp
    @Column(name = "solicitado_em", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "decidido_em")
    private LocalDateTime decidedAt;

    // Liberação: validade opcional e permissão de exportar em PDF
    @Column(name = "expira_em")
    private LocalDateTime expiresAt;

    @Column(name = "permite_exportacao", nullable = false)
    private boolean exportAllowed;

    @Column(name = "justificativa_cifrada", columnDefinition = "TEXT")
    private String justificationEncrypted;

    @Version
    private Long version;

    public boolean isActiveGrant(LocalDateTime now) {
        return status == RecordAccessStatusEnum.GRANTED && (expiresAt == null || expiresAt.isAfter(now));
    }
}
