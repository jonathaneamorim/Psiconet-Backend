package com.psiconet.infra.config;

import com.psiconet.model.dtos.auth.PasswordPolicy;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.services.interfaces.audit.AuditService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Garante a existência do ADMINISTRADOR BASE, de forma idempotente:
 * <ol>
 *   <li>Se já existe um admin marcado como base, não faz nada.</li>
 *   <li>Bases antigas (antes desta marcação) já têm admins: o admin com {@code ADMIN_EMAIL} — ou,
 *       na falta dele, o mais antigo — passa a ser o base. Nenhuma conta é criada nem alterada além disso.</li>
 *   <li>Sem nenhum admin: cria o base a partir de {@code ADMIN_EMAIL}/{@code ADMIN_PASSWORD}, com troca
 *       de senha obrigatória no primeiro acesso. Sem essas variáveis, apenas registra um aviso.</li>
 * </ol>
 * Credenciais nunca ficam no código nem com valor padrão no application.properties.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    // CPF não se aplica ao admin base criado por variável de ambiente; valor sentinela já usado
    // historicamente (a coluna é NOT NULL/UNIQUE). Admins criados pela tela informam CPF real.
    static final String BASE_ADMIN_CPF_PLACEHOLDER = "00000000000";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Value("${psiconet.admin.base-email:}")
    private String email;

    @Value("${psiconet.admin.base-password:}")
    private String password;

    @Value("${psiconet.admin.base-name:Administrador Psiconet}")
    private String name;

    @Override
    @Transactional
    public void run(String @NonNull ... args) {
        if (userRepository.findFirstByBaseAdminTrue().isPresent()) {
            return;
        }

        List<User> admins = userRepository.findByRoleOrderByCreatedAtAsc(RoleEnum.ADMIN);
        if (!admins.isEmpty()) {
            String normalized = normalize(email);
            User base = admins.stream()
                    .filter(a -> a.getEmail().equalsIgnoreCase(normalized))
                    .findFirst()
                    .orElse(admins.get(0));
            base.setBaseAdmin(true);
            userRepository.save(base);
            auditService.record(null, "BASE_ADMIN_FLAGGED", "USER", base.getId(), "Admin existente marcado como administrador base");
            log.info("Administrador base definido a partir de um admin já existente.");
            return;
        }

        if (normalize(email).isEmpty() || password == null || password.isBlank()) {
            log.warn("Nenhum administrador cadastrado e ADMIN_EMAIL/ADMIN_PASSWORD não definidos: " +
                    "o administrador base não foi criado.");
            return;
        }
        if (!password.matches(PasswordPolicy.REGEX)) {
            log.error("ADMIN_PASSWORD não atende à política de senha ({}). Administrador base não criado.", PasswordPolicy.MESSAGE);
            return;
        }
        if (userRepository.existsByEmailIgnoreCase(normalize(email))) {
            log.error("ADMIN_EMAIL já pertence a uma conta não administrativa. Administrador base não criado.");
            return;
        }

        User admin = new User();
        admin.setRole(RoleEnum.ADMIN);
        admin.setFullName(name);
        admin.setEmail(normalize(email));
        admin.setCpf(BASE_ADMIN_CPF_PLACEHOLDER);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setStatus(UserStatusEnum.ACTIVE);
        admin.setBaseAdmin(true);
        admin.setMustChangePassword(true);
        userRepository.save(admin);

        auditService.record(null, "BASE_ADMIN_CREATED", "USER", admin.getId(), "Administrador base criado na inicialização");
        log.info("Administrador base criado (troca de senha obrigatória no primeiro acesso).");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
