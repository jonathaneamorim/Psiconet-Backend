package com.psiconet.services.implement.clinical;

import com.psiconet.infra.exceptions.BusinessException;
import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.clinical.MeetingTypeEnum;

public final class MeetingFieldsValidator {

    private MeetingFieldsValidator() {}

    public static void validate(MeetingTypeEnum meetingType, String meetingLink, Location location) {
        if (meetingType == MeetingTypeEnum.VIDEO_CALL && (meetingLink == null || meetingLink.isBlank())) {
            throw new BusinessException("meetingLink", "Informe o link da chamada de vídeo.");
        }

        if (meetingType == MeetingTypeEnum.IN_PERSON && location == null) {
            throw new BusinessException("location", "Informe o endereço da consulta presencial.");
        }
    }
}
