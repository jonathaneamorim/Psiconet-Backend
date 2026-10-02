package com.psiconet.services.interfaces.clinical;

import com.psiconet.model.dtos.clinical.record.*;
import com.psiconet.model.entities.access.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/** Prontuário — visão do PSICÓLOGO titular do vínculo. Administradores nunca têm acesso. */
public interface MedicalRecordService {
    List<MedicalRecordSummaryDTO> listMine(User psychologist);
    MedicalRecordDTO get(User psychologist, UUID treatmentLinkId);
    MedicalRecordDTO update(User psychologist, UUID treatmentLinkId, MedicalRecordUpdateDTO dto);

    List<SessionNoteDTO> listNotes(User psychologist, UUID treatmentLinkId);
    SessionNoteDTO createNote(User psychologist, UUID treatmentLinkId, SessionNoteCreateDTO dto);
    SessionNoteDTO getNote(User psychologist, UUID noteId);
    SessionNoteDTO saveDraft(User psychologist, UUID noteId, SessionNoteDraftUpdateDTO dto);
    SessionNoteDTO finalizeNote(User psychologist, UUID noteId);
    SessionNoteDTO addAddendum(User psychologist, UUID noteId, SessionNoteAddendumCreateDTO dto);

    List<AccessRequestDTO> listAccessRequests(User psychologist);
    AccessRequestDTO grant(User psychologist, UUID requestId, AccessGrantDTO dto);
    AccessRequestDTO deny(User psychologist, UUID requestId, AccessDenyDTO dto);

    /**
     * PDF de guarda do psicólogo titular: registro documental + registros finalizados e, se pedido,
     * as anotações pessoais (PRIVATE). Rascunhos nunca entram. Exportação auditada.
     */
    ExportedPdfDTO exportPdf(User psychologist, UUID treatmentLinkId, boolean includePrivateNotes);

    Page<ClinicalAuditEntryDTO> auditTrail(User psychologist, UUID treatmentLinkId, Pageable pageable);
}
