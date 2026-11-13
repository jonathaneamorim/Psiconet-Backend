package com.psiconet.infra.richtext;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.MapperFeature;
import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.text.TextSanitizer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Valida e RECONSTRÓI o documento do editor (JSON no formato ProseMirror/TipTap) a partir de uma
 * whitelist — nunca aceita HTML do cliente. Nós/marcas desconhecidos → 400; atributos fora da lista
 * são descartados. O resultado é serializado de forma canônica (chaves ordenadas), base do hash
 * SHA-256 de integridade.
 *
 * <p>Permitido (barra de formatação enxuta): título (heading 1), subtítulo (heading 2), parágrafo,
 * negrito, itálico, sublinhado, lista ordenada, lista não ordenada, quebra de linha, alinhamento
 * (esquerda/centro/direita) em parágrafos e títulos e tamanho de fonte (lista fixa de valores —
 * nunca CSS livre).</p>
 */
@Component
public class RichTextSanitizer {

    public static final int MAX_JSON_CHARS = 200_000;
    public static final int MAX_TEXT_CHARS = 50_000;
    private static final int MAX_DEPTH = 12;

    private static final Set<String> BLOCK_CONTAINERS = Set.of("doc", "bulletList", "orderedList", "listItem");
    private static final Set<String> NODES = Set.of(
            "doc", "paragraph", "heading", "text", "bulletList", "orderedList", "listItem", "hardBreak");
    private static final Set<String> MARKS = Set.of("bold", "italic", "underline", "textStyle");

    /** Alinhamentos aceitos. "left" é o padrão e não é armazenado (mantém o JSON canônico enxuto). */
    public static final Set<String> TEXT_ALIGNS = Set.of("center", "right");

    /** Tamanhos de fonte aceitos (mesma lista do seletor do editor). Ausente = tamanho padrão. */
    public static final java.util.List<String> FONT_SIZES = java.util.List.of("12px", "14px", "18px", "20px", "24px");
    private static final Map<String, Set<String>> ALLOWED_CHILDREN = Map.of(
            "doc", Set.of("paragraph", "heading", "bulletList", "orderedList"),
            "paragraph", Set.of("text", "hardBreak"),
            "heading", Set.of("text", "hardBreak"),
            "bulletList", Set.of("listItem"),
            "orderedList", Set.of("listItem"),
            "listItem", Set.of("paragraph", "bulletList", "orderedList")
    );

    private static final ObjectMapper CANONICAL = JsonMapper.builder()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .build();
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    public SanitizedDocument sanitize(JsonNode input) {
        if (input == null || !input.isObject() || !"doc".equals(input.path("type").asString())) {
            throw invalid("O conteúdo da anotação é inválido.");
        }
        if (input.toString().length() > MAX_JSON_CHARS) {
            throw invalid("A anotação excede o tamanho máximo permitido.");
        }

        StringBuilder plain = new StringBuilder();
        ObjectNode clean = sanitizeNode(input, "root", 0, plain);
        if (plain.length() > MAX_TEXT_CHARS) {
            throw invalid("A anotação excede " + MAX_TEXT_CHARS + " caracteres.");
        }
        return new SanitizedDocument(clean, canonical(clean), plain.toString().strip());
    }

    /** Serialização canônica (chaves ordenadas, sem espaços) — usada no hash de integridade. */
    public static String canonical(JsonNode node) {
        try {
            return CANONICAL.writeValueAsString(CANONICAL.treeToValue(node, Object.class));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar documento.", e);
        }
    }

    public JsonNode parse(String json) {
        try {
            return CANONICAL.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Documento armazenado inválido.");
        }
    }

    private ObjectNode sanitizeNode(JsonNode node, String parentType, int depth, StringBuilder plain) {
        if (depth > MAX_DEPTH) throw invalid("A anotação tem aninhamento excessivo de listas.");
        String type = node.path("type").asString("");
        if (!NODES.contains(type)) throw invalid("Formatação não permitida: " + safe(type));
        if (!"root".equals(parentType) && !ALLOWED_CHILDREN.getOrDefault(parentType, Set.of()).contains(type)) {
            throw invalid("Estrutura de formatação inválida.");
        }

        ObjectNode out = F.objectNode();
        out.put("type", type);

        switch (type) {
            case "text" -> {
                // Sem strip: no editor, espaços nas bordas de um trecho formatado são significativos
                String raw = TextSanitizer.stripInvisible(node.path("text").asString(""));
                if (raw.isEmpty()) throw invalid("Trecho de texto vazio.");
                out.put("text", raw);
                plain.append(raw);
                ArrayNode marks = sanitizeMarks(node.path("marks"));
                if (!marks.isEmpty()) out.set("marks", marks);
                return out;
            }
            case "hardBreak" -> {
                plain.append('\n');
                return out;
            }
            case "heading" -> {
                int level = node.path("attrs").path("level").asInt(1);
                if (level != 1 && level != 2) throw invalid("Use apenas título ou subtítulo.");
                ObjectNode attrs = F.objectNode().put("level", level);
                putAlign(attrs, node);
                out.set("attrs", attrs);
            }
            case "paragraph" -> {
                ObjectNode attrs = F.objectNode();
                putAlign(attrs, node);
                if (!attrs.isEmpty()) out.set("attrs", attrs);
            }
            case "orderedList" -> {
                int start = node.path("attrs").path("start").asInt(1);
                if (start < 1 || start > 10_000) start = 1;
                out.set("attrs", F.objectNode().put("start", start));
            }
            default -> { /* sem atributos */ }
        }

        JsonNode content = node.path("content");
        if (!content.isMissingNode() && !content.isNull()) {
            if (!content.isArray()) throw invalid("Estrutura de formatação inválida.");
            ArrayNode children = F.arrayNode();
            for (JsonNode child : content) {
                children.add(sanitizeNode(child, type, depth + 1, plain));
            }
            if (!children.isEmpty()) out.set("content", children);
        }
        if (BLOCK_CONTAINERS.contains(type) && !"doc".equals(type) && !out.has("content")) {
            throw invalid("Lista vazia na anotação.");
        }
        if ("paragraph".equals(type) || "heading".equals(type)) plain.append('\n');
        return out;
    }

    private ArrayNode sanitizeMarks(JsonNode marks) {
        ArrayNode out = F.arrayNode();
        if (marks.isMissingNode() || marks.isNull()) return out;
        if (!marks.isArray()) throw invalid("Formatação inválida.");
        // Ordenadas por tipo: a mesma formatação sempre gera o mesmo JSON canônico (hash estável)
        java.util.TreeMap<String, ObjectNode> seen = new java.util.TreeMap<>();
        for (JsonNode mark : marks) {
            String type = mark.path("type").asString("");
            if (!MARKS.contains(type)) throw invalid("Formatação não permitida: " + safe(type));
            if ("textStyle".equals(type)) {
                // Só o tamanho de fonte da lista fixa; qualquer outro estilo (cor, fonte, valor livre) é descartado
                String size = mark.path("attrs").path("fontSize").asString("");
                if (FONT_SIZES.contains(size)) {
                    seen.put(type, F.objectNode().put("type", type).set("attrs", F.objectNode().put("fontSize", size)));
                }
                continue;
            }
            seen.put(type, F.objectNode().put("type", type));
        }
        seen.values().forEach(out::add);
        return out;
    }

    private static void putAlign(ObjectNode attrs, JsonNode node) {
        String align = node.path("attrs").path("textAlign").asString("");
        if (TEXT_ALIGNS.contains(align)) attrs.put("textAlign", align);
    }

    private static String safe(String type) {
        return type.length() > 30 ? type.substring(0, 30) : type.replaceAll("[^A-Za-z0-9_-]", "");
    }

    private static BusinessException invalid(String message) {
        return new BusinessException("content", message);
    }

    /** Documento limpo, sua forma canônica (armazenada/hasheada) e o texto puro (busca/PDF/contagem). */
    public record SanitizedDocument(ObjectNode document, String canonicalJson, String plainText) {
        public boolean isBlank() {
            return plainText.isBlank();
        }
    }
}
