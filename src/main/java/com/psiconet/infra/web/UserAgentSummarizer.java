package com.psiconet.infra.web;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reduz o User-Agent a "Navegador versão-principal · Sistema" (ex.: "Chrome 141 · Windows").
 * Guardar o cabeçalho completo não é necessário para a prova de aceite nem para listar sessões e
 * aumenta a "impressão digital" do titular (minimização, LGPD art. 6º, III).
 */
public final class UserAgentSummarizer {

    private static final Pattern EDGE = Pattern.compile("Edg(?:e|A|iOS)?/(\\d+)");
    private static final Pattern OPERA = Pattern.compile("(?:OPR|Opera)/(\\d+)");
    private static final Pattern FIREFOX = Pattern.compile("(?:Firefox|FxiOS)/(\\d+)");
    private static final Pattern CHROME = Pattern.compile("(?:Chrome|CriOS)/(\\d+)");
    private static final Pattern SAFARI = Pattern.compile("Version/(\\d+).*Safari/");

    private UserAgentSummarizer() {}

    public static String summarize(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) return null;
        String browser = browser(userAgent);
        String os = os(userAgent);
        String summary = os == null ? browser : browser + " · " + os;
        return summary.length() > 120 ? summary.substring(0, 120) : summary;
    }

    private static String browser(String ua) {
        Matcher m;
        if ((m = EDGE.matcher(ua)).find()) return "Edge " + m.group(1);
        if ((m = OPERA.matcher(ua)).find()) return "Opera " + m.group(1);
        if ((m = FIREFOX.matcher(ua)).find()) return "Firefox " + m.group(1);
        if ((m = CHROME.matcher(ua)).find()) return "Chrome " + m.group(1);
        if ((m = SAFARI.matcher(ua)).find()) return "Safari " + m.group(1);
        return "Outro navegador";
    }

    private static String os(String ua) {
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iOS")) return "iOS";
        if (ua.contains("Mac OS X") || ua.contains("Macintosh")) return "macOS";
        if (ua.contains("CrOS")) return "ChromeOS";
        if (ua.contains("Linux")) return "Linux";
        return null;
    }
}
