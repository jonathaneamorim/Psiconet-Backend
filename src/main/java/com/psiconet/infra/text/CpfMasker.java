package com.psiconet.infra.text;

/**
 * Máscara de CPF no estilo Pix: exibe apenas os dígitos centrais (4º ao 9º), que sozinhos não
 * identificam o titular. Ex.: 12345678909 → •••.456.789-••. Aplicada SEMPRE no backend — o CPF
 * completo nunca trafega para telas administrativas.
 */
public final class CpfMasker {

    private CpfMasker() {}

    public static String mask(String cpf) {
        if (cpf == null) return null;
        String digits = cpf.replaceAll("\\D", "");
        if (digits.length() != 11) return "•••.•••.•••-••";
        return "•••." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-••";
    }
}
