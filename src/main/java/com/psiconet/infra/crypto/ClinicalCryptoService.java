package com.psiconet.infra.crypto;

import com.psiconet.infra.exceptions.ClinicalUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Criptografia de campo para dados clínicos — AES-256-GCM com <b>envelope encryption</b>:
 * <ul>
 *   <li>Cada prontuário tem a própria <b>chave de dados</b> (DEK, 256 bits aleatórios).</li>
 *   <li>A DEK é guardada no banco apenas <b>cifrada pela chave-mestra</b> (KEK) de uma versão
 *       (coluna de versão permite rotação: re-cifrar DEKs com a nova KEK sem tocar nos dados).</li>
 *   <li>Cada campo é cifrado com IV aleatório de 96 bits e <b>AAD</b> = contexto (id do registro +
 *       nome do campo): um texto cifrado copiado para outro registro/campo não decifra.</li>
 * </ul>
 * Formato armazenado: {@code g1:<base64(iv || ciphertext || tag)>}. Nada clínico em texto claro
 * no banco, backups ou logs; o texto claro só existe em memória durante a requisição autorizada.
 */
@Service
@RequiredArgsConstructor
public class ClinicalCryptoService {

    private static final String FORMAT_PREFIX = "g1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int DEK_BYTES = 32;

    private final MasterKeyProvider masterKeyProvider;
    private final SecureRandom random = new SecureRandom();

    public boolean isAvailable() {
        return masterKeyProvider.isConfigured();
    }

    /** Gera uma nova DEK e devolve-a já protegida pela KEK ativa. */
    public WrappedKey newDataKey() {
        ensureAvailable();
        byte[] dek = new byte[DEK_BYTES];
        random.nextBytes(dek);
        try {
            String version = masterKeyProvider.activeVersion();
            return new WrappedKey(encrypt(kek(version), dek, aad("dek", version)), version);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    /** Re-protege uma DEK com a KEK ativa (rotação de chave-mestra). */
    public WrappedKey rewrap(WrappedKey wrapped) {
        byte[] dek = unwrapRaw(wrapped);
        try {
            String version = masterKeyProvider.activeVersion();
            return new WrappedKey(encrypt(kek(version), dek, aad("dek", version)), version);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    public String encryptField(WrappedKey dataKey, String context, String plaintext) {
        if (plaintext == null) return null;
        byte[] dek = unwrapRaw(dataKey);
        try {
            return encrypt(new SecretKeySpec(dek, "AES"), plaintext.getBytes(StandardCharsets.UTF_8), aad("field", context));
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    public String decryptField(WrappedKey dataKey, String context, String stored) {
        if (stored == null) return null;
        byte[] dek = unwrapRaw(dataKey);
        try {
            return new String(decrypt(new SecretKeySpec(dek, "AES"), stored, aad("field", context)), StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(dek, (byte) 0);
        }
    }

    // ---------------------------------------------------------------------------------------

    private byte[] unwrapRaw(WrappedKey wrapped) {
        ensureAvailable();
        return decrypt(kek(wrapped.keyVersion()), wrapped.wrappedKey(), aad("dek", wrapped.keyVersion()));
    }

    private SecretKey kek(String version) {
        return masterKeyProvider.key(version).orElseThrow(() ->
                new IllegalStateException("Chave-mestra da versão " + version + " não está disponível."));
    }

    private String encrypt(SecretKey key, byte[] plaintext, byte[] aad) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aad);
            byte[] ct = cipher.doFinal(plaintext);
            return FORMAT_PREFIX + Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar dado clínico.", e);
        }
    }

    private byte[] decrypt(SecretKey key, String stored, byte[] aad) {
        if (!stored.startsWith(FORMAT_PREFIX)) {
            throw new IllegalStateException("Formato de dado cifrado desconhecido.");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(FORMAT_PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            cipher.updateAAD(aad);
            return cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Sem detalhes: falha de integridade (dado adulterado ou chave/contexto errado)
            throw new IllegalStateException("Falha de integridade ao abrir dado clínico.");
        }
    }

    private static byte[] aad(String purpose, String context) {
        return ("psiconet:" + purpose + ":" + context).getBytes(StandardCharsets.UTF_8);
    }

    private void ensureAvailable() {
        if (!isAvailable()) throw new ClinicalUnavailableException();
    }

    /** DEK cifrada + versão da KEK que a protege. */
    public record WrappedKey(String wrappedKey, String keyVersion) {}
}
