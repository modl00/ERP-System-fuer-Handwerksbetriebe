package org.example.kalkulationsprogramm.service.ki.assistent;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.service.ki.assistent.Fehlerkontext.BrowserFehler;
import org.example.kalkulationsprogramm.service.log.ServerFehlerPuffer;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Baut den automatischen Anhang eines Fehlerberichts (#255): Zeit, Seite, was der Browser an Fehlern sah und die letzten Fehler des Servers. Alles wird gekürzt und von
 * E-Mail-Adressen, Bankdaten, Schlüsseln und Anfrage-Parametern bereinigt, bevor es irgendwo hingeht. Der Benutzer sieht den ganzen Anhang in der Vorschau, bevor er
 * bestätigt; ohne Bestätigung verlässt nichts das Programm.
 */
@Component
@RequiredArgsConstructor
public class FehlerBerichtService {

    static final int MAX_BROWSER = 10;
    static final int MAX_SEITENFEHLER = 5;
    static final int MAX_SERVER = 15;
    static final Duration SERVER_ZEITRAUM = Duration.ofMinutes(30);
    private static final int MAX_FELD = 300;

    private static final Pattern EMAIL = Pattern.compile("[^\\s@]++@[^\\s@.]++\\.[^\\s@]++");
    private static final Pattern IBAN = Pattern.compile("\\b[A-Z]{2}\\d{2}(?:\\s?[A-Z0-9]{4}){3,7}\\b");
    private static final Pattern GEHEIM = Pattern.compile("(?i)(passwort|kennwort|password|api[-_ ]?key|token|secret|geheimnis|schl[uü]ssel)\\s*[:=]\\s*\\S++");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s++\\S++");
    private static final Pattern LANGES_GEHEIMNIS = Pattern.compile("[A-Za-z0-9+/_=-]{32,}");
    private static final Pattern URL_ZUGANG = Pattern.compile("(?i)(https?://)[^\\s/@]+@");
    private static final DateTimeFormatter UHR = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ServerFehlerPuffer serverFehler;
    private final Environment umgebung;

    /** Der Anhang als Markdown; leer ohne Kontext. */
    public String anhang(Fehlerkontext k) {
        return anhang(k, Instant.now());
    }

    String anhang(Fehlerkontext k, Instant jetzt) {
        if (k == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<details>\n<summary>Automatischer Kontext (vom Programm angehängt)</summary>\n\n");
        sb.append("- **Zeit (Server):** ").append(UHR.withZone(ZoneId.systemDefault()).format(jetzt)).append(" Uhr\n");
        sb.append("- **Seite:** ").append(feld(k.route())).append(k.titel() == null || k.titel().isBlank() ? "" : " (" + feld(k.titel()) + ")").append('\n');
        List<String> seite = begrenzt(k.seitenFehler(), MAX_SEITENFEHLER);
        if (!seite.isEmpty()) {
            sb.append("- **Meldungen auf der Seite:**\n");
            seite.forEach(m -> sb.append("  - ").append(feld(m)).append('\n'));
        }
        List<BrowserFehler> browser = begrenzt(k.browserFehler(), MAX_BROWSER);
        sb.append("- **Fehlgeschlagene Aufrufe und Fehler im Browser (").append(browser.size()).append("):**");
        if (browser.isEmpty()) {
            sb.append(" keine\n");
        } else {
            sb.append('\n');
            for (BrowserFehler b : browser) {
                sb.append("  - ").append(feld(b.zeit())).append(' ').append(feld(b.methode())).append(' ').append(pfad(b.pfad()))
                        .append(b.status() == null ? "" : " → " + b.status())
                        .append(b.meldung() == null || b.meldung().isBlank() ? "" : ": " + feld(b.meldung())).append('\n');
            }
        }
        List<ServerFehlerPuffer.Eintrag> log = serverFehler.letzte(MAX_SERVER, jetzt.minus(SERVER_ZEITRAUM));
        sb.append("- **Letzte Fehler im Server (").append(log.size()).append(", höchstens 30 Minuten alt):**");
        if (log.isEmpty()) {
            sb.append(" keine\n");
        } else {
            sb.append('\n');
            for (ServerFehlerPuffer.Eintrag e : log) {
                sb.append("  - ").append(UHR.withZone(ZoneId.systemDefault()).format(e.zeit())).append(" [").append(feld(e.logger())).append("] ").append(feld(e.meldung()));
                if (e.fehlerart() != null) {
                    sb.append(" (").append(feld(e.fehlerart())).append(')');
                }
                if (!e.stellen().isEmpty()) {
                    sb.append(" an ").append(feld(e.stellen().get(0)));
                }
                sb.append('\n');
            }
        }
        String[] aktiv = umgebung.getActiveProfiles();
        String profile = aktiv == null ? "" : String.join(", ", aktiv);
        sb.append("- **Umgebung:** Profil ").append(profile.isBlank() ? "(Standard)" : feld(profile)).append(", Java ").append(feld(System.getProperty("java.version"))).append('\n');
        sb.append("\n</details>");
        return sb.toString();
    }

    /** Ein Wert aus dem Browser oder dem Log: einzeilig, gekürzt und bereinigt. */
    static String feld(String s) {
        return bereinige(kuerze(s, MAX_FELD));
    }

    /** Nur eigene Programmadressen, ohne Parameter (dort stehen oft Namen oder Suchbegriffe). */
    static String pfad(String p) {
        if (p == null || !p.startsWith("/api/")) {
            return "[andere Adresse]";
        }
        int q = p.indexOf('?');
        String ohne = q >= 0 ? p.substring(0, q) : p;
        return bereinige(kuerze(ohne.replaceAll("\\d{6,}", "…"), 120));
    }

    static String bereinige(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("\\p{Cntrl}", " ");
        t = EMAIL.matcher(t).replaceAll("[E-Mail]");
        t = IBAN.matcher(t).replaceAll("[Bankdaten]");
        t = GEHEIM.matcher(t).replaceAll("$1=[entfernt]");
        t = BEARER.matcher(t).replaceAll("Bearer [entfernt]");
        t = URL_ZUGANG.matcher(t).replaceAll("$1[entfernt]@");
        t = LANGES_GEHEIMNIS.matcher(t).replaceAll("[…]");
        return t;
    }

    private static String kuerze(String s, int max) {
        if (s == null) {
            return "";
        }
        String einzeilig = s.replaceAll("\\s+", " ").strip();
        return einzeilig.length() <= max ? einzeilig : einzeilig.substring(0, max) + " …";
    }

    private static <T> List<T> begrenzt(List<T> liste, int max) {
        if (liste == null) {
            return List.of();
        }
        List<T> aus = new ArrayList<>();
        for (T t : liste) {
            if (t != null) {
                aus.add(t);
            }
            if (aus.size() >= max) {
                break;
            }
        }
        return aus;
    }
}
