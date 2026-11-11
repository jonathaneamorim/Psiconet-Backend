package com.psiconet.infra.legal;

import java.time.LocalDate;

/** Documento legal carregado de {@code classpath:legal/} (fonte: {@code docs/legal/}). */
public record LegalDocument(
        LegalDocumentType type,
        String title,
        String version,
        LocalDate effectiveDate,
        String content
) {}
