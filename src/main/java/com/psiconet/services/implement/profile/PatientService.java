package com.psiconet.services.implement.profile;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.storage.FileStorageService;
import com.psiconet.mapper.PatientMapper;
import com.psiconet.model.dtos.profile.ConnectionStatusInfoDTO;
import com.psiconet.model.dtos.profile.PatientMeDTO;
import com.psiconet.model.dtos.profile.PatientProfileDTO;
import com.psiconet.model.dtos.profile.PatientProfileUpdateDTO;
import com.psiconet.model.dtos.profile.SearchPatientDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.profile.PatientRepository;
import com.psiconet.services.interfaces.profile.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PatientService {

    private static final long MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024;

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ConnectionService connectionService;
    private final PatientMapper patientMapper;
    private final FileStorageService fileStorageService;

    @Value("${psiconet.backend-url}")
    private String backendUrl;

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

    @Transactional
    public PatientMeDTO updateProfile(User user, PatientProfileUpdateDTO dto) {
        Patient patient = patientRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Perfil de paciente não encontrado."));

        user.setFullName(dto.fullName());
        user.setPhone(dto.phone());
        userRepository.save(user);

        return patientMapper.toMeDto(patient);
    }

    @Transactional
    public PatientMeDTO updatePhoto(User user, MultipartFile file) {
        Patient patient = patientRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Perfil de paciente não encontrado."));

        String contentType = file != null ? FileStorageService.normalizeContentType(file.getContentType()) : null;
        if (!"image/png".equals(contentType) && !"image/jpeg".equals(contentType)) {
            throw new BusinessException("file", "A foto de perfil deve ser PNG ou JPG.");
        }

        // O front já limita a 5MB, mas isso é só UX — a checagem que importa é esta aqui, já que a
        // requisição pode ser feita diretamente na API sem passar pelo formulário.
        if (file.getSize() > MAX_PHOTO_SIZE_BYTES) {
            throw new BusinessException("file", "A foto de perfil deve ter no máximo 5MB.");
        }

        String previousPhotoUrl = user.getPhotoUrl();

        String path = fileStorageService.store(file, "avatars");
        String fileName = path.substring(path.lastIndexOf('/') + 1);

        user.setPhotoUrl(backendUrl + "/public/photos/" + fileName);
        userRepository.save(user);

        // Remove a foto antiga só depois que a nova já foi salva com sucesso — sem isso, cada troca
        // de foto deixa um arquivo órfão para trás e o armazenamento cresce indefinidamente.
        deleteAvatarIfOwned(previousPhotoUrl);

        return patientMapper.toMeDto(patient);
    }

    // Só apaga arquivos dentro de "avatars/" e cujo nome seja exatamente o que o próprio backend
    // gerou (UUID + extensão) — nunca confia livremente em uma URL para decidir o que remover do disco.
    private void deleteAvatarIfOwned(String photoUrl) {
        if (photoUrl == null || !photoUrl.startsWith(backendUrl + "/public/photos/")) {
            return;
        }

        String fileName = photoUrl.substring((backendUrl + "/public/photos/").length());
        if (fileName.isBlank() || fileName.contains("/") || fileName.contains("..")) {
            return;
        }

        fileStorageService.delete("avatars/" + fileName);
    }

    private SearchPatientDTO mapToSearchDto(User currentUser, Patient p) {
        SearchPatientDTO dto = patientMapper.toSearchDto(p);
        ConnectionStatusInfoDTO connectionInfo = connectionService.getConnectionStatus(currentUser, p.getUser());

        dto.setConnectionId(connectionInfo.getConnectionId());
        dto.setConnectionStatus(connectionInfo.getStatus());

        return dto;
    }
}
