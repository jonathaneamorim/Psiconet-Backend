package com.psiconet.model.enums.clinical;

public enum VideoSessionEventEnum {
    /** Credencial de entrada emitida pela API (o usuário pediu para entrar). */
    TOKEN_ISSUED,
    /** Webhook do provedor: participante conectou à sala. */
    PARTICIPANT_JOINED,
    /** Webhook do provedor: participante saiu ou perdeu a conexão. */
    PARTICIPANT_LEFT,
    /** Webhook do provedor: sala encerrada (vazia ou fechada). */
    ROOM_FINISHED,
    /** O psicólogo encerrou a sessão para todos. */
    ROOM_CLOSED_BY_HOST
}
