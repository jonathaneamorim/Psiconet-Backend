package com.psiconet.services.implement.profile;

import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.mapper.PatientMapper;
import com.psiconet.model.dtos.profile.ConnectionStatusInfoDTO;
import com.psiconet.model.dtos.profile.PatientMeDTO;
import com.psiconet.model.dtos.profile.PatientProfileDTO;
import com.psiconet.model.dtos.profile.SearchPatientDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.repositories.profile.PatientRepository;
import com.psiconet.services.interfaces.profile.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final ConnectionService connectionService;
    private final PatientMapper patientMapper;

    @Transactional(readOnly = true)
    public PatientMeDTO getMyProfile(User user) {
        Patient patient = patientRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Perfil de paciente não encontrado."));
        return patientMapper.toMeDto(patient);
    }

    @Transactional(readOnly = true)
    public PatientProfileDTO getProfile(User currentUser, UUID userId) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException(Patient.class, userId));
        
        PatientProfileDTO dto = patientMapper.toProfileDto(patient);
        
        ConnectionStatusInfoDTO connectionInfo = connectionService.getConnectionStatus(currentUser, patient.getUser());
        dto.setConnectionStatus(connectionInfo.getStatus());
        dto.setConnectionId(connectionInfo.getConnectionId());
        
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<SearchPatientDTO> search(User currentUser, String name, Pageable pageable) {
        Page<Patient> patients = patientRepository.findByUser_FullNameContainingIgnoreCase(name, pageable);
        return patients.map(p -> mapToSearchDto(currentUser, p));
    }

    private SearchPatientDTO mapToSearchDto(User currentUser, Patient p) {
        SearchPatientDTO dto = patientMapper.toSearchDto(p);
        ConnectionStatusInfoDTO connectionInfo = connectionService.getConnectionStatus(currentUser, p.getUser());

        dto.setConnectionId(connectionInfo.getConnectionId());
        dto.setConnectionStatus(connectionInfo.getStatus());

        return dto;
    }
}
