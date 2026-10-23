package com.psiconet.infra.video;

import lombok.Getter;

/**
 * Entrada na sala recusada por regra de negócio (janela, situação da consulta, provedor). Carrega o
 * status HTTP e um {@code code} estável para o frontend escolher a mensagem/estado da tela.
 */
@Getter
public class VideoAccessException extends RuntimeException {

    public static final String NOT_IN_APP = "VIDEO_NOT_IN_APP";
    public static final String APPOINTMENT_INACTIVE = "VIDEO_APPOINTMENT_INACTIVE";
    public static final String WINDOW_NOT_OPEN = "VIDEO_WINDOW_NOT_OPEN";
    public static final String WINDOW_CLOSED = "VIDEO_WINDOW_CLOSED";
    public static final String UNAVAILABLE = "VIDEO_UNAVAILABLE";

    private final int status;
    private final String code;

    public VideoAccessException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static VideoAccessException unavailable() {
        return new VideoAccessException(503, UNAVAILABLE,
                "A videochamada integrada está indisponível no momento. Tente novamente em instantes.");
    }
}
