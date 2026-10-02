package com.psiconet.model.enums.clinical;

public enum SessionNoteStatusEnum {
    /** Rascunho: editável (autosave) apenas pelo autor. Não integra o prontuário. */
    DRAFT,
    /** Finalizado: imutável, com hash de integridade. Correções só por adendo. */
    FINALIZED
}
