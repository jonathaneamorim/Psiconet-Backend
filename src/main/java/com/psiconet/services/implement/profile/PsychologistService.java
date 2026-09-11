package com.psiconet.services.implement.profile;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.infra.storage.FileStorageService;
import com.psiconet.mapper.PsychologistMapper;
import com.psiconet.model.dtos.profile.BillingSettingsUpdateDTO;
import com.psiconet.model.dtos.profile.ConnectionStatusInfoDTO;
import com.psiconet.model.dtos.profile.ProfileUpdateDTO;
import com.psiconet.model.dtos.profile.PsychologistMeDTO;
import com.psiconet.model.dtos.profile.PsychologistProfileDTO;
import com.psiconet.model.dtos.profile.SearchPsychologistDTO;
import com.psiconet.model.dtos.profile.open.PublicPsychologistDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.financial.PaymentTimingEnum;
import com.psiconet.repositories.access.UserRepository;
import com.psiconet.repositories.profile.PsychologistRepository;
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
public class PsychologistService {

    private static final long MAX_PHOTO_SIZE_BYTES = 5L * 1024 * 1024;

    private final PsychologistRepository psychologistRepository;
    private final UserRepository userRepository;
    private final ConnectionService connectionService;
    private final PsychologistMapper psychologistMapper;
    private final FileStorageService fileStorageService;

    @Value("${psiconet.backend-url}")
    private String backendUrl;

    @Transactional(readOnly = true)
    public PsychologistMeDTO getMyProfile(User user) {
        Psychologist psychologist = psychologistRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Perfil de psicólogo não encontrado."));
        return psychologistMapper.toMeDto(psychologist);
    }

    @Transactional(readOnly = true)
    public PsychologistProfileDTO getProfile(User currentUser, UUID userId) {
        Psychologist psychologist = psychologistRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException(Psychologist.class, userId));
        
        PsychologistProfileDTO dto = psychologistMapper.toProfileDto(psychologist);
        
        ConnectionStatusInfoDTO connectionInfo = connectionService.getConnectionStatus(currentUser, psychologist.getUser());
        dto.setConnectionStatus(connectionInfo.getStatus());
        dto.setConnectionId(connectionInfo.getConnectionId());
        
        return dto;
    }

    @Transactional(readOnly = true)
    public Page<SearchPsychologistDTO> search(User currentUser, String name, Pageable pageable) {
        Page<Psychologist> psychologists = psychologistRepository.findByUser_FullNameContainingIgnoreCase(name, pageable);
        return psychologists.map(p -> mapToSearchDto(currentUser, p));
    }

    @Transactional(readOnly = true)
    public SearchPsychologistDTO searchByCrpWithStatus(User currentUser, String crp) {
        Psychologist psychologist = psychologistRepository.findByCrp(crp)
                .orElseThrow(() -> new EntityNotFoundException(Psychologist.class, crp));
        return mapToSearchDto(currentUser, psychologist);
    }

    @Transactional(readOnly = true)
    public Page<PublicPsychologistDTO> searchPublic(String name, Pageable pageable) {
        Page<Psychologist> psychologists = psychologistRepository.findByUser_FullNameContainingIgnoreCase(name, pageable);
        return psychologists.map(psychologistMapper::toPsychologistDto);
    }

    @Transactional(readOnly = true)
    public PublicPsychologistDTO getPublicProfileByCrp(String crp) {
        Psychologist psychologist = psychologistRepository.findByCrp(crp)
                .orElseThrow(() -> new EntityNotFoundException(Psychologist.class, crp));
        return psychologistMapper.toPsychologistDto(psychologist);
    }

    @Transactional
    public void updateBillingSettings(User user, BillingSettingsUpdateDTO dto) {
        Psychologist psychologist = psychologistRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Perfil de psicólogo não encontrado."));

        if (dto.getPaymentTiming() == PaymentTimingEnum.BEFORE_APPOINTMENT
                && (dto.getPaymentAdvanceValue() == null || dto.getPaymentAdvanceValue() <= 0 || dto.getPaymentAdvanceUnit() == null)) {
            throw new BusinessException("paymentAdvanceValue", "Informe a antecedência de cobrança (valor e unidade).");
        }

        psychologist.setPixKey(dto.getPixKey());
        psychologist.setPaymentTiming(dto.getPaymentTiming());
        psychologist.setPaymentAdvanceValue(dto.getPaymentAdvanceValue());
        psychologist.setPaymentAdvanceUnit(dto.getPaymentAdvanceUnit());
        psychologistRepository.save(psychologist);
    }

    @Transactional
    public PsychologistMeDTO updateProfile(User user, ProfileUpdateDTO dto) {
        Psychologist psychologist = psychologistRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Perfil de psicólogo não encontrado."));

        user.setFullName(dto.fullName());
        user.setPhone(dto.phone());
        userRepository.save(user);

        psychologist.setDescription(dto.description());
        psychologist.setOfficeAddress(dto.officeAddress());
        psychologist = psychologistRepository.save(psychologist);

        return psychologistMapper.toMeDto(psychologist);
    }

    @Transactional
    public PsychologistMeDTO updatePhoto(User user, MultipartFile file) {
        Psychologist psychologist = psychologistRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Perfil de psicólogo não encontrado."));

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

        return psychologistMapper.toMeDto(psychologist);
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

    private SearchPsychologistDTO mapToSearchDto(User currentUser, Psychologist p) {
        SearchPsychologistDTO dto = psychologistMapper.toSearchDto(p);
        ConnectionStatusInfoDTO connectionInfo = connectionService.getConnectionStatus(currentUser, p.getUser());
        
        dto.setConnectionId(connectionInfo.getConnectionId());
        dto.setConnectionStatus(connectionInfo.getStatus());
        
        return dto;
    }
}
