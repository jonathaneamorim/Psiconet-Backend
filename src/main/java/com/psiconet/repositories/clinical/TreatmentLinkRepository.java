package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.TreatmentLink;
import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TreatmentLinkRepository extends JpaRepository<TreatmentLink, UUID> {
    Optional<TreatmentLink> findByPatientAndPsychologistAndIsActiveTrue(Patient patient, Psychologist psychologist);
    Optional<TreatmentLink> findByPatientAndPsychologist(Patient patient, Psychologist psychologist);
}