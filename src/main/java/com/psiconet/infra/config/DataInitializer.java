package com.psiconet.infra.config;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.model.enums.UserStatusEnum;
import com.psiconet.repositories.access.UserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${BASE_EMAIL}")
    private String email;

    @Value("${BASE_PASSWORD}")
    private String password;


    @Override
    public void run(String @NonNull ... args) {

        boolean adminExists = userRepository.existsByRole(RoleEnum.ADMIN);

        if (!adminExists) {
            User admin = new User();
            admin.setRole(RoleEnum.ADMIN);
            admin.setEmail(email);
            admin.setCpf("00000000000");
            admin.setPassword(passwordEncoder.encode(password));
            admin.setStatus(UserStatusEnum.ACTIVE);

            userRepository.save(admin);
        }
    }
}
