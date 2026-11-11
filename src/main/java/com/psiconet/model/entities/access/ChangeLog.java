package com.psiconet.model.entities.access;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;

/**
 * Trilha de auditoria de ações administrativas e de segurança (quem, o quê, quando, alvo, IP).
 * {@code @Immutable}: o Hibernate nunca emite UPDATE para esta entidade; não há endpoint de
 * edição ou exclusão. Nunca grave senha, token, CPF completo ou conteúdo clínico em {@code details}.
 */
@Getter
@Setter
@Entity
@Immutable
@Table(name = "log_alteracao", indexes = {
        @Index(name = "idx_log_alteracao_data", columnList = "data_hora"),
        @Index(name = "idx_log_alteracao_alvo", columnList = "alvo_id")
})
public class ChangeLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Autor da ação (null para ações do sistema, ex.: inicialização)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private User user;

    // Tipo do alvo (ex.: USER, TICKET, ANNOUNCEMENT). Coluna legada "tabela".
    @Column(name = "tabela")
    private String tableName;

    // Ação realizada (ex.: USER_INACTIVATED). Nome de coluna legado mantido para não migrar dados.
    @Column(name = "acao_reealizada")
    private String actionPerformed;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dateTime;

    @Column(name = "alvo_id", length = 64)
    private String targetId;

    @Column(name = "ip", length = 45)
    private String ipAddress;

    @Column(name = "detalhes", length = 1000)
    private String details;
}
