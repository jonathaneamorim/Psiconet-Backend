package com.psiconet.model.entities.profile;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.embeddable.Location;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "psicologo")
@Data
@NoArgsConstructor
@AllArgsConstructor
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

    private Location location;

    @ManyToMany
    @JoinTable(
            name = "especialidade_psicologo",
            joinColumns = @JoinColumn(name = "psicologo_id"),
            inverseJoinColumns = @JoinColumn(name = "especialidade_id")
    )
    private List<Specialty> specialties;
}