package com.psiconet.model.entities.profile;

import com.psiconet.model.entities.access.User;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "paciente")
@Data
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private User user;

    @Column(name = "nome")
    private String fullName;

    @Column(unique = true, nullable = false)
    private String cpf;

    @Column(name = "telefone")
    private String phone;

    @Column(name = "data_nascimento")
    private LocalDate birthDate;

    @Column(name = "foto")
    private String photoUrl;
}