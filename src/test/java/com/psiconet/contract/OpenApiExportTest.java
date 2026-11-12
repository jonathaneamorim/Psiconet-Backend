package com.psiconet.contract;

import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Exporta o OpenAPI para target/contract/openapi.json (comparação antes × depois de trocas de versão,
 * ADR 018). Também garante que a documentação continua gerando quando ligada (API_DOCS_ENABLED=true).
 */
@TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class OpenApiExportTest extends IntegrationTestBase {

    @Test
    void documentacaoGeraEExporta() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")).andReturn().getResponse();
        assertEquals(200, response.getStatus());
        Path out = Path.of("target/contract/openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, response.getContentAsString(StandardCharsets.UTF_8), StandardCharsets.UTF_8);

        assertEquals(200, mvc.perform(get("/swagger-ui/index.html")).andReturn().getResponse().getStatus());
    }
}
