package com.psiconet.infra.video;

/** Webhook sem assinatura válida do provedor de vídeo. Respondido como 401, sem detalhes. */
public class InvalidWebhookSignatureException extends RuntimeException {
    public InvalidWebhookSignatureException(String message) {
        super(message);
    }
}
