package com.psiconet.repositories.access;

import com.psiconet.model.entities.access.ChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChangeLogRepository extends JpaRepository<ChangeLog, Long> {}