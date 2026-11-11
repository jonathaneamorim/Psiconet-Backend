package com.psiconet.model.enums.clinical;

/** Camadas do registro (ADR 009). */
public enum SessionNoteTypeEnum {
    /** Registro documental de evolução: integra o prontuário e pode ser liberado ao paciente. */
    RECORD,
    /** Anotação pessoal de trabalho do psicólogo: nunca entregue automaticamente ao paciente. */
    PRIVATE
}
