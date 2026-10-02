package com.psiconet.model.entities.clinical;

import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.clinical.ClinicalAuditActionEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trilha de auditoria IMUTÁVEL de todo acesso e alteração clínica (quem, quando, ação, IP).
 * Append-only: {@code @Immutable} no Hibernate + trigger no Postgres que recusa UPDATE/DELETE
 * (SchemaPatches) + encadeamento de hash (cada linha guarda o hash da anterior), o que torna
 * qualquer adulteração detectável. Nunca contém conteúdo clínico.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "auditoria_clinica", indexes = {
        @Index(name = "idx_auditoria_clinica_prontuario", columnList = "prontuario_id, ocorrido_em"),
        @Index(name = "idx_auditoria_clinica_seq", columnList = "sequencia")
})
public class ClinicalAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sequencia", nullable = false, unique = true)
    private long sequence;

    @Column(name = "prontuario_id")
    private UUID medicalRecordId;

    @Column(name = "ator_id")
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "papel_ator", length = 15)
    private RoleEnum actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "acao", nullable = false, length = 40)
    private ClinicalAuditActionEnum action;

    @Column(name = "alvo_id", length = 64)
    private String targetId;

    @Column(name = "ip", length = 45)
    private String ipAddress;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "hash_anterior", length = 64)
    private String previousHash;

    @Column(name = "hash", nullable = false, length = 64)
    private String hash;
}
