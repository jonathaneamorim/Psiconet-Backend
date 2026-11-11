package com.psiconet.services.interfaces.legal;

import com.psiconet.model.dtos.legal.ConsentAcceptRequestDTO;
import com.psiconet.model.dtos.legal.ConsentRecordDTO;
import com.psiconet.model.dtos.legal.ConsentStatusDTO;
import com.psiconet.model.entities.access.User;

import java.util.List;

public interface ConsentService {

    /** Lança {@code LegalVersionOutdatedException} se as versões vistas pelo usuário não forem as vigentes. */
    void requireCurrentVersions(String termsVersion, String privacyVersion);

    /** Registra os aceites do cadastro (mesma transação do cadastro). O usuário já deve estar persistido. */
    void recordRegistration(User user, boolean healthDataAccepted);

    /** Itens pendentes de (re)aceite, sem consulta ao banco (usa as versões gravadas no usuário). */
    List<String> pendingFor(User user);

    default boolean isConsentRequired(User user) {
        return !pendingFor(user).isEmpty();
    }

    ConsentStatusDTO status(User user);

    ConsentStatusDTO accept(User user, ConsentAcceptRequestDTO request);

    List<ConsentRecordDTO> history(User user);
}
