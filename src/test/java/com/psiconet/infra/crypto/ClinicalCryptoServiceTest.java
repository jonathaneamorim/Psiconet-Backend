package com.psiconet.infra.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalCryptoServiceTest {

    private static String key(int fill) {
        byte[] raw = new byte[32];
        java.util.Arrays.fill(raw, (byte) fill);
        return Base64.getEncoder().encodeToString(raw);
    }

    private final ClinicalCryptoService crypto =
            new ClinicalCryptoService(new EnvMasterKeyProvider("v1:" + key(1), "v1"));

    @Test
    void cifraEDecifraComContexto() {
        var dek = crypto.newDataKey();
        String stored = crypto.encryptField(dek, "record:1:demand", "Ansiedade em situações sociais");

        assertThat(stored).startsWith("g1:").doesNotContain("Ansiedade");
        assertThat(crypto.decryptField(dek, "record:1:demand", stored)).isEqualTo("Ansiedade em situações sociais");
        // IV aleatório: mesma entrada gera cifras diferentes
        assertThat(crypto.encryptField(dek, "record:1:demand", "Ansiedade em situações sociais")).isNotEqualTo(stored);
    }

    @Test
    void textoCifradoCopiadoParaOutroCampoNaoAbre() {
        var dek = crypto.newDataKey();
        String stored = crypto.encryptField(dek, "record:1:demand", "segredo");
        assertThatThrownBy(() -> crypto.decryptField(dek, "record:2:demand", stored))
                .hasMessageContaining("integridade")
                .hasMessageNotContaining("segredo");
    }

    @Test
    void adulteracaoEhDetectada() {
        var dek = crypto.newDataKey();
        String stored = crypto.encryptField(dek, "ctx", "conteudo");
        byte[] raw = Base64.getDecoder().decode(stored.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "g1:" + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> crypto.decryptField(dek, "ctx", tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rotacaoDeChaveMestraPreservaDados() {
        var dek = crypto.newDataKey();
        String stored = crypto.encryptField(dek, "ctx", "dado clínico");

        ClinicalCryptoService rotated = new ClinicalCryptoService(
                new EnvMasterKeyProvider("v1:" + key(1) + ",v2:" + key(2), "v2"));
        var rewrapped = rotated.rewrap(dek);

        assertThat(rewrapped.keyVersion()).isEqualTo("v2");
        assertThat(rotated.decryptField(rewrapped, "ctx", stored)).isEqualTo("dado clínico");
    }

    @Test
    void semChaveConfiguradaFicaIndisponivel() {
        ClinicalCryptoService none = new ClinicalCryptoService(new EnvMasterKeyProvider("", ""));
        assertThat(none.isAvailable()).isFalse();
        assertThatThrownBy(none::newDataKey).isInstanceOf(com.psiconet.infra.exceptions.ClinicalUnavailableException.class);
    }
}
