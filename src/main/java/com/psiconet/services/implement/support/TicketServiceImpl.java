package com.psiconet.services.implement.support;

import com.psiconet.infra.config.TicketProperties;
import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.ConflictException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.exceptions.TooManyRequestsException;
import com.psiconet.infra.mail.EmailService;
import com.psiconet.infra.text.TextSanitizer;
import com.psiconet.model.dtos.support.*;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.support.Ticket;
import com.psiconet.model.entities.support.TicketMessage;
import com.psiconet.model.enums.NotificationType;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.support.TicketCategoryEnum;
import com.psiconet.model.enums.support.TicketPriorityEnum;
import com.psiconet.model.enums.support.TicketStatusEnum;
import com.psiconet.repositories.support.TicketMessageRepository;
import com.psiconet.repositories.support.TicketRepository;
import com.psiconet.services.interfaces.NotificationService;
import com.psiconet.services.interfaces.audit.AuditService;
import com.psiconet.services.interfaces.support.TicketService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketServiceImpl implements TicketService {

    private static final String SUPPORT_DISPLAY_NAME = "Equipe Psiconet";
    private static final String REFERENCE_TYPE = "TICKET";
    private static final DateTimeFormatter PROTOCOL_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final TicketProperties properties;

    // ------------------------------------------------------------------ usuário

    @Override
    @Transactional
    public TicketDetailDTO open(User requester, TicketCreateDTO dto) {
        ensureCanUseSupport(requester);

        String subject = TextSanitizer.cleanLine(dto.subject());
        String description = TextSanitizer.clean(dto.description());
        validateLength("subject", subject, 5, 120, "O assunto deve ter entre 5 e 120 caracteres.");
        validateLength("description", description, 10, 4000, "A descrição deve ter entre 10 e 4000 caracteres.");

        enforceOpeningLimits(requester);

        String hash = TextSanitizer.fingerprint(subject, description);
        if (ticketRepository.existsByRequesterAndContentHashAndCreatedAtAfter(
                requester, hash, LocalDateTime.now().minus(properties.getDuplicateWindow()))) {
            throw new ConflictException(null,
                    "Você já abriu um chamado com este mesmo conteúdo recentemente. Acompanhe-o em \"Meus chamados\".");
        }

        Ticket ticket = new Ticket();
        ticket.setProtocol(generateProtocol());
        ticket.setRequester(requester);
        ticket.setCategory(dto.category());
        ticket.setSubject(subject);
        ticket.setDescription(description);
        ticket.setPriority(TicketPriorityEnum.MEDIUM);
        ticket.setStatus(TicketStatusEnum.OPEN);
        ticket.setContentHash(hash);
        ticket.setLastMessageAt(LocalDateTime.now());
        ticketRepository.save(ticket);

        return toDetail(ticket, requester, false);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TicketSummaryDTO> listMine(User requester, TicketStatusEnum status, Pageable pageable) {
        ensureCanUseSupport(requester);
        Page<Ticket> page = status == null
                ? ticketRepository.findByRequester(requester, pageable)
                : ticketRepository.findByRequesterAndStatus(requester, status, pageable);
        return page.map(t -> toSummary(t, false));
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDetailDTO getMine(User requester, UUID ticketId) {
        ensureCanUseSupport(requester);
        return toDetail(findOwned(requester, ticketId), requester, false);
    }

    @Override
    @Transactional
    public TicketDetailDTO replyAsRequester(User requester, UUID ticketId, TicketMessageCreateDTO dto) {
        ensureCanUseSupport(requester);
        Ticket ticket = findOwned(requester, ticketId);

        if (ticket.getStatus() == TicketStatusEnum.CLOSED) {
            throw new BusinessException("ticket", "Este chamado foi encerrado. Abra um novo chamado se precisar de ajuda.");
        }

        addMessage(ticket, requester, dto.content());

        // Resposta do usuário num chamado resolvido reabre o atendimento
        if (ticket.getStatus() == TicketStatusEnum.RESOLVED) {
            ticket.setStatus(TicketStatusEnum.OPEN);
            ticket.setResolvedAt(null);
        }
        ticketRepository.save(ticket);
        return toDetail(ticket, requester, false);
    }

    @Override
    @Transactional
    public TicketDetailDTO closeByRequester(User requester, UUID ticketId) {
        ensureCanUseSupport(requester);
        Ticket ticket = findOwned(requester, ticketId);
        if (ticket.getStatus() != TicketStatusEnum.CLOSED) {
            ticket.setStatus(TicketStatusEnum.CLOSED);
            ticket.setClosedAt(LocalDateTime.now());
            ticketRepository.save(ticket);
        }
        return toDetail(ticket, requester, false);
    }

    // ------------------------------------------------------------------ admin

    @Override
    @Transactional(readOnly = true)
    public Page<TicketSummaryDTO> listForAdmin(TicketStatusEnum status, TicketPriorityEnum priority,
                                               TicketCategoryEnum category, String search, Pageable pageable) {
        Specification<Ticket> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            if (priority != null) predicates.add(cb.equal(root.get("priority"), priority));
            if (category != null) predicates.add(cb.equal(root.get("category"), category));
            String term = TextSanitizer.cleanLine(search);
            if (term != null && !term.isBlank()) {
                String like = "%" + term.toLowerCase(Locale.ROOT).replace("%", "\\%").replace("_", "\\_") + "%";
                var requester = root.join("requester");
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("subject")), like, '\\'),
                        cb.like(cb.lower(root.get("protocol")), like, '\\'),
                        cb.like(cb.lower(requester.get("fullName")), like, '\\'),
                        cb.like(cb.lower(requester.get("email")), like, '\\')
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return ticketRepository.findAll(spec, pageable).map(t -> toSummary(t, true));
    }

    @Override
    @Transactional(readOnly = true)
    public TicketDetailDTO getForAdmin(UUID ticketId) {
        return toDetail(findAny(ticketId), null, true);
    }

    @Override
    @Transactional
    public TicketDetailDTO replyAsAdmin(User admin, UUID ticketId, TicketMessageCreateDTO dto) {
        Ticket ticket = findAny(ticketId);
        if (ticket.getStatus() == TicketStatusEnum.CLOSED) {
            throw new BusinessException("ticket", "Chamado encerrado não recebe novas respostas.");
        }

        addMessage(ticket, admin, dto.content());
        if (ticket.getStatus() == TicketStatusEnum.OPEN) {
            ticket.setStatus(TicketStatusEnum.IN_PROGRESS);
        }
        ticketRepository.save(ticket);

        User requester = ticket.getRequester();
        notificationService.create(requester, admin, NotificationType.TICKET_REPLIED,
                "Seu chamado foi respondido",
                "A equipe Psiconet respondeu ao chamado " + ticket.getProtocol() + ".",
                REFERENCE_TYPE, ticket.getId());
        emailService.sendTicketReplied(requester.getEmail(), requester.getFullName(), ticket.getProtocol(), userPath(ticket));
        auditService.record(admin, "TICKET_REPLIED", "TICKET", ticket.getId(), "Protocolo " + ticket.getProtocol());

        return toDetail(ticket, admin, true);
    }

    @Override
    @Transactional
    public TicketDetailDTO updateStatusAsAdmin(User admin, UUID ticketId, TicketStatusEnum status) {
        Ticket ticket = findAny(ticketId);
        TicketStatusEnum previous = ticket.getStatus();
        if (previous == status) {
            return toDetail(ticket, admin, true);
        }
        if (previous == TicketStatusEnum.CLOSED) {
            throw new BusinessException("status", "Chamado encerrado não pode ser reaberto. O usuário pode abrir um novo chamado.");
        }

        ticket.setStatus(status);
        LocalDateTime now = LocalDateTime.now();
        if (status == TicketStatusEnum.RESOLVED) ticket.setResolvedAt(now);
        if (status == TicketStatusEnum.CLOSED) ticket.setClosedAt(now);
        if (status.isActive()) ticket.setResolvedAt(null);
        ticketRepository.save(ticket);

        auditService.record(admin, "TICKET_STATUS_CHANGED", "TICKET", ticket.getId(),
                "Protocolo " + ticket.getProtocol() + ": " + previous + " -> " + status);

        // Finalização (resolvido/encerrado) avisa o solicitante; uma única vez por finalização
        boolean finalized = !status.isActive() && previous.isActive();
        if (finalized) {
            User requester = ticket.getRequester();
            notificationService.create(requester, admin, NotificationType.TICKET_CLOSED,
                    "Seu chamado foi finalizado",
                    "O chamado " + ticket.getProtocol() + " foi finalizado pela equipe Psiconet.",
                    REFERENCE_TYPE, ticket.getId());
            emailService.sendTicketClosed(requester.getEmail(), requester.getFullName(), ticket.getProtocol(), userPath(ticket));
        }
        return toDetail(ticket, admin, true);
    }

    @Override
    @Transactional
    public TicketDetailDTO updatePriorityAsAdmin(User admin, UUID ticketId, TicketPriorityEnum priority) {
        Ticket ticket = findAny(ticketId);
        if (ticket.getPriority() != priority) {
            auditService.record(admin, "TICKET_PRIORITY_CHANGED", "TICKET", ticket.getId(),
                    "Protocolo " + ticket.getProtocol() + ": " + ticket.getPriority() + " -> " + priority);
            ticket.setPriority(priority);
            ticketRepository.save(ticket);
        }
        return toDetail(ticket, admin, true);
    }

    // ------------------------------------------------------------------ regras internas

    private void ensureCanUseSupport(User user) {
        if (user.getRole() != RoleEnum.PATIENT && user.getRole() != RoleEnum.PSYCHOLOGIST) {
            throw new org.springframework.security.access.AccessDeniedException("Somente pacientes e psicólogos abrem chamados.");
        }
    }

    private void enforceOpeningLimits(User requester) {
        long active = ticketRepository.countByRequesterAndStatusIn(
                requester, EnumSet.of(TicketStatusEnum.OPEN, TicketStatusEnum.IN_PROGRESS));
        if (active >= properties.getMaxActivePerUser()) {
            throw new BusinessException("ticket", "Você já tem " + active + " chamados em aberto. "
                    + "Aguarde a resposta ou encerre um deles antes de abrir outro.");
        }

        ticketRepository.findFirstByRequesterOrderByCreatedAtDesc(requester).ifPresent(last -> {
            Duration since = Duration.between(last.getCreatedAt(), LocalDateTime.now());
            Duration min = properties.getMinIntervalBetweenOpenings();
            if (since.compareTo(min) < 0) {
                long wait = Math.max(1, min.minus(since).toSeconds());
                throw new TooManyRequestsException(
                        "Você abriu um chamado há pouco tempo. Aguarde um instante antes de abrir outro.", wait);
            }
        });
    }

    private void addMessage(Ticket ticket, User author, String rawContent) {
        String content = TextSanitizer.clean(rawContent);
        validateLength("content", content, 1, 4000, "A mensagem deve ter entre 1 e 4000 caracteres.");

        String hash = TextSanitizer.fingerprint(content);
        if (messageRepository.existsByTicketAndAuthorAndContentHashAndCreatedAtAfter(
                ticket, author, hash, LocalDateTime.now().minus(properties.getDuplicateMessageWindow()))) {
            throw new ConflictException("content", "Esta mensagem já foi enviada.");
        }

        TicketMessage message = new TicketMessage();
        message.setTicket(ticket);
        message.setAuthor(author);
        message.setAuthorRole(author.getRole());
        message.setContent(content);
        message.setContentHash(hash);
        messageRepository.save(message);

        ticket.getMessages().add(message);
        ticket.setLastMessageAt(LocalDateTime.now());
    }

    private Ticket findOwned(User requester, UUID ticketId) {
        // 404 (e não 403) para chamados de outra pessoa: não confirma que o id existe (anti-IDOR)
        return ticketRepository.findByIdAndRequester(ticketId, requester)
                .orElseThrow(() -> new EntityNotFoundException(Ticket.class, ticketId));
    }

    private Ticket findAny(UUID ticketId) {
        return ticketRepository.findWithRequesterById(ticketId)
                .orElseThrow(() -> new EntityNotFoundException(Ticket.class, ticketId));
    }

    private static void validateLength(String field, String value, int min, int max, String message) {
        if (value == null || value.length() < min || value.length() > max) {
            throw new BusinessException(field, message);
        }
    }

    private String generateProtocol() {
        String protocol;
        do {
            protocol = "CH-" + LocalDateTime.now().format(PROTOCOL_DATE) + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        } while (ticketRepository.existsByProtocol(protocol));
        return protocol;
    }

    private static String userPath(Ticket ticket) {
        return "/support/" + ticket.getId();
    }

    // ------------------------------------------------------------------ mapeamento

    private TicketSummaryDTO toSummary(Ticket t, boolean adminView) {
        return new TicketSummaryDTO(
                t.getId(), t.getProtocol(), t.getCategory(), t.getSubject(), t.getPriority(), t.getStatus(),
                t.getCreatedAt(), t.getUpdatedAt(), t.getLastMessageAt(),
                adminView ? t.getRequester().getFullName() : null,
                adminView ? t.getRequester().getRole() : null
        );
    }

    private TicketDetailDTO toDetail(Ticket t, User viewer, boolean adminView) {
        List<TicketMessageDTO> messages = messageRepository.findByTicketOrderByCreatedAtAsc(t).stream()
                .map(m -> {
                    boolean fromSupport = m.getAuthorRole() == RoleEnum.ADMIN;
                    String name = fromSupport && !adminView ? SUPPORT_DISPLAY_NAME : m.getAuthor().getFullName();
                    boolean mine = viewer != null && m.getAuthor().getId().equals(viewer.getId());
                    return new TicketMessageDTO(m.getId(), name, fromSupport, mine, m.getContent(), m.getCreatedAt());
                })
                .toList();

        User requester = t.getRequester();
        return new TicketDetailDTO(
                t.getId(), t.getProtocol(), t.getCategory(), t.getSubject(), t.getDescription(),
                t.getPriority(), t.getStatus(), t.getCreatedAt(), t.getUpdatedAt(), t.getResolvedAt(), t.getClosedAt(),
                requester.getFullName(),
                adminView ? requester.getEmail() : null,
                adminView ? requester.getRole() : null,
                t.getStatus() != TicketStatusEnum.CLOSED,
                messages
        );
    }
}
