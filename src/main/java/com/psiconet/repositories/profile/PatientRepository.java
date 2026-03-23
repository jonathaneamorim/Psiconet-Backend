package com.psiconet.repositories.profile;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.profile.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {
    Optional<Patient> findByUser(User user);
}