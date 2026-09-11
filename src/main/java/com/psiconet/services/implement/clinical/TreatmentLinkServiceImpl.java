package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.infra.exceptions.EntityNotFoundException;
import com.psiconet.model.dtos.clinical.TreatmentLinkPriceUpdateDTO;
import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.enums.RoleEnum;
import com.psiconet.repositories.clinical.TreatmentLinkRepository;
import com.psiconet.services.interfaces.clinical.TreatmentLinkService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TreatmentLinkServiceImpl implements TreatmentLinkService {

    private final TreatmentLinkRepository treatmentLinkRepository;

    @Override
    @Transactional
    public void updateDefaultPrice(User psychologistUser, UUID treatmentLinkId, TreatmentLinkPriceUpdateDTO dto) {
        if (psychologistUser.getRole() != RoleEnum.PSYCHOLOGIST) {
            throw new BusinessException("treatmentLink", "Somente psicólogos podem definir o preço da consulta.");
        }

        TreatmentLink treatmentLink = treatmentLinkRepository.findById(treatmentLinkId)
                .orElseThrow(() -> new EntityNotFoundException(TreatmentLink.class, treatmentLinkId));

        if (!treatmentLink.getPsychologist().getUser().getId().equals(psychologistUser.getId())) {
            throw new BusinessException("treatmentLink", "Você não tem permissão para alterar este vínculo.");
        }

        treatmentLink.setDefaultPrice(dto.getDefaultPrice());
        treatmentLinkRepository.save(treatmentLink);
    }
}
