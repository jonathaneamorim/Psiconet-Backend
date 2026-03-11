package com.psiconet.repositories.profile;

import com.psiconet.model.entities.profile.Specialty;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {}