package com.psiconet.services.implement.profile;

import com.psiconet.mapper.PatientMapper;
import com.psiconet.mapper.PsychologistMapper;
import com.psiconet.mapper.UserMapper;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.repositories.profile.PatientRepository;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.services.interfaces.profile.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final PsychologistRepository psychologistRepository;
    private final PsychologistMapper psychologistMapper;
    private final UserMapper userMapper;

    public Object getLoggedUserProfile(User user) {

        if (user.getRole() == RoleEnum.PATIENT) {
            Patient patient = patientRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Paciente não encontrado"));
            return patientMapper.toDto(patient);
        }

        if (user.getRole() == RoleEnum.PSYCHOLOGIST) {
            Psychologist psychologist = psychologistRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Psicólogo não encontrado"));
            return psychologistMapper.toDto(psychologist);
        }

        return userMapper.toDto(user);
    }
}
