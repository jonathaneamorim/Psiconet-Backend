package com.psiconet.services.interfaces.clinical;

import com.psiconet.model.dtos.clinical.record.AccessRequestDTO;
import com.psiconet.model.dtos.clinical.record.ExportedPdfDTO;
import com.psiconet.model.dtos.clinical.record.PatientRecordLinkDTO;
import com.psiconet.model.dtos.clinical.record.PatientRecordViewDTO;
import com.psiconet.model.entities.access.User;

import java.util.List;
import java.util.UUID;

/** Prontuário — visão do PACIENTE: solicita acesso e lê (somente leitura) o que foi liberado. */
public interface PatientMedicalRecordService {
    List<PatientRecordLinkDTO> listMyLinks(User patient);
    AccessRequestDTO requestAccess(User patient, UUID treatmentLinkId);
    PatientRecordViewDTO viewGranted(User patient, UUID accessRequestId);
    ExportedPdfDTO exportPdf(User patient, UUID accessRequestId);
}
