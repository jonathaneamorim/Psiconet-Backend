package com.psiconet.model.entities.access;

import com.psiconet.model.enums.access.SessionRevocationReasonEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Sessão de login de um dispositivo (ADR 014). O refresh token entregue ao cliente tem o formato
 * {@code <id da sessão>.<segredo aleatório>}; só o SHA-256 do segredo é guardado. A cada renovação o
 * segredo é trocado (rotação); reapresentar um segredo antigo fora da janela de tolerância revoga a
 * sessão (detecção de reutilização). O token de acesso (JWT) carrega o id da sessão no claim
 * {@code sid}, e o filtro recusa tokens de sessões revogadas — logout derruba o acesso na hora.
 */
@Getter
@Setter
@Entity
@Table(name = "sessao_usuario", indexes = @Index(name = "idx_sessao_usuario", columnList = "usuario_id, revogada_em"))
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private User user;

    // "Manter conectado neste dispositivo"
    @Column(name = "manter_conectado", nullable = false, updatable = false)
    private boolean rememberMe;

    // usuario.versao_token no momento do login: logout geral, troca/redefinição de senha, inativação e
    // troca de e-mail incrementam a versão do usuário e matam todas as sessões anteriores.
    @Column(name = "versao_token", nullable = false, updatable = false)
    private int tokenVersion;

    @Column(name = "hash_refresh_atual", nullable = false, length = 64)
    private String currentTokenHash;

    @Column(name = "hash_refresh_anterior", length = 64)
    private String previousTokenHash;

    @Column(name = "rotacionada_em")
    private Instant rotatedAt;

    @Column(name = "criada_em", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ultimo_uso_em", nullable = false)
    private Instant lastUsedAt;

    // Expira se ficar este tempo sem renovar (inatividade); renovação empurra até o limite absoluto.
    @Column(name = "expira_inatividade_em", nullable = false)
    private Instant idleExpiresAt;

    @Column(name = "expira_absoluta_em", nullable = false, updatable = false)
    private Instant absoluteExpiresAt;

    @Column(name = "revogada_em")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_revogacao", length = 30)
    private SessionRevocationReasonEnum revokedReason;

    @Column(name = "ip", length = 45)
    private String ipAddress;

    @Column(name = "navegador", length = 120)
    private String userAgent;

    public boolean isUsable(Instant now) {
        return revokedAt == null && now.isBefore(idleExpiresAt) && now.isBefore(absoluteExpiresAt);
    }
}
