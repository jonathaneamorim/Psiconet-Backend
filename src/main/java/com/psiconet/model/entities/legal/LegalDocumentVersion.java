package com.psiconet.model.entities.legal;

import com.psiconet.infra.legal.LegalDocumentType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Versões de documentos legais já publicadas por esta instalação. Serve para detectar, na
 * inicialização, que uma versão NOVA entrou no ar e avisar os usuários uma única vez (ADR 013).
 */
@Getter
@Setter
@Entity
@Table(name = "versao_documento_legal",
        uniqueConstraints = @UniqueConstraint(name = "uk_versao_documento_legal", columnNames = {"tipo_documento", "versao"}))
public class LegalDocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", nullable = false, length = 20)
    private LegalDocumentType type;

    @Column(name = "versao", nullable = false, length = 20)
    private String version;

    @Column(name = "vigencia", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "publicada_em", nullable = false)
    private Instant publishedAt;
}
