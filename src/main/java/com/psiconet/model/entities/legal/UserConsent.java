package com.psiconet.model.entities.legal;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.legal.ConsentSourceEnum;
import com.psiconet.model.enums.legal.ConsentTypeEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Prova do aceite de um documento legal (ADR 013). Só inserção: {@code @Immutable} no Hibernate e
 * trigger no PostgreSQL bloqueando UPDATE/DELETE ({@code SchemaPatches}). Data/hora, IP e navegador
 * são sempre obtidos pelo servidor — nunca do corpo da requisição.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "consentimento_usuario", indexes = {
        @Index(name = "idx_consentimento_usuario", columnList = "usuario_id, aceito_em")
})
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 20, updatable = false)
    private ConsentTypeEnum type;

    @Column(name = "versao", nullable = false, length = 20, updatable = false)
    private String version;

    @Column(name = "aceito_em", nullable = false, updatable = false)
    private Instant acceptedAt;

    @Column(name = "ip", length = 45, updatable = false)
    private String ipAddress;

    // Navegador/sistema resumidos (ex.: "Chrome 141 · Windows"), nunca o cabeçalho completo.
    @Column(name = "navegador", length = 120, updatable = false)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem", nullable = false, length = 30, updatable = false)
    private ConsentSourceEnum source;
}
