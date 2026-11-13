package com.psiconet.model;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** SEC-10: toString de entidade nunca carrega dado pessoal (vai parar em log e mensagem de exceção). */
class EntityToStringTest {

    @Test
    void entidadesNaoExpoemDadosPessoaisNoToString() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("maria@exemplo.com");
        user.setCpf("12345678909");
        user.setPhone("11999998888");
        user.setPassword("$2a$10$hashdasenha");
        Location location = new Location();
        location.setStreet("Rua Secreta, 123");
        user.setLocation(location);

        Psychologist psychologist = new Psychologist();
        psychologist.setId(UUID.randomUUID());
        psychologist.setUser(user);
        psychologist.setPixKey("chave-pix-12345678909");
        psychologist.setOfficeAddress(location);

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user);

        for (String text : new String[]{user.toString(), psychologist.toString(), patient.toString()}) {
            assertThat(text).doesNotContain("maria@exemplo.com", "12345678909", "11999998888", "hashdasenha", "Rua Secreta");
        }
    }
}
