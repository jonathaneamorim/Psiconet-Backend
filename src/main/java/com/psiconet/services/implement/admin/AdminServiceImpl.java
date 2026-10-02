package com.psiconet.services.implement.admin;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.ConflictException;
import com.psiconet.infra.exceptions.EmailAlreadyExistsException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.mail.EmailService;
import com.psiconet.infra.security.SessionService;
import com.psiconet.model.enums.access.SessionRevocationReasonEnum;
import com.psiconet.infra.text.TextSanitizer;
import com.psiconet.mapper.UserMapper;
import com.psiconet.model.dtos.admin.*;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.model.enums.support.TicketStatusEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.support.TicketRepository;
import com.psiconet.services.interfaces.NotificationService;
import com.psiconet.services.interfaces.admin.AdminService;
import com.psiconet.services.interfaces.audit.AuditService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    // Status que o admin pode atribuir pela tela (PENDING_VERIFICATION é só do fluxo de cadastro).
    private static final Set<UserStatusEnum> ASSIGNABLE_STATUSES =
            EnumSet.of(UserStatusEnum.ACTIVE, UserStatusEnum.INACTIVE, UserStatusEnum.BLOCKED);

    private final UserRepository userRepository;
    private final TicketRepository ticketRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final SessionService sessionService;

    @Override
    @Transactional(readOnly = true)
    public Page<UsersToListAdminDTO> listUsers(String search, RoleEnum role, UserStatusEnum status, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) predicates.add(cb.equal(root.get("role"), role));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            String term = TextSanitizer.cleanLine(search);
            if (term != null && !term.isBlank()) {
                String like = "%" + term.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                // Busca por nome ou e-mail. CPF propositalmente fora da busca: a tela só conhece o CPF mascarado.
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like, '\\'),
                        cb.like(cb.lower(root.get("email")), like, '\\')
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return userRepository.findAll(spec, pageable).map(userMapper::toAdminListDto);
    }

    @Override
    @Transactional
    public void updateUserStatus(User actor, UUID id, UserStatusUpdateDTO statusDto) {
        UserStatusEnum newStatus = statusDto.getStatus();
        if (!ASSIGNABLE_STATUSES.contains(newStatus)) {
            throw new BusinessException("status", "Status inválido para alteração administrativa.");
        }

        User target = userRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new EntityNotFoundException(User.class, id));
        UserStatusEnum previous = target.getStatus();
        if (previous == newStatus) return;

        if (target.getId().equals(actor.getId())) {
            throw new BusinessException("status", "Você não pode alterar o status da sua própria conta.");
        }
        boolean deactivating = newStatus != UserStatusEnum.ACTIVE;
        if (target.getRole() == RoleEnum.ADMIN && deactivating) {
            if (target.isBaseAdmin()) {
                throw new BusinessException("status", "O administrador base não pode ser inativado ou bloqueado.");
            }
            if (previous == UserStatusEnum.ACTIVE
                    && userRepository.countByRoleAndStatus(RoleEnum.ADMIN, UserStatusEnum.ACTIVE) <= 1) {
                throw new BusinessException("status", "É preciso manter ao menos um administrador ativo.");
            }
        }

        target.setStatus(newStatus);
        if (deactivating) {
            // Derruba as sessões abertas imediatamente (o filtro também barra status != ACTIVE)
            target.setTokenVersion(target.getTokenVersion() + 1);
            sessionService.revokeAll(target, SessionRevocationReasonEnum.ADMIN);
        }
        userRepository.save(target);

        auditService.record(actor, "USER_STATUS_CHANGED", "USER", target.getId(), previous + " -> " + newStatus);
    }

    @Override
    @Transactional
    public void updateUser(User actor, UUID id, UserUpdateAdminDTO updateDto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(User.class, id));

        List<String> changed = new ArrayList<>();

        String fullName = TextSanitizer.cleanLine(updateDto.getFullName());
        if (fullName != null && !fullName.equals(user.getFullName())) {
            user.setFullName(fullName);
            changed.add("nome");
        }

        String phone = updateDto.getPhone() == null ? null : updateDto.getPhone().trim();
        if (phone != null && !phone.equals(user.getPhone() == null ? "" : user.getPhone())) {
            user.setPhone(phone.isEmpty() ? null : phone);
            changed.add("telefone");
        }

        if (updateDto.getEmail() != null && !updateDto.getEmail().isBlank()) {
            String email = updateDto.getEmail().trim().toLowerCase(Locale.ROOT);
            if (!email.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByEmailIgnoreCase(email)) {
                    throw new EmailAlreadyExistsException();
                }
                user.setEmail(email);
                // O subject do JWT é o e-mail: tokens antigos deixam de valer
                user.setTokenVersion(user.getTokenVersion() + 1);
                sessionService.revokeAll(user, SessionRevocationReasonEnum.ADMIN);
                changed.add("e-mail");
            }
        }

        if (changed.isEmpty()) return;
        userRepository.save(user);
        // Só os NOMES dos campos alterados vão para a auditoria (sem os valores pessoais)
        auditService.record(actor, "USER_UPDATED", "USER", user.getId(), "Campos alterados: " + String.join(", ", changed));
    }

    @Override
    @Transactional
    public UsersToListAdminDTO createAdmin(User actor, AdminCreateDTO dto) {
        String email = dto.email().trim().toLowerCase(Locale.ROOT);
        String cpf = dto.cpf().replaceAll("\\D", "");

        // Sempre uma conta NOVA: não existe caminho para promover paciente/psicólogo a admin.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("email",
                    "Este e-mail já pertence a uma conta. Administradores precisam de um e-mail exclusivo.");
        }
        if (userRepository.existsByCpf(cpf)) {
            throw new ConflictException("cpf", "Este CPF já está vinculado a outra conta.");
        }

        User admin = new User();
        admin.setRole(RoleEnum.ADMIN);
        admin.setFullName(TextSanitizer.cleanLine(dto.fullName()));
        admin.setEmail(email);
        admin.setCpf(cpf);
        admin.setPassword(passwordEncoder.encode(dto.temporaryPassword()));
        admin.setStatus(UserStatusEnum.ACTIVE);
        admin.setMustChangePassword(true);
        admin.setBaseAdmin(false);
        admin.setCreatedBy(userRepository.getReferenceById(actor.getId()));
        userRepository.saveAndFlush(admin);

        auditService.record(actor, "ADMIN_CREATED", "USER", admin.getId(), "Novo administrador criado");
        emailService.sendAdminInvite(admin.getEmail(), admin.getFullName(), actor.getFullName());

        return userMapper.toAdminListDto(admin);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardDTO dashboard() {
        Map<TicketStatusEnum, Long> tickets = new EnumMap<>(TicketStatusEnum.class);
        for (Object[] row : ticketRepository.countGroupedByStatus()) {
            tickets.put((TicketStatusEnum) row[0], (Long) row[1]);
        }

        long total = userRepository.count();
        long active = userRepository.countByStatus(UserStatusEnum.ACTIVE);
        return new AdminDashboardDTO(
                total,
                active,
                total - active,
                userRepository.countByRoleAndStatus(RoleEnum.PSYCHOLOGIST, UserStatusEnum.ACTIVE),
                userRepository.countByRoleAndStatus(RoleEnum.PATIENT, UserStatusEnum.ACTIVE),
                userRepository.countByRole(RoleEnum.PSYCHOLOGIST),
                userRepository.countByRole(RoleEnum.PATIENT),
                userRepository.countByRole(RoleEnum.ADMIN),
                AdminDashboardDTO.TicketCounts.of(
                        tickets.getOrDefault(TicketStatusEnum.OPEN, 0L),
                        tickets.getOrDefault(TicketStatusEnum.IN_PROGRESS, 0L),
                        tickets.getOrDefault(TicketStatusEnum.RESOLVED, 0L),
                        tickets.getOrDefault(TicketStatusEnum.CLOSED, 0L)
                ),
                LocalDateTime.now()
        );
    }

    @Override
    @Transactional
    public void notifyUser(User actor, AdminNotifyUserDTO dto) {
        User recipient = userRepository.findById(dto.recipientId())
                .orElseThrow(() -> new EntityNotFoundException(User.class, dto.recipientId()));

        String title = TextSanitizer.cleanLine(dto.title());
        String message = TextSanitizer.clean(dto.message());
        if (title == null || title.length() < 3 || message == null || message.length() < 3) {
            throw new BusinessException("message", "Preencha título e mensagem.");
        }

        notificationService.create(recipient, actor, NotificationType.ADMIN_ANNOUNCEMENT,
                title, message, "ANNOUNCEMENT", null);
        emailService.sendAdminAnnouncement(recipient.getEmail(), recipient.getFullName(), title, message);

        auditService.record(actor, "ADMIN_ANNOUNCEMENT_SENT", "USER", recipient.getId(), "Título: " + title);
    }
}
