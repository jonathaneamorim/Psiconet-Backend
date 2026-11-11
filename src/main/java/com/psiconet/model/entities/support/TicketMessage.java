package com.psiconet.model.entities.support;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.UUID;

/** Mensagem da conversa usuário ↔ administração. Imutável depois de enviada. */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "chamado_mensagem", indexes = @Index(name = "idx_chamado_mensagem_chamado", columnList = "chamado_id, criado_em"))
public class TicketMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chamado_id", nullable = false)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private User author;

    // Papel do autor no momento do envio (a conversa continua legível mesmo se o papel mudar)
    @Enumerated(EnumType.STRING)
    @Column(name = "papel_autor", nullable = false, length = 15)
    private RoleEnum authorRole;

    @Column(name = "conteudo", nullable = false, length = 4000)
    private String content;

    @Column(name = "hash_conteudo", nullable = false, length = 64)
    private String contentHash;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
