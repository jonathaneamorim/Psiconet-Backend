package com.psiconet.model.enums.access;

/** Motivo do encerramento de uma sessão (dispositivo). */
public enum SessionRevocationReasonEnum {
    /** "Sair da conta" neste dispositivo. */
    LOGOUT,
    /** "Sair de todos os dispositivos". */
    LOGOUT_ALL,
    /** Troca de senha pelo próprio usuário (a sessão atual é recriada). */
    PASSWORD_CHANGED,
    /** Refresh token já rotacionado foi reapresentado: possível roubo — a sessão inteira cai. */
    REUSE_DETECTED,
    /** Ação administrativa (inativação, bloqueio, troca de e-mail). */
    ADMIN
}
