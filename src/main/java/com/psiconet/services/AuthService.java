package com.psiconet.services;

import com.psiconet.exception.EmailAlreadyExistsException;
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
import com.psiconet.repositories.profile.PatientRepository;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.security.TokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

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
        User user = createUser(data.getEmail(), data.getPassword(), RoleEnum.PATIENT);
        userRepository.save(user);
        Patient patient = patientMapper.toPatient(data, user);
        patientRepository.save(patient);
    }

    @Transactional
    public void registerPsychologist(PsychologistRegisterRequest data) {
        User user = createUser(data.getEmail(), data.getPassword(), RoleEnum.PSYCHOLOGIST);
        userRepository.save(user);
        Psychologist psychologist = psychologistMapper.toPsychologist(data, user);
        psychologistRepository.save(psychologist);
    }

    private User createUser(String email, String password, RoleEnum role) {
        if (userRepository.existsByEmail(email)) throw new EmailAlreadyExistsException(email);
        User user = userMapper.toUser(email, role);
        user.setPassword(passwordEncoder.encode(password));
        user.setStatus(UserStatusEnum.ACTIVE);
        return user;
    }

    public String login(AuthenticationDTO data) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.email(), data.password());
        var auth = authenticationManager.authenticate(usernamePassword);
        User user = (User) auth.getPrincipal();

        if (!UserStatusEnum.ACTIVE.equals(user.getStatus())) throw new IllegalStateException("Usuário não está ativo");

        return tokenService.generateToken(user);
    }
}