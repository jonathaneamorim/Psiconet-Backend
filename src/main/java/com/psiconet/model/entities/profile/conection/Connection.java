package com.psiconet.model.entities.profile.conection;

import com.psiconet.model.entities.profile.Patient;
import com.psiconet.model.entities.profile.Psychologist;
import com.psiconet.model.enums.conection.ConectionStatusEnum;
import com.psiconet.model.enums.conection.UserTypeConnectionEnum;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
public class Connection {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    private Psychologist psychologist;

    @ManyToOne
    private Patient patient;

    @Enumerated(EnumType.STRING)
    private ConectionStatusEnum status;

    @Enumerated(EnumType.STRING)
    private UserTypeConnectionEnum requestedBy;

    @CreationTimestamp
    private LocalDateTime requestedAt;
}
