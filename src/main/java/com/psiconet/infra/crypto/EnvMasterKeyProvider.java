package com.psiconet.infra.crypto;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Chaves-mestras via variável de ambiente (ou secret manager que injete variável):
 * <pre>
 *   CLINICAL_MASTER_KEYS=v1:&lt;base64 32 bytes&gt;,v2:&lt;base64 32 bytes&gt;
 *   CLINICAL_ACTIVE_KEY_VERSION=v2
 * </pre>
 * Gere cada chave com {@code openssl rand -base64 32}. Versões antigas devem permanecer na lista até
 * a rotação (re-wrap) terminar. As chaves nunca são logadas nem gravadas no banco.
 */
@Slf4j
@Component
public class EnvMasterKeyProvider implements MasterKeyProvider {

    private final Map<String, SecretKey> keys = new HashMap<>();
    private final String activeVersion;

    public EnvMasterKeyProvider(
            @Value("${psiconet.clinical.master-keys:}") String masterKeys,
            @Value("${psiconet.clinical.active-key-version:}") String activeVersion
    ) {
        if (masterKeys != null && !masterKeys.isBlank()) {
            for (String entry : masterKeys.split(",")) {
                String[] parts = entry.trim().split(":", 2);
                if (parts.length != 2) {
                    throw new IllegalStateException("CLINICAL_MASTER_KEYS inválida: use o formato versao:base64.");
                }
                byte[] raw = Base64.getDecoder().decode(parts[1].trim());
                if (raw.length != 32) {
                    throw new IllegalStateException("Chave-mestra '" + parts[0] + "' deve ter 32 bytes (AES-256).");
                }
                keys.put(parts[0].trim(), new SecretKeySpec(raw, "AES"));
            }
        }
        this.activeVersion = activeVersion == null || activeVersion.isBlank()
                ? keys.keySet().stream().sorted().reduce((a, b) -> b).orElse(null)
                : activeVersion.trim();

        if (keys.isEmpty()) {
            log.error("CLINICAL_MASTER_KEYS não configurada: prontuário e anotações de sessão ficarão INDISPONÍVEIS.");
        } else if (!keys.containsKey(this.activeVersion)) {
            throw new IllegalStateException("CLINICAL_ACTIVE_KEY_VERSION aponta para uma versão inexistente.");
        }
    }

    @Override
    public String activeVersion() {
        return activeVersion;
    }

    @Override
    public Optional<SecretKey> key(String version) {
        return Optional.ofNullable(keys.get(version));
    }

    @Override
    public boolean isConfigured() {
        return !keys.isEmpty();
    }
}
