package com.psiconet.infra.security;

import com.psiconet.infra.web.ClientIpResolver;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.access.UserSession;
import com.psiconet.model.enums.access.SessionRevocationReasonEnum;
import com.psiconet.repositories.access.UserSessionRepository;
import com.psiconet.services.interfaces.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Sessões por dispositivo com refresh token rotativo (ADR 014).
 * <ul>
 *   <li>Refresh = {@code <sessionId>.<segredo>}; o banco guarda só o SHA-256 do segredo.</li>
 *   <li>Cada renovação troca o segredo. Reapresentar o segredo anterior dentro de
 *       {@code reuse-grace} (requisições paralelas) devolve só um novo token de acesso; fora da
 *       janela, ou qualquer segredo desconhecido, revoga a sessão inteira (possível roubo).</li>
 *   <li>Renovação exige usuário ativo e {@code versao_token} igual à do login.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserSessionRepository sessionRepository;
    private final TokenService tokenService;
    private final SessionProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final AuditService auditService;

    /** Abre uma sessão para o dispositivo atual. */
    @Transactional
    public IssuedSession start(User user, boolean rememberMe) {
        Instant now = Instant.now();
        String secret = newSecret();

        UserSession session = new UserSession();
        session.setUser(user);
        session.setRememberMe(rememberMe);
        session.setTokenVersion(user.getTokenVersion());
        session.setCurrentTokenHash(hash(secret));
        session.setCreatedAt(now);
        session.setLastUsedAt(now);
        session.setAbsoluteExpiresAt(now.plus(rememberMe ? properties.getRememberAbsolute() : properties.getAbsolute()));
        session.setIdleExpiresAt(nextIdleExpiry(session, now));
        session.setIpAddress(clientIpResolver.currentRequestIp());
        session.setUserAgent(clientIpResolver.currentRequestUserAgent());
        session = sessionRepository.save(session);

        return issue(session, user, secret);
    }

    /**
     * Renova a sessão. Lança {@link InvalidSessionException} (401 {@code SESSION_EXPIRED}) quando a
     * sessão não pode continuar — o cliente deve apagar os cookies e mandar para o login.
     * <p>
     * {@code noRollbackFor}: a revogação por reutilização precisa ser gravada mesmo com a exceção.
     */
    @Transactional(noRollbackFor = InvalidSessionException.class)
    public IssuedSession refresh(String refreshToken) {
        ParsedRefresh parsed = parse(refreshToken).orElseThrow(InvalidSessionException::new);
        UserSession session = sessionRepository.findByIdForUpdate(parsed.sessionId()).orElseThrow(InvalidSessionException::new);
        User user = session.getUser();
        Instant now = Instant.now();

        if (!session.isUsable(now) || !user.isEnabled() || user.getTokenVersion() != session.getTokenVersion()) {
            throw new InvalidSessionException();
        }

        String presentedHash = hash(parsed.secret());
        if (MessageDigest.isEqual(bytes(presentedHash), bytes(session.getCurrentTokenHash()))) {
            String secret = newSecret();
            session.setPreviousTokenHash(session.getCurrentTokenHash());
            session.setCurrentTokenHash(hash(secret));
            session.setRotatedAt(now);
            session.setLastUsedAt(now);
            session.setIdleExpiresAt(nextIdleExpiry(session, now));
            return issue(sessionRepository.save(session), user, secret);
        }

        boolean withinGrace = session.getPreviousTokenHash() != null && session.getRotatedAt() != null
                && MessageDigest.isEqual(bytes(presentedHash), bytes(session.getPreviousTokenHash()))
                && Duration.between(session.getRotatedAt(), now).compareTo(properties.getReuseGrace()) <= 0;
        if (withinGrace) {
            // Requisição paralela que saiu com o refresh anterior: o cliente já recebeu o novo
            // refresh na outra resposta, então aqui vai só um token de acesso (refresh inalterado).
            TokenService.IssuedToken access = tokenService.generateAccessToken(user, session.getId());
            return new IssuedSession(user, access.token(), access.expiresAt(), null, session.getIdleExpiresAt(), session.isRememberMe());
        }

        session.setRevokedAt(now);
        session.setRevokedReason(SessionRevocationReasonEnum.REUSE_DETECTED);
        sessionRepository.save(session);
        auditService.record(user, "SESSION_REUSE_DETECTED", "SESSION", session.getId(), null);
        log.warn("Reutilização de refresh token detectada; sessão {} revogada.", session.getId());
        throw new InvalidSessionException();
    }

    /** Sessão do token de acesso ainda vale? Conferido pelo filtro a cada requisição. */
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public boolean isActive(UUID sessionId, User user) {
        return sessionRepository.existsByIdAndUser_IdAndRevokedAtIsNullAndAbsoluteExpiresAtAfter(
                sessionId, user.getId(), Instant.now());
    }

    @Transactional
    public void revoke(UUID sessionId, SessionRevocationReasonEnum reason) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(Instant.now());
                session.setRevokedReason(reason);
                sessionRepository.save(session);
            }
        });
    }

    @Transactional
    public int revokeAll(User user, SessionRevocationReasonEnum reason) {
        return sessionRepository.revokeAllActive(user, Instant.now(), reason);
    }

    @Transactional(readOnly = true)
    public Optional<UserSession> find(UUID sessionId) {
        return sessionRepository.findById(sessionId);
    }

    private IssuedSession issue(UserSession session, User user, String secret) {
        TokenService.IssuedToken access = tokenService.generateAccessToken(user, session.getId());
        return new IssuedSession(user, access.token(), access.expiresAt(),
                session.getId() + "." + secret, session.getIdleExpiresAt(), session.isRememberMe());
    }

    private Instant nextIdleExpiry(UserSession session, Instant now) {
        Instant idle = now.plus(session.isRememberMe() ? properties.getRememberIdle() : properties.getIdle());
        return idle.isAfter(session.getAbsoluteExpiresAt()) ? session.getAbsoluteExpiresAt() : idle;
    }

    private static Optional<ParsedRefresh> parse(String refreshToken) {
        if (refreshToken == null) return Optional.empty();
        int dot = refreshToken.indexOf('.');
        if (dot <= 0 || dot == refreshToken.length() - 1 || refreshToken.length() > 200) return Optional.empty();
        try {
            return Optional.of(new ParsedRefresh(UUID.fromString(refreshToken.substring(0, dot)), refreshToken.substring(dot + 1)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String newSecret() {
        byte[] buffer = new byte[32];
        RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    private record ParsedRefresh(UUID sessionId, String secret) {}

    /**
     * Resultado de login/renovação. {@code refreshToken} nulo = manter o refresh atual (janela de
     * tolerância). {@code refreshExpiresAt} é a expiração por inatividade (o cookie acompanha).
     */
    public record IssuedSession(User user, String accessToken, Instant accessExpiresAt, String refreshToken,
                                Instant refreshExpiresAt, boolean rememberMe) {}
}
