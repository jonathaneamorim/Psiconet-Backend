package com.psiconet.model.entities.profile;

import com.psiconet.model.entities.access.User;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "paciente")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "usuario_id")
    private User user;

    private String name;
    private String email;

    @Column(name = "telefone")
    private String phone;

    @Column(name = "data_nascimento")
    private LocalDate birthDate;

    @Column(name = "foto")
    private String photoUrl;

    @Column(name = "ativo")
    private Boolean isActive;
}