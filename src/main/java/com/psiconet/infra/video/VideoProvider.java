package com.psiconet.infra.video;

import java.time.Instant;
import java.util.Optional;

/**
 * Abstração do provedor de vídeo (SFU/WebRTC). O backend só cria/garante a sala, emite tokens curtos
 * por participante e valida webhooks; a mídia vai direto do navegador ao provedor e NUNCA passa pela
 * Psiconet nem é gravada. Trocar de provedor = nova implementação desta interface (ADR 016).
 */
public interface VideoProvider {

    /** Nome usado em {@code psiconet.video.provider}. */
    String name();

    /** Cria a sala se ainda não existir (idempotente), limitada a {@code maxParticipants}. */
    void ensureRoom(String roomName, int maxParticipants);

    /** Token de entrada com permissões mínimas: publicar/assinar câmera e microfone, nada além. */
    IssuedToken issueToken(String roomName, String identity, int maxParticipants);

    /** Encerra a sala, desconectando todos (sem efeito se ela não existir). */
    void closeRoom(String roomName);

    /**
     * Valida a assinatura e interpreta o webhook. Assinatura inválida ⇒ {@link InvalidWebhookSignatureException};
     * evento sem interesse para a Psiconet ⇒ vazio.
     */
    Optional<VideoWebhookEvent> parseWebhook(byte[] rawBody, String authorizationHeader);

    record IssuedToken(String token, Instant expiresAt) {}
}
