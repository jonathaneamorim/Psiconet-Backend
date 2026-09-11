package com.psiconet.services.interfaces.clinical;

import com.psiconet.model.dtos.clinical.TreatmentLinkPriceUpdateDTO;
import com.psiconet.model.entities.access.User;

import java.util.UUID;

public interface TreatmentLinkService {
    void updateDefaultPrice(User psychologistUser, UUID treatmentLinkId, TreatmentLinkPriceUpdateDTO dto);
}
