package com.psiconet.infra.exceptions;

import lombok.Getter;

/** Limite de requisições excedido. Respondido como 429 com cabeçalho Retry-After. */
@Getter
public class TooManyRequestsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super("Muitas tentativas em pouco tempo. Aguarde " + humanize(retryAfterSeconds) + " e tente novamente.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public TooManyRequestsException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    private static String humanize(long seconds) {
        if (seconds < 60) return seconds + (seconds == 1 ? " segundo" : " segundos");
        long minutes = (long) Math.ceil(seconds / 60.0);
        return minutes + (minutes == 1 ? " minuto" : " minutos");
    }
}
