package com.psiconet.model.entities.support;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.support.TicketCategoryEnum;
import com.psiconet.model.enums.support.TicketPriorityEnum;
import com.psiconet.model.enums.support.TicketStatusEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Chamado de suporte aberto por paciente ou psicólogo e atendido por administradores.
 * Suporte NÃO é canal clínico: administradores nunca acessam prontuário por aqui.
 */
@Getter
@Setter
@Entity
@Table(name = "chamado", indexes = {
        @Index(name = "idx_chamado_solicitante", columnList = "solicitante_id, status"),
        @Index(name = "idx_chamado_status", columnList = "status, prioridade")
})
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Identificador legível exibido ao usuário e nos e-mails (ex.: CH-20261001-3F9A2C)
    @Column(name = "protocolo", nullable = false, unique = true, length = 24)
    private String protocol;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitante_id", nullable = false)
    private User requester;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria", nullable = false, length = 20)
    private TicketCategoryEnum category;

    @Column(name = "assunto", nullable = false, length = 120)
    private String subject;

    @Column(name = "descricao", nullable = false, length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, length = 10)
    private TicketPriorityEnum priority = TicketPriorityEnum.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    private TicketStatusEnum status = TicketStatusEnum.OPEN;

    // Hash (SHA-256) de assunto+descrição normalizados: barra reenvio do mesmo chamado
    @Column(name = "hash_conteudo", length = 64)
    private String contentHash;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime updatedAt;

    @Column(name = "ultima_mensagem_em")
    private LocalDateTime lastMessageAt;

    @Column(name = "resolvido_em")
    private LocalDateTime resolvedAt;

    @Column(name = "fechado_em")
    private LocalDateTime closedAt;

    @OneToMany(mappedBy = "ticket")
    @OrderBy("createdAt ASC")
    private List<TicketMessage> messages = new ArrayList<>();

    @Version
    private Long version;
}
