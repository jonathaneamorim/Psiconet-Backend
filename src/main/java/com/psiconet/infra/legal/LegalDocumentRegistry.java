package com.psiconet.infra.legal;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fonte única das versões dos documentos legais (ADR 011). Cada Markdown em {@code docs/legal/}
 * começa com um cabeçalho:
 * <pre>
 * ---
 * tipo: TERMS
 * titulo: Termos de Uso
 * versao: 1.0.0
 * vigencia: 2026-10-02
 * ---
 * </pre>
 * Publicar nova versão = editar o arquivo e subir {@code versao}/{@code vigencia}. A aplicação não
 * sobe se algum documento estiver ausente ou com cabeçalho inválido.
 */
@Component
public class LegalDocumentRegistry {

    private static final Pattern SEMVER = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    private final Map<LegalDocumentType, LegalDocument> documents = new EnumMap<>(LegalDocumentType.class);

    @PostConstruct
    void load() {
        for (LegalDocumentType type : LegalDocumentType.values()) {
            documents.put(type, read(type));
        }
    }

    public LegalDocument get(LegalDocumentType type) {
        return documents.get(type);
    }

    public String currentVersion(LegalDocumentType type) {
        return documents.get(type).version();
    }

    private LegalDocument read(LegalDocumentType type) {
        ClassPathResource resource = new ClassPathResource("legal/" + type.getFileName());
        String raw;
        try (InputStream in = resource.getInputStream()) {
            raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Documento legal ausente no classpath: legal/" + type.getFileName(), e);
        }
        return parse(type, raw);
    }

    static LegalDocument parse(LegalDocumentType type, String raw) {
        String text = raw.replace("\r\n", "\n");
        if (!text.startsWith("---\n")) {
            throw new IllegalStateException("Documento legal sem cabeçalho: " + type.getFileName());
        }
        int end = text.indexOf("\n---", 4);
        if (end < 0) {
            throw new IllegalStateException("Cabeçalho do documento legal não fechado: " + type.getFileName());
        }

        Map<String, String> meta = new HashMap<>();
        for (String line : text.substring(4, end).split("\n")) {
            int colon = line.indexOf(':');
            if (colon > 0) meta.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
        }

        if (!type.name().equals(meta.get("tipo"))) {
            throw new IllegalStateException("Tipo divergente em " + type.getFileName() + ": " + meta.get("tipo"));
        }
        String version = meta.get("versao");
        if (version == null || !SEMVER.matcher(version).matches()) {
            throw new IllegalStateException("Versão inválida em " + type.getFileName() + ": " + version);
        }
        LocalDate effectiveDate = LocalDate.parse(meta.getOrDefault("vigencia", ""));
        String title = meta.getOrDefault("titulo", type.name());

        String content = text.substring(text.indexOf('\n', end + 1) + 1).strip();
        return new LegalDocument(type, title, version, effectiveDate, content);
    }
}
