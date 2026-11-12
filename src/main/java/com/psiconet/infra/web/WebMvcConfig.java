package com.psiconet.infra.web;

import com.psiconet.infra.ratelimit.RateLimitInterceptor;
import com.psiconet.infra.ratelimit.RateLimitProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(RateLimitProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;
    private final PageableSortGuard pageableSortGuard;

    // Teto de itens por página (SEC-08): ?size= maior é reduzido a este valor.
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer pageableLimits(
            @Value("${psiconet.pagination.max-page-size:100}") int maxPageSize) {
        return resolver -> resolver.setMaxPageSize(maxPageSize);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor);
        registry.addInterceptor(pageableSortGuard);
    }
}
