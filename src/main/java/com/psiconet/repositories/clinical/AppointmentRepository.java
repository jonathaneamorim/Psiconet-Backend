package com.psiconet.repositories.clinical;

import com.psiconet.model.entities.clinical.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {}