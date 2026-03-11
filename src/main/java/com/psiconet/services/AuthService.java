package com.psiconet.services;

import com.psiconet.model.dtos.auth.AuthenticationDTO;
import com.psiconet.model.dtos.auth.RegisterPatientDTO;
import com.psiconet.model.dtos.auth.RegisterPsychologistDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.RoleEnum;
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

    @Transactional
    public void registerPatient(RegisterPatientDTO data) {
        User user = createUser(data.getEmail(), data.getPassword(), RoleEnum.PACIENTE);

        Patient patient = new Patient();
        patient.setUser(user);
        patient.setEmail(data.getEmail());
        patient.setCpf(data.getCpf());
        patient.setBirthDate(data.getBirthDate());
        patient.setIsActive(true);

        patientRepository.save(patient);
    }

    @Transactional
    public void registerPsychologist(RegisterPsychologistDTO data) {
        User user = createUser(data.getEmail(), data.getPassword(), RoleEnum.PSICOLOGO);

        Psychologist psych = new Psychologist();
        psych.setUser(user);
        psych.setEmail(data.getEmail());
        psych.setCpf(data.getCpf());
        psych.setCrp(data.getCrp());
        psych.setBirthDate(data.getBirthDate());
        psych.setIsActive(true);

        psychologistRepository.save(psych);
    }

    private User createUser(String email, String password, RoleEnum role) {
        if (userRepository.findByUsername(email) != null) {
            throw new RuntimeException("E-mail já cadastrado!");
        }
        User user = new User();
        user.setUsername(email); // O e-mail será o login
        user.setPassword(passwordEncoder.encode(password));
        user.setAccessRoleEnum(role);
        return userRepository.save(user);
    }

    public String login(AuthenticationDTO data) {
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.email(), data.password());
        var auth = authenticationManager.authenticate(usernamePassword);

        User user = (User) Objects.requireNonNull(auth.getPrincipal());

        return tokenService.generateToken(user);
    }
}