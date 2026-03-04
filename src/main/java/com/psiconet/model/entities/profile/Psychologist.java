package com.psiconet.model.entities.profile;

import com.psiconet.model.entities.access.User;
import jakarta.persistence.*;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@Entity
@Table(name = "psicologo")
public class Psychologist {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "usuario_id")
    private User user;

    private String name;

    @Column(name = "nome")
    private String fullName;

    private String email;

    @Column(name = "telefone")
    private String phone;

    private String crp;

    @Column(name = "foto")
    private String photoUrl;

    @Column(name = "ativo")
    private Boolean isActive;

    @Column(name = "tempo_experiencia")
    private Integer experienceTime;

    @Column(name = "descricao")
    private String description;

    @ManyToMany
    @JoinTable(
            name = "especialidade_psicologo",
            joinColumns = @JoinColumn(name = "psicologo_id"),
            inverseJoinColumns = @JoinColumn(name = "especialidades_id")
    )
    private List<Specialty> specialties;
}