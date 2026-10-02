package com.psiconet.model.dtos.legal;

import java.time.LocalDate;

/** Documento legal para as páginas públicas (/terms, /privacy, /cookies). {@code content} em Markdown. */
public record LegalDocumentDTO(String type, String slug, String title, String version, LocalDate effectiveDate, String content) {}
