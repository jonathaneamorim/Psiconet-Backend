package com.psiconet.services.implement;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.mail.EmailService;
import com.psiconet.model.entities.access.PasswordResetCode;
import com.psiconet.model.entities.access.User;
import com.psiconet.repositories.access.PasswordResetCodeRepository;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.services.interfaces.PasswordResetService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    // 256 bits de entropia: inviável de adivinhar por força bruta, então o link em si já é a prova
    // de posse — diferente do código de 6 dígitos antigo, não há mais motivo para limitar tentativas.
    private static final int TOKEN_BYTES = 32;
    private static final String INVALID_TOKEN_MESSAGE = "Link de redefinição inválido ou expirado.";

    private final UserRepository userRepository;
    private final PasswordResetCodeRepository passwordResetCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Value("${psiconet.password-reset.expiration-minutes}")
    private int expirationMinutes;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public void requestReset(String email) {
        // Não revela se o e-mail existe ou não: o controller sempre responde com a mesma mensagem genérica.
        userRepository.findByEmail(email).ifPresent(user -> {
            passwordResetCodeRepository.deleteUnusedByUser(user);

            String token = generateToken();

            PasswordResetCode resetCode = PasswordResetCode.builder()
                    .user(user)
                    .tokenHash(hashToken(token))
                    .expiresAt(LocalDateTime.now().plusMinutes(expirationMinutes))
                    .used(false)
                    .build();

            passwordResetCodeRepository.save(resetCode);

            // sendPasswordResetLink é @Async: dispara o envio em background e retorna na hora,
            // então a resposta do endpoint não fica presa esperando o SMTP. O comportamento
            // observável pelo cliente continua idêntico ao de um e-mail que não existe na base
            // — sempre a mesma mensagem genérica de sucesso, mesmo que o envio falhe depois.
            // O token vai apenas no e-mail: nunca é logado nem retornado em nenhuma resposta HTTP.
            emailService.sendPasswordResetLink(user.getEmail(), user.getFullName(), token, expirationMinutes);
        });
    }

    @Override
    @Transactional
    public void validateResetToken(String token) {
        // Chamado pelo front ao abrir a tela de troca de senha (a partir do link recebido por
        // e-mail): garante que o token ainda é válido (não expirado, não usado) sem consumi-lo —
        // a validação "definitiva" continua em confirmReset.
        resolveValidToken(token);
    }

    @Override
    @Transactional
    public void confirmReset(String token, String newPassword) {
        PasswordResetCode resetCode = resolveValidToken(token);

        resetCode.setUsed(true);
        passwordResetCodeRepository.save(resetCode);

        User user = resetCode.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private PasswordResetCode resolveValidToken(String token) {
        PasswordResetCode resetCode = passwordResetCodeRepository
                .findByTokenHashAndUsedFalse(hashToken(token))
                .orElseThrow(() -> new BusinessException("token", INVALID_TOKEN_MESSAGE));

        if (resetCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("token", INVALID_TOKEN_MESSAGE);
        }

        return resetCode;
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // SHA-256 (determinístico) em vez do PasswordEncoder de senhas: precisamos localizar a linha
    // diretamente por igualdade de hash (sem já saber o usuário), e o token de 256 bits gerado
    // acima já tem entropia suficiente para não precisar de salt nem de custo computacional extra.
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível na JVM.", e);
        }
    }
}
