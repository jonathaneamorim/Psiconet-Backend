package com.psiconet.services.implement.profile;

import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.mapper.PsychologistMapper;
import com.psiconet.model.dtos.profile.open.PublicPsychologistDTO;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.repositories.profile.PsychologistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PsychologistService {

    private final PsychologistRepository psychologistRepository;
    private final PsychologistMapper psychologistMapper;

    public List<PublicPsychologistDTO> searchByName(String name) {

        List<Psychologist> psychologists =
                psychologistRepository
                        .findByUser_FullNameContainingIgnoreCase(name);

        if (psychologists.isEmpty()) {

            throw new EntityNotFoundException(
                    Psychologist.class,
                    name
            );
        }

        return psychologists
                .stream()
                .map(psychologistMapper::toPsychologistDto)
                .toList();
    }

    public PublicPsychologistDTO searchByCrp(String crp) {

        Psychologist psychologist =
                psychologistRepository
                        .findByCrp(crp)
                        .orElseThrow(() ->
                                new EntityNotFoundException(
                                        Psychologist.class,
                                        crp
                                )
                        );

        return psychologistMapper.toPsychologistDto(
                psychologist
        );
    }
}