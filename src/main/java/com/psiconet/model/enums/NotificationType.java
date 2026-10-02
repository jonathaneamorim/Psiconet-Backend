package com.psiconet.model.enums;

public enum NotificationType {
    APPOINTMENT_SCHEDULED,
    APPOINTMENT_CANCELLED,
    CONNECTION_REQUESTED,
    CONNECTION_ACCEPTED,
    PAYMENT_PENDING,
    PAYMENT_RECEIPT_SUBMITTED,
    PAYMENT_APPROVED,
    PAYMENT_REJECTED,
    PAYMENT_DISPUTED,
    // Suporte e comunicados — obrigatórias (não desativáveis), ver ADR 005
    TICKET_REPLIED,
    TICKET_CLOSED,
    ADMIN_ANNOUNCEMENT,
    // Prontuário — obrigatórias (prazo de resposta a direito do titular, LGPD art. 18)
    MEDICAL_RECORD_ACCESS_REQUESTED,
    MEDICAL_RECORD_ACCESS_GRANTED,
    MEDICAL_RECORD_ACCESS_DENIED,
    // Nova versão de Termos/Privacidade — obrigatória (ADR 013)
    LEGAL_DOCUMENTS_UPDATED;

    private static final java.util.Set<NotificationType> MANDATORY = java.util.EnumSet.of(
            TICKET_REPLIED, TICKET_CLOSED, ADMIN_ANNOUNCEMENT,
            MEDICAL_RECORD_ACCESS_REQUESTED, MEDICAL_RECORD_ACCESS_GRANTED, MEDICAL_RECORD_ACCESS_DENIED,
            LEGAL_DOCUMENTS_UPDATED
    );

    /** Notificações que ignoram as preferências do usuário. */
    public boolean isMandatory() {
        return MANDATORY.contains(this);
    }
}
