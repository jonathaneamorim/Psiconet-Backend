package com.psiconet.services.implement.profile;

import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.mapper.PsychologistMapper;
import com.psiconet.model.dtos.profile.ConnectionStatusInfoDTO;
import com.psiconet.model.dtos.profile.PsychologistMeDTO;
import com.psiconet.model.dtos.profile.PsychologistProfileDTO;
import com.psiconet.model.dtos.profile.SearchPsychologistDTO;
import com.psiconet.model.dtos.profile.open.PublicPsychologistDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.services.interfaces.profile.ConnectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PsychologistService {

    private final PsychologistRepository psychologistRepository;
    private final ConnectionService connectionService;
    private final PsychologistMapper psychologistMapper;

    @Transactional(readOnly = true)
    public PsychologistMeDTO getMyProfile(User user) {
        Psychologist psychologist = psychologistRepository.findByUser(user)
                .orElseThrow(() -> new EntityNotFoundException("Perfil de psicólogo não encontrado."));
        return psychologistMapper.toMeDto(psychologist);
    }

    @Transactional(readOnly = true)
    public PsychologistProfileDTO getProfile(UUID userId) {
        Psychologist psychologist = psychologistRepository.findByUserId(userId)
                .orElseThrow(() -> new EntityNotFoundException(Psychologist.class, userId));
        return psychologistMapper.toProfileDto(psychologist);
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

    private SearchPsychologistDTO mapToSearchDto(User currentUser, Psychologist p) {
        SearchPsychologistDTO dto = psychologistMapper.toSearchDto(p);
        ConnectionStatusInfoDTO connectionInfo = connectionService.getConnectionStatus(currentUser, p.getUser());
        
        dto.setConnectionId(connectionInfo.getConnectionId());
        dto.setConnectionStatus(connectionInfo.getStatus());
        
        return dto;
    }
}
