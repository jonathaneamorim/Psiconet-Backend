package com.psiconet.services.interfaces.clinical;

import com.psiconet.model.dtos.clinical.RecurrenceOccurrenceOverlapDTO;
import com.psiconet.model.dtos.clinical.RecurrenceRuleCreateDTO;
import com.psiconet.model.dtos.clinical.RecurrenceRuleDTO;
import com.psiconet.model.entities.access.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface RecurrenceRuleService {
    RecurrenceRuleDTO create(User psychologistUser, UUID treatmentLinkId, RecurrenceRuleCreateDTO dto);
    Page<RecurrenceRuleDTO> list(User user, UUID treatmentLinkId, Pageable pageable);
    void deactivate(User psychologistUser, UUID ruleId);
    List<LocalDateTime> previewOccurrences(User user, UUID ruleId, LocalDate from, LocalDate to);
    /** Ocorrências do rascunho (próximos 90 dias) que coincidem com outra consulta — ADR 019. */
    List<RecurrenceOccurrenceOverlapDTO> draftOverlaps(User psychologistUser, UUID treatmentLinkId, RecurrenceRuleCreateDTO dto);
    /** Datas da série no intervalo que coincidem com outra consulta — só o psicólogo da regra. */
    List<RecurrenceOccurrenceOverlapDTO> previewOverlaps(User psychologistUser, UUID ruleId, LocalDate from, LocalDate to);
}
