package com.psiconet.model.entities.profile;

import com.psiconet.model.entities.access.User;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "psicologo")
@Data
public class Psychologist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private User user;

    @Column(name = "nome")
    private String fullName;

    @Column(name = "telefone")
    private String phone;

    @Column(unique = true, nullable = false)
    private String cpf;

    @Column(unique = true, nullable = false)
    private String crp;

    @Column(name = "foto")
    private String photoUrl;

    @Column(name = "data_nascimento")
    private LocalDate birthDate;

    @Column(name = "tempo_experiencia")
    private Integer experienceTime;

    @Column(name = "descricao")
    private String description;

    @ManyToMany
    @JoinTable(
            name = "especialidade_psicologo",
            joinColumns = @JoinColumn(name = "psicologo_id"),
            inverseJoinColumns = @JoinColumn(name = "especialidade_id")
    )
    private List<Specialty> specialties;
}