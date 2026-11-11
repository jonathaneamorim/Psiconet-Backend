package com.psiconet.infra.video;

import java.time.Instant;

/** Evento de webhook já validado e normalizado (independente do provedor). */
public record VideoWebhookEvent(
        String eventId,
        Type type,
        String roomName,
        String participantIdentity,
        Instant occurredAt
) {
    public enum Type { PARTICIPANT_JOINED, PARTICIPANT_LEFT, ROOM_FINISHED }
}
