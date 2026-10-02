package com.psiconet.model.enums.support;

public enum TicketStatusEnum {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    /** Chamados que contam no limite de abertos simultâneos por usuário. */
    public boolean isActive() {
        return this == OPEN || this == IN_PROGRESS;
    }
}
