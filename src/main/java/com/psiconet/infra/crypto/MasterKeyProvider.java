package com.psiconet.infra.crypto;

import javax.crypto.SecretKey;
import java.util.Optional;

/**
 * Fonte das chaves-mestras (KEK) que protegem as chaves de dados dos prontuários.
 * A implementação atual lê de variável de ambiente; a interface foi desenhada para trocar por um
 * KMS gerenciado (AWS KMS, GCP KMS, Vault Transit) sem alterar o restante do código — nesse caso
 * wrap/unwrap passam a ser chamadas ao KMS e a chave nunca entra na memória da aplicação.
 */
public interface MasterKeyProvider {

    /** Versão usada para proteger novas chaves de dados (ex.: "v2"). */
    String activeVersion();

    /** Chave de uma versão específica (necessária para abrir dados protegidos por versões antigas). */
    Optional<SecretKey> key(String version);

    /** false quando nenhuma chave foi configurada: funcionalidades clínicas ficam indisponíveis. */
    boolean isConfigured();
}
