package com.psiconet.infra.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Regras antiabuso de chamados (psiconet.tickets.*). */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "psiconet.tickets")
public class TicketProperties {
    /** Máximo de chamados OPEN/IN_PROGRESS por usuário ao mesmo tempo. */
    private int maxActivePerUser = 3;
    /** Intervalo mínimo entre aberturas de chamado pelo mesmo usuário. */
    private Duration minIntervalBetweenOpenings = Duration.ofMinutes(2);
    /** Janela em que o mesmo assunto+descrição (ou a mesma mensagem) é considerado duplicado. */
    private Duration duplicateWindow = Duration.ofHours(24);
    private Duration duplicateMessageWindow = Duration.ofMinutes(10);
}
