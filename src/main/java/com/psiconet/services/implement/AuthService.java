package com.psiconet.services.implement;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.security.TokenService;
import com.psiconet.mapper.PatientMapper;
import com.psiconet.mapper.PsychologistMapper;
import com.psiconet.mapper.UserMapper;
import com.psiconet.model.dtos.auth.AuthenticationDTO;
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
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final PsychologistRepository psychologistRepository;

    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    private final PatientMapper patientMapper;
    private final PsychologistMapper psychologistMapper;
    private final UserMapper userMapper;

    @Transactional
    public void registerPatient(PatientRegisterRequest data) {

        validateUserData(
                data.getEmail(),
                data.getCpf()
        );

        User user = userMapper.toPatientUser(
                data,
                RoleEnum.PATIENT
        );

        user.setPassword(
                passwordEncoder.encode(data.getPassword())
        );

        user.setStatus(
                UserStatusEnum.ACTIVE
        );

        userRepository.save(user);

        Patient patient = patientMapper.toPatient(
                data,
                user
        );

        patientRepository.save(patient);
    }

    @Transactional
    public void registerPsychologist(PsychologistRegisterRequest data) {

        validateUserData(
                data.getEmail(),
                data.getCpf()
        );

        validatePsychologistData(
                data.getCrp()
        );

        User user = userMapper.toPsychologistUser(
                data,
                RoleEnum.PSYCHOLOGIST
        );

        user.setPassword(
                passwordEncoder.encode(data.getPassword())
        );

        user.setStatus(
                UserStatusEnum.ACTIVE
        );

        userRepository.save(user);

        Psychologist psychologist =
                psychologistMapper.toPsychologist(
                        data,
                        user
                );

        psychologistRepository.save(psychologist);
    }

    private void validateUserData(
            String email,
            String cpf
    ) {

        if (userRepository.existsByEmail(email)) {

            throw new BusinessException(
                    "email",
                    "E-mail já cadastrado."
            );
        }

        if (userRepository.existsByCpf(cpf)) {

            throw new BusinessException(
                    "cpf",
                    "CPF já cadastrado."
            );
        }
    }

    private void validatePsychologistData(String crp) {

        if (psychologistRepository.existsByCrp(crp)) {

            throw new BusinessException(
                    "crp",
                    "CRP já cadastrado."
            );
        }
    }

    public String login(AuthenticationDTO data) {

        try {

            var usernamePassword =
                    new UsernamePasswordAuthenticationToken(
                            data.email(),
                            data.password()
                    );

            // O AuthenticationManager já verifica isEnabled() e isAccountNonExpired() da entidade User
            var auth =
                    authenticationManager.authenticate(
                            usernamePassword
                    );

            User user = (User) auth.getPrincipal();

            return tokenService.generateToken(user);

        } catch (BadCredentialsException ex) {

            throw new BusinessException(
                    "credentials",
                    "E-mail ou senha inválidos."
            );
        } catch (org.springframework.security.authentication.DisabledException |
                 org.springframework.security.authentication.LockedException ex) {

            throw new BusinessException(
                    "account",
                    "Acesso negado. Esta conta não está ativa ou está bloqueada."
            );
        }
    }
}