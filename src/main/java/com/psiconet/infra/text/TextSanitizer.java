package com.psiconet.infra.text;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sanitização de texto livre digitado por usuários (chamados, comunicados, justificativas).
 * O conteúdo é armazenado e trafegado como TEXTO PURO — a proteção contra XSS é o escape na
 * renderização (React/Thymeleaf). Aqui removemos o que não tem uso legítimo e atrapalha
 * moderação/deduplicação: caracteres de controle, invisíveis e excesso de quebras de linha.
 */
public final class TextSanitizer {

    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    private static final Pattern INVISIBLE = Pattern.compile("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]");
    private static final Pattern MANY_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Pattern SPACES = Pattern.compile("[ \\t]+");

    private TextSanitizer() {}

    public static String clean(String input) {
        if (input == null) return null;
        String text = Normalizer.normalize(input, Normalizer.Form.NFC)
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        text = INVISIBLE.matcher(text).replaceAll("");
        text = CONTROL.matcher(text).replaceAll("");
        text = MANY_BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.strip();
    }

    /** Linha única (títulos/assuntos): sem quebras e com espaços colapsados. */
    public static String cleanLine(String input) {
        String text = clean(input);
        return text == null ? null : SPACES.matcher(text.replace('\n', ' ')).replaceAll(" ").strip();
    }

    /** Hash SHA-256 do texto normalizado (minúsculas, espaços colapsados) — para detectar duplicatas. */
    public static String fingerprint(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(part == null ? "" : SPACES.matcher(part.toLowerCase(Locale.ROOT).replace('\n', ' ')).replaceAll(" ").strip());
            sb.append('\u0000');
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível na JVM.", e);
        }
    }
}
