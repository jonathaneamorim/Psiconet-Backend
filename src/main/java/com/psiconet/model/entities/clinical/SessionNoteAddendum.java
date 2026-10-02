package com.psiconet.model.entities.clinical;

import com.psiconet.model.entities.access.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Adendo/retificação de uma anotação FINALIZADA: datado e assinado (nome + CRP + hash), numerado em
 * sequência. Nunca altera o original — o histórico completo é preservado. Imutável.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "anotacao_adendo", indexes = @Index(name = "idx_adendo_anotacao", columnList = "anotacao_id, sequencia"))
public class SessionNoteAddendum {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anotacao_id", nullable = false)
    private SessionNote note;

    @Column(name = "sequencia", nullable = false)
    private int sequence;

    @Column(name = "motivo_cifrado", columnDefinition = "TEXT", nullable = false)
    private String reasonEncrypted;

    @Column(name = "conteudo_cifrado", columnDefinition = "TEXT", nullable = false)
    private String contentEncrypted;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private User author;

    @Column(name = "autor_nome", length = 150, nullable = false)
    private String authorName;

    @Column(name = "autor_crp", length = 20)
    private String authorCrp;

    // Encadeado ao hash do registro anterior (original ou adendo n-1)
    @Column(name = "hash_integridade", length = 64, nullable = false)
    private String integrityHash;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
