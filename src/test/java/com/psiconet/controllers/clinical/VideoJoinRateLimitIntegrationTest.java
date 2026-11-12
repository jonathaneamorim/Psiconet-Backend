package com.psiconet.controllers.clinical;

import com.psiconet.infra.video.VideoProvider;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Spam de pedidos de credencial de sala é barrado pelo rate limit (política video-join). */
@TestPropertySource(properties = {
        "psiconet.rate-limit.enabled=true",
        "psiconet.rate-limit.policies.video-join.capacity=3",
        "psiconet.rate-limit.policies.video-join.period=1h"
})
class VideoJoinRateLimitIntegrationTest extends IntegrationTestBase {

    @MockitoBean VideoProvider videoProvider;

    @Test
    void excederLimiteDeEntradaRetorna429() throws Exception {
        User user = createUser(RoleEnum.PATIENT, UserStatusEnum.ACTIVE);
        String token = tokenFor(user);
        String path = "/appointments/" + UUID.randomUUID() + "/video/join";

        for (int i = 0; i < 3; i++) {
            mvc.perform(post(path).header("Authorization", bearer(token))).andExpect(status().isNotFound());
        }
        mvc.perform(post(path).header("Authorization", bearer(token)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
