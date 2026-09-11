package com.psiconet.model.entities.profile;

import com.psiconet.model.entities.access.User;
import com.psiconet.model.entities.embeddable.Location;
import com.psiconet.model.enums.financial.PaymentAdvanceUnitEnum;
import com.psiconet.model.enums.financial.PaymentTimingEnum;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "psicologo")
@Getter
@Setter
@ToString
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
    @org.hibernate.annotations.BatchSize(size = 20)
    @JoinTable(
            name = "especialidade_psicologo",
            joinColumns = @JoinColumn(name = "psicologo_id"),
            inverseJoinColumns = @JoinColumn(name = "especialidade_id")
    )
    private List<Specialty> specialties;

    @Column(name = "chave_pix")
    private String pixKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "momento_cobranca")
    private PaymentTimingEnum paymentTiming;

    @Column(name = "antecedencia_cobranca_valor")
    private Integer paymentAdvanceValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "antecedencia_cobranca_unidade")
    private PaymentAdvanceUnitEnum paymentAdvanceUnit;

    // Endereço fixo do consultório, opcional — usado como sugestão pré-preenchida ao agendar
    // uma consulta presencial, sem impedir que o psicólogo informe um endereço avulso diferente.
    @Embedded
    private Location officeAddress;
}