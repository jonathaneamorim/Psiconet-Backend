package com.psiconet.model.entities.clinical;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.clinical.SessionNoteStatusEnum;
import com.psiconet.model.enums.clinical.SessionNoteTypeEnum;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Anotação de sessão (vinculada à consulta e ao prontuário). Conteúdo = JSON do editor, validado
 * por whitelist, cifrado. Ao FINALIZAR: data/hora do servidor, autor (nome + CRP congelados) e hash
 * SHA-256 de integridade; o registro passa a ser imutável (correções via {@link SessionNoteAddendum}).
 */
@Getter
@Setter
@Entity
@Table(name = "anotacoes_sessao", indexes = @Index(name = "idx_anotacao_prontuario", columnList = "prontuario_id, criado_em"))
public class SessionNote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Antes era @OneToOne; virou ManyToOne para permitir mais de um registro por consulta
    // (ex.: registro documental + anotação pessoal). Mesma coluna, sem migração de dados.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id")
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prontuario_id")
    private MedicalRecord medicalRecord;

    // ---- Legado (texto puro). Somente leitura para migração; manter nulo. ----
    @Column(name = "resumo")
    private String summary;

    @Column(name = "estado_mental")
    private String mentalState;

    @Column(name = "tarefas_casa")
    private String homework;

    // ---- Novo formato ----
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'RECORD'")
    @Column(name = "tipo", length = 10, nullable = false)
    private SessionNoteTypeEnum type = SessionNoteTypeEnum.RECORD;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'DRAFT'")
    @Column(name = "situacao", length = 10, nullable = false)
    private SessionNoteStatusEnum status = SessionNoteStatusEnum.DRAFT;

    // Título curto opcional (cifrado como o conteúdo)
    @Column(name = "titulo_cifrado", columnDefinition = "TEXT")
    private String titleEncrypted;

    @Column(name = "conteudo_cifrado", columnDefinition = "TEXT")
    private String contentEncrypted;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id")
    private User author;

    // Identificação do autor congelada na finalização (validade do registro)
    @Column(name = "autor_nome", length = 150)
    private String authorName;

    @Column(name = "autor_crp", length = 20)
    private String authorCrp;

    @Column(name = "hash_integridade", length = 64)
    private String integrityHash;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime updatedAt;

    @Column(name = "finalizado_em")
    private LocalDateTime finalizedAt;

    @Version
    private Long version;
}
