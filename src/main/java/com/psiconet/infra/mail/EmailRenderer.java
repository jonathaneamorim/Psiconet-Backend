package com.psiconet.infra.mail;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Year;
import java.util.Locale;

/** Renderiza um {@link EmailMessage} nas versões HTML e texto simples. */
@Component
public class EmailRenderer {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final TemplateEngine htmlEngine;
    private final TemplateEngine textEngine;

    public EmailRenderer(
            @Qualifier("emailHtmlTemplateEngine") TemplateEngine htmlEngine,
            @Qualifier("emailTextTemplateEngine") TemplateEngine textEngine
    ) {
        this.htmlEngine = htmlEngine;
        this.textEngine = textEngine;
    }

    public RenderedEmail render(EmailMessage message) {
        Context context = new Context(PT_BR);
        context.setVariables(message.variables());
        context.setVariable("assunto", message.subject());
        context.setVariable("anoAtual", Year.now().getValue());

        String name = message.template().getTemplateName();
        return new RenderedEmail(
                message.subject(),
                htmlEngine.process(name, context),
                textEngine.process(name, context)
        );
    }

    public record RenderedEmail(String subject, String html, String text) {}
}
