package com.psiconet.services.interfaces.video;

import com.psiconet.model.dtos.video.VideoConfigDTO;
import com.psiconet.model.dtos.video.VideoJoinResponseDTO;
import com.psiconet.model.entities.access.User;

import java.util.UUID;

public interface VideoSessionService {

    VideoConfigDTO getConfig();

    /** Valida participação, modalidade, situação e janela; garante a sala e emite a credencial. */
    VideoJoinResponseDTO join(User user, UUID appointmentId);

    /** Psicólogo encerra a sessão para todos. */
    void end(User user, UUID appointmentId);

    /** Webhook do provedor (assinatura validada pelo provedor); só registra presença. */
    void handleWebhook(byte[] rawBody, String authorizationHeader);
}
