package com.psiconet.model.entities.profile;

import com.psiconet.model.entities.access.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "psicologo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Psychologist {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private User user;

    @Column(unique = true, nullable = false)
    private String crp;

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