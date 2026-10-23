package com.psiconet.infra.mail;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Catálogo de e-mails transacionais. Cada item aponta para um template HTML em
 * {@code templates/email/<nome>.html} e para a versão texto em {@code templates/email/text/<nome>.txt}.
 * <p>
 * Regra de ouro: nenhum template pode receber conteúdo clínico (prontuário, anotações, motivos de
 * negativa) — só avisos com link para a plataforma, onde o acesso é autenticado e auditado.
 */
@Getter
@RequiredArgsConstructor
public enum EmailTemplate {
    PASSWORD_RESET("recuperacao-senha", "Redefinição de senha — Psiconet"),
    WELCOME("boas-vindas", "Bem-vindo(a) à Psiconet"),
    ADMIN_INVITE("convite-administrador", "Você foi convidado(a) como administrador(a) da Psiconet"),
    TICKET_REPLIED("chamado-respondido", "Seu chamado foi respondido — Psiconet"),
    TICKET_CLOSED("chamado-finalizado", "Seu chamado foi finalizado — Psiconet"),
    ADMIN_ANNOUNCEMENT("comunicado", "Comunicado da Psiconet"),
    RECORD_ACCESS_REQUESTED("prontuario-solicitado", "Nova solicitação de acesso a prontuário — Psiconet"),
    RECORD_ACCESS_DECIDED("prontuario-respondido", "Sua solicitação de acesso ao prontuário foi respondida — Psiconet"),
    LEGAL_DOCUMENTS_UPDATED("documentos-atualizados", "Atualizamos nossos Termos e Política de Privacidade — Psiconet"),
    VIDEO_SESSION_REMINDER("lembrete-sessao-video", "Sua sessão online começa em breve — Psiconet");

    private final String templateName;
    private final String defaultSubject;
}
