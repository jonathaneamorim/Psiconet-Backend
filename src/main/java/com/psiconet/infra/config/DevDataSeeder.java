package com.psiconet.infra.config;

import com.psiconet.infra.text.TextSanitizer;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.support.Ticket;
import com.psiconet.model.entities.support.TicketMessage;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.support.TicketCategoryEnum;
import com.psiconet.model.enums.support.TicketPriorityEnum;
import com.psiconet.model.enums.support.TicketStatusEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.support.TicketMessageRepository;
import com.psiconet.repositories.support.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Dados de EXEMPLO de chamados para visualizar as telas em desenvolvimento.
 * Só existe no perfil "dev" (SPRING_PROFILES_ACTIVE=dev) — nunca é carregado em produção.
 * Idempotente: não faz nada se já houver qualquer chamado. Não cria usuários: usa pacientes e
 * psicólogos já cadastrados no banco local.
 */
@Slf4j
@Component
@Profile("dev")
@Order(10)
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (ticketRepository.count() > 0) return;

        List<User> requesters = userRepository.findAll().stream()
                .filter(u -> u.getRole() == RoleEnum.PATIENT || u.getRole() == RoleEnum.PSYCHOLOGIST)
                .limit(3)
                .toList();
        User admin = userRepository.findFirstByBaseAdminTrue().orElse(null);
        if (requesters.isEmpty()) {
            log.info("[dev] Sem pacientes/psicólogos cadastrados: chamados de exemplo não criados.");
            return;
        }

        Object[][] samples = {
                {TicketCategoryEnum.ACCOUNT, "Não consigo alterar minha foto de perfil",
                        "Ao enviar uma imagem PNG aparece uma mensagem de erro e a foto não é atualizada.",
                        TicketPriorityEnum.MEDIUM, TicketStatusEnum.OPEN, null},
                {TicketCategoryEnum.PAYMENT, "Comprovante enviado aparece como pendente",
                        "Enviei o comprovante do Pix ontem e a cobrança continua pendente na minha tela.",
                        TicketPriorityEnum.HIGH, TicketStatusEnum.IN_PROGRESS,
                        "Olá! Já estamos verificando o comprovante. Retornaremos em breve."},
                {TicketCategoryEnum.APPOINTMENT, "Dúvida sobre cancelamento de consulta",
                        "Gostaria de entender até quando posso cancelar uma consulta sem custo.",
                        TicketPriorityEnum.LOW, TicketStatusEnum.RESOLVED,
                        "O prazo de cancelamento é definido pelo seu psicólogo. Você encontra a regra no detalhe da consulta."},
                {TicketCategoryEnum.TECHNICAL, "Página de agenda demora para carregar",
                        "A agenda leva mais de 10 segundos para abrir no celular.",
                        TicketPriorityEnum.URGENT, TicketStatusEnum.OPEN, null},
        };

        int i = 0;
        for (Object[] sample : samples) {
            User requester = requesters.get(i++ % requesters.size());
            Ticket t = new Ticket();
            t.setProtocol("CH-DEV-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
            t.setRequester(requester);
            t.setCategory((TicketCategoryEnum) sample[0]);
            t.setSubject((String) sample[1]);
            t.setDescription((String) sample[2]);
            t.setPriority((TicketPriorityEnum) sample[3]);
            t.setStatus((TicketStatusEnum) sample[4]);
            t.setContentHash(TextSanitizer.fingerprint((String) sample[1], (String) sample[2]));
            t.setLastMessageAt(LocalDateTime.now());
            if (t.getStatus() == TicketStatusEnum.RESOLVED) t.setResolvedAt(LocalDateTime.now());
            ticketRepository.save(t);

            if (sample[5] != null && admin != null) {
                TicketMessage m = new TicketMessage();
                m.setTicket(t);
                m.setAuthor(admin);
                m.setAuthorRole(RoleEnum.ADMIN);
                m.setContent((String) sample[5]);
                m.setContentHash(TextSanitizer.fingerprint((String) sample[5]));
                messageRepository.save(m);
            }
        }
        log.info("[dev] {} chamados de exemplo criados.", samples.length);
    }
}
