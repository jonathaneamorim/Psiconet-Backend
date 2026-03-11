package com.psiconet.repositories.profile;

import com.psiconet.model.entities.profile.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {}