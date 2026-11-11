package com.psiconet.controllers.admin;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.support.IntegrationTestBase;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.UUID;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PATIENT e PSYCHOLOGIST recebem 403 em TODOS os endpoints administrativos. */
class AdminAuthorizationIntegrationTest extends IntegrationTestBase {

    static Stream<Arguments> adminEndpoints() {
        String id = UUID.randomUUID().toString();
        Stream<Object[]> endpoints = Stream.of(
                new Object[]{HttpMethod.GET, "/admin/users", null},
                new Object[]{HttpMethod.PATCH, "/admin/users/" + id + "/status", "{\"status\":\"INACTIVE\"}"},
                new Object[]{HttpMethod.PUT, "/admin/users/" + id, "{\"fullName\":\"Nome Teste\"}"},
                new Object[]{HttpMethod.POST, "/admin/admins", "{\"fullName\":\"X Y Z\",\"email\":\"a@b.com\",\"cpf\":\"52998224725\",\"temporaryPassword\":\"Senha@Forte1\"}"},
                new Object[]{HttpMethod.GET, "/admin/dashboard", null},
                new Object[]{HttpMethod.POST, "/admin/notifications", "{\"recipientId\":\"" + id + "\",\"title\":\"Oi\",\"message\":\"Olá\"}"},
                new Object[]{HttpMethod.GET, "/admin/tickets", null},
                new Object[]{HttpMethod.GET, "/admin/tickets/" + id, null},
                new Object[]{HttpMethod.POST, "/admin/tickets/" + id + "/messages", "{\"content\":\"oi\"}"},
                new Object[]{HttpMethod.PATCH, "/admin/tickets/" + id + "/status", "{\"status\":\"RESOLVED\"}"},
                new Object[]{HttpMethod.PATCH, "/admin/tickets/" + id + "/priority", "{\"priority\":\"HIGH\"}"}
        );
        return endpoints.flatMap(e -> Stream.of(
                Arguments.of(RoleEnum.PATIENT, e[0], e[1], e[2]),
                Arguments.of(RoleEnum.PSYCHOLOGIST, e[0], e[1], e[2])));
    }

    @ParameterizedTest(name = "{0} {1} {2} → 403")
    @MethodSource("adminEndpoints")
    void naoAdminRecebe403(RoleEnum role, HttpMethod method, String path, String body) throws Exception {
        User user = createUser(role, UserStatusEnum.ACTIVE);
        String token = tokenFor(user);

        var req = request(method, path).header("Authorization", bearer(token));
        if (body != null) req = req.contentType(MediaType.APPLICATION_JSON).content(body);

        mvc.perform(req).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "sem token {1} {2} → 401")
    @MethodSource("adminEndpoints")
    void anonimoRecebe401(RoleEnum ignored, HttpMethod method, String path, String body) throws Exception {
        var req = request(method, path);
        if (body != null) req = req.contentType(MediaType.APPLICATION_JSON).content(body);
        mvc.perform(req).andExpect(status().isUnauthorized());
    }
}
