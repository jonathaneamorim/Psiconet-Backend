package com.psiconet.repositories.profile;

import com.psiconet.model.entities.profile.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {}