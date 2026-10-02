package com.psiconet.infra.mail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EmailRendererTest {

    private final EmailTemplateConfig config = new EmailTemplateConfig();
    private final EmailRenderer renderer = new EmailRenderer(
            config.emailHtmlTemplateEngine(), config.emailTextTemplateEngine());

    private static final Map<String, Object> SAMPLE = Map.of(
            "nome", "Maria",
            "link", "http://localhost:3000/login",
            "expiracaoMinutos", 30,
            "perfil", "Paciente",
            "convidadoPor", "Admin Base",
            "protocolo", "#000042",
            "titulo", "Manutenção programada",
            "mensagem", "Linha 1\nLinha 2",
            "liberado", true
    );

    @ParameterizedTest
    @EnumSource(EmailTemplate.class)
    void todosOsTemplatesRenderizamHtmlETextoComLayoutPadrao(EmailTemplate template) {
        EmailRenderer.RenderedEmail email = renderer.render(EmailMessage.of("maria@teste.com", template, SAMPLE));

        assertThat(email.subject()).isEqualTo(template.getDefaultSubject());
        assertThat(email.html())
                .contains("Psiconet")
                .contains("Este é um e-mail automático")
                .contains("Olá")
                .contains("Maria")
                .doesNotContainPattern("\\sth:[a-z]+=");
        assertThat(email.text())
                .contains("Maria")
                .contains("Este é um e-mail automático")
                .doesNotContain("[(")
                .doesNotContain("<");
    }

    @Test
    void conteudoDinamicoEhEscapadoNoHtml() {
        Map<String, Object> malicious = Map.of(
                "nome", "<script>alert(1)</script>",
                "titulo", "<img src=x onerror=alert(1)>",
                "mensagem", "<a href=\"javascript:alert(1)\">clique</a>",
                "link", "http://localhost:3000/notifications"
        );

        String html = renderer.render(EmailMessage.of("x@teste.com", EmailTemplate.ADMIN_ANNOUNCEMENT, malicious)).html();

        assertThat(html)
                .doesNotContain("<script>")
                .doesNotContain("<img src=x")
                .doesNotContain("<a href=\"javascript:")
                .contains("&lt;script&gt;")
                .contains("&lt;img src=x onerror=alert(1)&gt;");
    }

    @Test
    void mascaraEmailNosLogs() {
        assertThat(EmailDispatcher.maskEmail("maria.silva@teste.com")).isEqualTo("m***@teste.com");
        assertThat(EmailDispatcher.maskEmail(null)).isEqualTo("***");
    }
}
