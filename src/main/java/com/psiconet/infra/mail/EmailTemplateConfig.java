package com.psiconet.infra.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;

/**
 * Motores Thymeleaf dedicados a e-mail (HTML e texto simples). Usamos só o core do Thymeleaf,
 * sem o starter, para não registrar view resolvers MVC numa API REST.
 */
@Configuration
public class EmailTemplateConfig {

    @Bean
    public TemplateEngine emailHtmlTemplateEngine() {
        return engine("templates/email/", ".html", TemplateMode.HTML);
    }

    @Bean
    public TemplateEngine emailTextTemplateEngine() {
        return engine("templates/email/text/", ".txt", TemplateMode.TEXT);
    }

    private TemplateEngine engine(String prefix, String suffix, TemplateMode mode) {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix(prefix);
        resolver.setSuffix(suffix);
        resolver.setTemplateMode(mode);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        resolver.setCheckExistence(true);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }
}
