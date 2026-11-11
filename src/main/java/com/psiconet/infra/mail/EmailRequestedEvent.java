package com.psiconet.infra.mail;

/** Evento publicado dentro da transação de negócio; o envio só acontece depois do commit. */
public record EmailRequestedEvent(EmailMessage message) {}
