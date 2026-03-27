package com.psiconet.services.implement.profile;

import com.psiconet.mapper.PsychologistMapper;
import com.psiconet.model.dtos.profile.open.PublicPsychologistDTO;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.repositories.profile.PsychologistRepository;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PsychologistService {
    private final PsychologistRepository psychologistRepository;
    private final PsychologistMapper psychologistMapper;

    public PublicPsychologistDTO searchByName(String name) {
        Optional<Psychologist> psychologist = psychologistRepository.findByFullNameContainingIgnoreCase(name);
        if(psychologist.isEmpty()) throw new EntityNotFoundException(Psychologist.class, name);
        return psychologistMapper.toPsychologistDto(psychologist.get());
    }

    public PublicPsychologistDTO searchByCrp(String crp) {
        Optional<Psychologist> psychologist = psychologistRepository.findByCrp(crp);
        if(psychologist.isEmpty()) throw new EntityNotFoundException(Psychologist.class, crp);
        return psychologistMapper.toPsychologistDto(psychologist.get());
    }
}
