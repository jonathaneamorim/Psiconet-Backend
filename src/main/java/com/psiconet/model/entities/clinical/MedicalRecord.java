package com.psiconet.model.entities.clinical;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Prontuário psicológico: UM por vínculo paciente–psicólogo (TreatmentLink).
 * Todo conteúdo clínico fica nos campos {@code *_cifrado} (AES-256-GCM com chave de dados própria,
 * protegida pela chave-mestra — ADR 008). As colunas de texto puro legadas são mantidas apenas para
 * migração (ficam nulas depois dela) e NUNCA devem voltar a ser preenchidas.
 * Sem exclusão física (ver SchemaPatches: trigger bloqueia DELETE).
 */
@Getter
@Setter
@Entity
@Table(name = "prontuario")
public class MedicalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "paciente_psicologo_id")
    private TreatmentLink treatmentLink;

    @Column(name = "data_abertura")
    private LocalDate openingDate;

    // ---- Legado (texto puro). Somente leitura para migração; manter nulo. ----
    @Column(name = "queixa_principal")
    private String mainComplaint;

    @Column(name = "historico_familiar")
    private String familyHistory;

    @Column(name = "antecedentes_medicos")
    private String medicalHistory;

    @Column(name = "objetivos_terapeuticos")
    private String therapeuticGoals;

    // ---- Envelope encryption ----
    @Column(name = "chave_dados_cifrada", length = 200)
    private String wrappedDataKey;

    @Column(name = "versao_chave_mestra", length = 20)
    private String keyVersion;

    // ---- Conteúdo cifrado (registro documental) ----
    @Column(name = "demanda_cifrada", columnDefinition = "TEXT")
    private String demandEncrypted;

    @Column(name = "objetivos_cifrado", columnDefinition = "TEXT")
    private String goalsEncrypted;

    @Column(name = "historico_cifrado", columnDefinition = "TEXT")
    private String historyEncrypted;

    @Column(name = "procedimentos_cifrado", columnDefinition = "TEXT")
    private String proceduresEncrypted;

    @Column(name = "encerramento_cifrado", columnDefinition = "TEXT")
    private String closingEncrypted;

    @CreationTimestamp
    @Column(name = "criado_em", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private LocalDateTime updatedAt;

    @Version
    private Long version;
}
