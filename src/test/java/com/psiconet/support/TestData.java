package com.psiconet.support;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Geradores de dados únicos e válidos para testes de integração (H2 compartilhado no contexto). */
public final class TestData {

    private static final Random RANDOM = new Random();
    private static final AtomicInteger SEQ = new AtomicInteger();

    public static final String STRONG_PASSWORD = "Senha@Forte1";

    /** Versão vigente de Termos e Privacidade em docs/legal (atualize ao publicar nova versão). */
    public static final String LEGAL_VERSION = "1.0.0";

    private TestData() {}

    public static String uniqueEmail(String prefix) {
        return prefix + "." + SEQ.incrementAndGet() + "." + UUID.randomUUID().toString().substring(0, 8) + "@teste.com";
    }

    /** CPF válido (dígitos verificadores corretos), sem máscara. */
    public static String validCpf() {
        int[] d = new int[11];
        do {
            for (int i = 0; i < 9; i++) d[i] = RANDOM.nextInt(10);
        } while (allEqual(d));
        d[9] = checkDigit(d, 9);
        d[10] = checkDigit(d, 10);
        StringBuilder sb = new StringBuilder();
        for (int x : d) sb.append(x);
        return sb.toString();
    }

    public static String uniqueCrp() {
        return "06/" + (100000 + SEQ.incrementAndGet()) + RANDOM.nextInt(10);
    }

    private static int checkDigit(int[] d, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) sum += d[i] * ((length + 1) - i);
        int r = (sum * 10) % 11;
        return r == 10 ? 0 : r;
    }

    private static boolean allEqual(int[] d) {
        for (int i = 1; i < 9; i++) if (d[i] != d[0]) return false;
        return true;
    }
}
