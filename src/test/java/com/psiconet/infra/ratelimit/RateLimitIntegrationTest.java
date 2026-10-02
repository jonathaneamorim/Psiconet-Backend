package com.psiconet.infra.ratelimit;

import com.psiconet.support.IntegrationTestBase;
import com.psiconet.support.TestData;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "psiconet.rate-limit.enabled=true",
        "psiconet.rate-limit.policies.login.capacity=3",
        "psiconet.rate-limit.policies.login.period=1h",
        "psiconet.rate-limit.policies.login-account.capacity=100",
        "psiconet.rate-limit.policies.forgot-password.capacity=2",
        "psiconet.rate-limit.policies.forgot-password.period=1h"
})
class RateLimitIntegrationTest extends IntegrationTestBase {

    private MockHttpServletRequestBuilder loginAttempt(String remoteAddr, String forwardedFor) throws Exception {
        MockHttpServletRequestBuilder builder = post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", TestData.uniqueEmail("rl"), "password", "Errada@123")))
                .with(request -> {
                    request.setRemoteAddr(remoteAddr);
                    return request;
                });
        if (forwardedFor != null) builder.header("X-Forwarded-For", forwardedFor);
        return builder;
    }

    @Test
    void excederLimiteDeLoginRetorna429ComRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(loginAttempt("127.0.0.1", "203.0.113.10")).andExpect(status().isUnauthorized());
        }
        mvc.perform(loginAttempt("127.0.0.1", "203.0.113.10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").exists());

        // Outro IP (repassado pelo proxy confiável) tem o próprio limite
        mvc.perform(loginAttempt("127.0.0.1", "203.0.113.11")).andExpect(status().isUnauthorized());
    }

    @Test
    void xForwardedForDeOrigemNaoConfiavelEhIgnorado() throws Exception {
        // Cliente direto (não é o proxy do front) tentando trocar o IP a cada requisição
        for (int i = 0; i < 3; i++) {
            mvc.perform(loginAttempt("198.51.100.7", "10.0.0." + i)).andExpect(status().isUnauthorized());
        }
        mvc.perform(loginAttempt("198.51.100.7", "10.0.0.99")).andExpect(status().isTooManyRequests());
    }

    @Test
    void esqueciASenhaTemLimiteProprio() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("email", TestData.uniqueEmail("fp"))))
                            .header("X-Forwarded-For", "203.0.113.50"))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", TestData.uniqueEmail("fp"))))
                        .header("X-Forwarded-For", "203.0.113.50"))
                .andExpect(status().isTooManyRequests());
    }
}
