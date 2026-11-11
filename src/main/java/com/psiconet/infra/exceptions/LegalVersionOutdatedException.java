package com.psiconet.infra.exceptions;

/**
 * O usuário aceitou uma versão de documento legal que não é mais a vigente (a página ficou aberta
 * enquanto uma nova versão foi publicada). Respondido como 409 com code {@code LEGAL_VERSION_OUTDATED}:
 * o frontend recarrega as versões e pede o aceite de novo.
 */
public class LegalVersionOutdatedException extends RuntimeException {
    public LegalVersionOutdatedException() {
        super("Os Termos de Uso ou a Política de Privacidade foram atualizados. Leia a nova versão e confirme o aceite novamente.");
    }
}
