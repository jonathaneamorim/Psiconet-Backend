package com.psiconet.infra.legal;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;
import java.util.Optional;

/** Documentos legais publicados. O slug é o da URL pública no frontend (/terms, /privacy, /cookies). */
@Getter
@RequiredArgsConstructor
public enum LegalDocumentType {
    TERMS("terms", "termos-de-uso.md"),
    PRIVACY("privacy", "politica-de-privacidade.md"),
    COOKIES("cookies", "politica-de-cookies.md");

    private final String slug;
    private final String fileName;

    public static Optional<LegalDocumentType> fromSlug(String slug) {
        return Arrays.stream(values()).filter(t -> t.slug.equalsIgnoreCase(slug)).findFirst();
    }
}
