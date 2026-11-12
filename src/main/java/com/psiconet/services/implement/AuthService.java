package com.psiconet.services.implement;

import com.psiconet.infra.exceptions.AccountStatusException;
import com.psiconet.infra.exceptions.ConflictException;
import com.psiconet.infra.exceptions.EmailAlreadyExistsException;
import com.psiconet.infra.mail.EmailService;
import com.psiconet.infra.ratelimit.RateLimiterService;
import com.psiconet.infra.security.InvalidSessionException;
import com.psiconet.infra.security.SessionDetails;
import com.psiconet.infra.security.SessionService;
import com.psiconet.model.entities.access.UserSession;
import com.psiconet.model.enums.access.SessionRevocationReasonEnum;
import com.psiconet.mapper.PatientMapper;
import com.psiconet.mapper.PsychologistMapper;
import com.psiconet.mapper.UserMapper;
import com.psiconet.model.dtos.auth.AuthenticationDTO;
import com.psiconet.model.dtos.auth.ChangePasswordRequestDTO;
import com.psiconet.model.dtos.auth.LoginResponseDTO;
import com.psiconet.model.dtos.auth.PatientRegisterRequest;
import com.psiconet.model.dtos.auth.PsychologistRegisterRequest;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.profile.PatientRepository;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.services.interfaces.audit.AuditService;
import com.psiconet.services.interfaces.legal.ConsentService;
import com.psiconet.infra.exceptions.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final int MINIMUM_AGE_YEARS = 18;
    private static final int MAX_AGE_YEARS = 120;

    private static final String CPF_CONFLICT_MESSAGE =
            "Não foi possível concluir o cadastro com este CPF. Se você já possui conta, faça login ou recupere sua senha.";
    private static final String CRP_CONFLICT_MESSAGE =
            "Este CRP já está vinculado a uma conta. Se ela é sua, faça login ou recupere sua senha.";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PsychologistRepository psychologistRepository;

    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final EmailService emailService;
    private final AuditService auditService;
    private final RateLimiterService rateLimiter;
    private final ConsentService consentService;

    private final PatientMapper patientMapper;
    private final PsychologistMapper psychologistMapper;
    private final UserMapper userMapper;

    // Hash usado quando o e-mail não existe: mantém o custo do BCrypt nos dois caminhos e evita
    // que o tempo de resposta revele se o e-mail está cadastrado.
    private String dummyHash;

    @PostConstruct
    void init() {
        dummyHash = passwordEncoder.encode("psiconet-dummy-password-for-timing");
    }

    /*
     * Cadastro atômico: usuário + perfil na mesma transação, com flush para que violações de
     * constraint (cadastro concorrente com o mesmo e-mail/CPF) estourem aqui dentro e virem um
     * erro amigável. O e-mail de boas-vindas só é enviado depois do commit e de forma assíncrona
     * (EmailDispatcher): falha de SMTP nunca desfaz nem trava o cadastro.
     */
    @Transactional
    public void registerPatient(PatientRegisterRequest data) {
        String email = normalizeEmail(data.getEmail());
        consentService.requireCurrentVersions(data.getTermsVersion(), data.getPrivacyVersion());
        requireMinimumAge(data.getBirthDate());
        validateUserData(email, data.getCpf());

        User user = userMapper.toPatientUser(data, RoleEnum.PATIENT);
        prepareNewUser(user, email, data.getPassword());

        Patient patient = patientMapper.toPatient(data, user);
        persist(user, () -> patientRepository.saveAndFlush(patient));
        // Mesma transação: sem prova de aceite não existe conta (e vice-versa).
        consentService.recordRegistration(user, data.isAcceptHealthData());

        emailService.sendWelcome(user.getEmail(), user.getFullName(), "Paciente");
    }

    @Transactional
    public void registerPsychologist(PsychologistRegisterRequest data) {
        String email = normalizeEmail(data.getEmail());
        consentService.requireCurrentVersions(data.getTermsVersion(), data.getPrivacyVersion());
        requireMinimumAge(data.getBirthDate());
        validateUserData(email, data.getCpf());

        String crp = data.getCrp().trim();
        if (psychologistRepository.existsByCrp(crp)) {
            throw new ConflictException("crp", CRP_CONFLICT_MESSAGE);
        }
        data.setCrp(crp);

        User user = userMapper.toPsychologistUser(data, RoleEnum.PSYCHOLOGIST);
        prepareNewUser(user, email, data.getPassword());

        Psychologist psychologist = psychologistMapper.toPsychologist(data, user);
        persist(user, () -> psychologistRepository.saveAndFlush(psychologist));
        consentService.recordRegistration(user, false);

        emailService.sendWelcome(user.getEmail(), user.getFullName(), "Psicólogo(a)");
    }

    /*
     * Login em duas etapas: (1) confere a senha — qualquer falha aqui (e-mail inexistente ou senha
     * errada) responde a MESMA mensagem genérica; (2) só depois, com a senha correta, informa o
     * status da conta (inativa, bloqueada, aguardando verificação).
     */
    @Transactional
    public LoginResponseDTO login(AuthenticationDTO data) {
        String email = normalizeEmail(data.email());
        // Além do limite por IP (anotação no controller), limita tentativas por conta-alvo:
        // freia ataques distribuídos de força bruta contra um mesmo e-mail.
        rateLimiter.consume("login-account", "email:" + email);

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            passwordEncoder.matches(data.password(), dummyHash);
            throw new BadCredentialsException("Credenciais inválidas");
        }
        if (!passwordEncoder.matches(data.password(), user.getPassword())) {
            throw new BadCredentialsException("Credenciais inválidas");
        }

        ensureCanLogin(user);

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        return toResponse(sessionService.start(user, data.rememberMeEnabled()));
    }

    /** "Sair": encerra só a sessão deste dispositivo (o token de acesso dela morre na hora). */
    @Transactional
    public void logout(User user) {
        if (user == null) return;
        SessionDetails.currentSessionId().ifPresent(id -> sessionService.revoke(id, SessionRevocationReasonEnum.LOGOUT));
    }

    /** "Sair de todos os dispositivos": invalida todos os tokens e sessões do usuário. */
    @Transactional
    public void logoutAll(User user) {
        if (user == null) return;
        userRepository.findById(user.getId()).ifPresent(managed -> {
            managed.setTokenVersion(managed.getTokenVersion() + 1);
            userRepository.save(managed);
            sessionService.revokeAll(managed, SessionRevocationReasonEnum.LOGOUT_ALL);
            auditService.record(managed, "LOGOUT_ALL_DEVICES", "USER", managed.getId(), null);
        });
    }

    /** Renova a sessão com o refresh token (rotação + detecção de reutilização no SessionService). */
    @Transactional(noRollbackFor = InvalidSessionException.class)
    public LoginResponseDTO refresh(String refreshToken) {
        return toResponse(sessionService.refresh(refreshToken));
    }

    /**
     * Troca de senha pelo próprio usuário (inclui a troca obrigatória do primeiro acesso de
     * administradores). Invalida os tokens antigos e devolve um novo para a sessão atual.
     */
    @Transactional
    public LoginResponseDTO changePassword(User principal, ChangePasswordRequestDTO data) {
        User user = userRepository.findById(principal.getId()).orElseThrow(() -> new BadCredentialsException("Sessão inválida"));

        if (!passwordEncoder.matches(data.currentPassword(), user.getPassword())) {
            throw new com.psiconet.infra.exceptions.BusinessException("currentPassword", "A senha atual está incorreta.");
        }
        if (passwordEncoder.matches(data.newPassword(), user.getPassword())) {
            throw new com.psiconet.infra.exceptions.BusinessException("newPassword", "A nova senha deve ser diferente da atual.");
        }

        user.setPassword(passwordEncoder.encode(data.newPassword()));
        user.setMustChangePassword(false);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        auditService.record(user, "PASSWORD_CHANGED", "USER", user.getId(), null);

        // Todas as sessões caem (inclusive em outros dispositivos); este dispositivo ganha uma nova,
        // mantendo a escolha de "manter conectado" da sessão atual.
        boolean rememberMe = SessionDetails.currentSessionId()
                .flatMap(sessionService::find)
                .map(UserSession::isRememberMe)
                .orElse(false);
        sessionService.revokeAll(user, SessionRevocationReasonEnum.PASSWORD_CHANGED);
        emailService.sendPasswordChanged(user.getEmail(), user.getFullName());
        return toResponse(sessionService.start(user, rememberMe));
    }

    private LoginResponseDTO toResponse(SessionService.IssuedSession issued) {
        User user = issued.user();
        return new LoginResponseDTO(issued.accessToken(), issued.accessExpiresAt(),
                issued.refreshToken(), issued.refreshExpiresAt(), issued.rememberMe(),
                user.getRole(), user.isMustChangePassword(), consentService.isConsentRequired(user));
    }

    private void ensureCanLogin(User user) {
        UserStatusEnum status = user.getStatus();
        if (status == UserStatusEnum.ACTIVE) return;

        switch (status) {
            case INACTIVE -> throw new AccountStatusException("ACCOUNT_INACTIVE",
                    "Sua conta está inativa. Entre em contato com a administração da Psiconet para reativá-la.");
            case BLOCKED -> throw new AccountStatusException("ACCOUNT_BLOCKED",
                    "Sua conta está bloqueada. Entre em contato com a administração da Psiconet.");
            case PENDING_VERIFICATION -> throw new AccountStatusException("ACCOUNT_PENDING",
                    "Sua conta ainda está aguardando verificação. Entre em contato com a administração da Psiconet.");
            default -> throw new AccountStatusException("ACCOUNT_UNAVAILABLE",
                    "Sua conta não está disponível no momento.");
        }
    }

    /** Termos de Uso, seção 4.1: cadastro só para maiores de 18 anos (regra a validar com jurídico). */
    static void requireMinimumAge(LocalDate birthDate) {
        if (birthDate == null) return; // @NotNull no DTO já responde a ausência
        if (birthDate.isBefore(LocalDate.now().minusYears(MAX_AGE_YEARS))) {
            throw new BusinessException("birthDate", "Informe uma data de nascimento válida.");
        }
        if (Period.between(birthDate, LocalDate.now()).getYears() < MINIMUM_AGE_YEARS) {
            throw new BusinessException("birthDate",
                    "É preciso ter " + MINIMUM_AGE_YEARS + " anos ou mais para se cadastrar na Psiconet.");
        }
    }

    private void validateUserData(String email, String cpf) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException();
        }
        if (userRepository.existsByCpf(cpf)) {
            throw new ConflictException("cpf", CPF_CONFLICT_MESSAGE);
        }
    }

    private void prepareNewUser(User user, String email, String rawPassword) {
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        // O papel vem do endpoint chamado (paciente/psicólogo), nunca do corpo da requisição.
        user.setStatus(UserStatusEnum.ACTIVE);
    }

    private void persist(User user, Runnable saveProfile) {
        try {
            userRepository.saveAndFlush(user);
            saveProfile.run();
        } catch (DataIntegrityViolationException e) {
            // Corrida entre a validação e o insert (dois cadastros simultâneos com os mesmos dados).
            throw new ConflictException(null,
                    "Não foi possível concluir o cadastro: já existe uma conta com estes dados. Faça login ou recupere sua senha.");
        }
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
