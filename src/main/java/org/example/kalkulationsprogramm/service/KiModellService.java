package org.example.kalkulationsprogramm.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Welches Gemini-Modell welche KI-Funktion benutzt. Die Auswahl steht in den Einstellungen (Datenbank); ohne Eintrag gilt wie bisher der Standard
 * aus {@code application.properties} ({@code ai.gemini.model.*}, {@code ai.ki-hilfe.model}). Dazu: die Modelle laden, die der Schlüssel kann, ein
 * Modell mit einer Mini-Anfrage testen (so fallen ein erschöpftes Kontingent oder ein nicht freigegebenes Modell sofort auf) und die Websuche der KI-Hilfe.
 */
@Service
@Slf4j
public class KiModellService {

    /** Die KI-Funktionen, für die ein Modell gewählt werden kann. */
    public enum Funktion {
        HILFE("ki-hilfe", "KI-Hilfe (Chat)", "ai.ki-hilfe.model", "gemini-2.5-flash"),
        DOKUMENT_ANALYSE("dokument-analyse", "Dokumente und Belege erkennen", "ai.gemini.model.dokument-analyse", "gemini-2.5-flash"),
        PRO("pro", "Schwierige Fälle (genaues Modell)", "ai.gemini.model.pro", "gemini-2.5-pro"),
        SCANNER("scanner", "Scanner", "ai.gemini.model.scanner", "gemini-2.5-flash"),
        PDF_EXTRAKTION("pdf-extractor", "PDF-Auslesen", "ai.gemini.model.pdf-extractor", "gemini-2.5-flash"),
        SPRACHEINGABE("spracheingabe", "Spracheingabe", "ai.gemini.model.spracheingabe", "gemini-2.5-flash"),
        EMAIL_KLASSIFIZIERUNG("email-classification", "E-Mails einordnen", "ai.gemini.model.email-classification", "gemini-2.5-flash-lite"),
        ASSISTENT("assistent", "KI-Assistent mit Auskünften", "ai.assistent.model", "gemini-2.5-flash"),
        EMAIL_TEXT("email-text", "E-Mail-Texte verbessern", "ai.email.model", "gemini-2.5-flash"),
        PLANUNG("plantafel", "Plantafel: Vorschläge und Erklärungen", "ai.plantafel.model", "gemini-2.5-flash"),
        TESTBOX("testbox", "Test-Box: Rückmeldungen aufbereiten", "ai.testbox.model", "gemini-2.5-flash");

        private final String schluessel;
        private final String anzeige;
        private final String eigenschaft;
        private final String reserve;

        Funktion(String schluessel, String anzeige, String eigenschaft, String reserve) {
            this.schluessel = schluessel;
            this.anzeige = anzeige;
            this.eigenschaft = eigenschaft;
            this.reserve = reserve;
        }

        public String schluessel() { return schluessel; }
        public String anzeige() { return anzeige; }
    }

    /** Ein Modell, das der Schlüssel benutzen kann. */
    public record VerfuegbaresModell(String name, String anzeigename, String beschreibung) { }

    /** Die Einstellung einer Funktion. {@code modell} ist das, was gerade gilt; {@code standard} das, was ohne Auswahl gelten würde. */
    public record FunktionsModell(String schluessel, String anzeige, String modell, String standard, boolean gewaehlt) { }

    public record Uebersicht(List<FunktionsModell> funktionen, boolean websuche, boolean websucheStandard) { }

    /** Ergebnis eines Modelltests. {@code art}: OK, NICHT_GEFUNDEN, KONTINGENT, KEIN_ZUGRIFF, SCHLUESSEL, FEHLER. */
    public record Testergebnis(boolean ok, String art, String meldung, int status) { }

    static final String PRAEFIX = "ki.modell.";
    static final String WEBSUCHE = "ki.hilfe.websuche";
    private static final Pattern MODELLNAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final String BASIS = "https://generativelanguage.googleapis.com/v1beta/";

    private final SystemSettingsService einstellungen;
    private final Environment umgebung;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Value("${ai.ki-hilfe.web-search-enabled:true}")
    private boolean websucheStandard;

    public KiModellService(SystemSettingsService einstellungen, Environment umgebung) {
        this.einstellungen = einstellungen;
        this.umgebung = umgebung;
    }

    /** Das Modell der Funktion: die Auswahl aus den Einstellungen, sonst {@code standard} (der bisherige Wert des aufrufenden Dienstes). */
    public String modell(Funktion funktion, String standard) {
        String gewaehlt = gewaehlt(funktion);
        if (gewaehlt != null) {
            return gewaehlt;
        }
        return standard == null || standard.isBlank() ? standardVon(funktion) : standard;
    }

    /** Das Modell, das ohne Auswahl für diese Funktion gilt. */
    public String standard(Funktion funktion) {
        return standardVon(funktion);
    }

    /** Wurde für diese Funktion in den Einstellungen ein Modell gewählt? */
    public boolean istGewaehlt(Funktion funktion) {
        return gewaehlt(funktion) != null;
    }

    public boolean websuche() {
        String wert = einstellungen.get(WEBSUCHE, null);
        return wert == null || wert.isBlank() ? websucheStandard : Boolean.parseBoolean(wert);
    }

    public Uebersicht uebersicht() {
        List<FunktionsModell> liste = new ArrayList<>();
        for (Funktion f : Funktion.values()) {
            String gewaehlt = gewaehlt(f);
            String standard = standardVon(f);
            liste.add(new FunktionsModell(f.schluessel, f.anzeige, gewaehlt != null ? gewaehlt : standard, standard, gewaehlt != null));
        }
        return new Uebersicht(liste, websuche(), websucheStandard);
    }

    /**
     * Speichert die Auswahl. Eine Funktion fehlt oder hat einen leeren Wert: Der Standard gilt wieder. Unbekannte Funktionen und ungültige
     * Modellnamen werden abgelehnt.
     */
    @Transactional
    public Uebersicht speichern(Map<String, String> modelle, Boolean websuche) {
        Map<Funktion, String> neu = new LinkedHashMap<>();
        if (modelle != null) {
            for (Map.Entry<String, String> e : modelle.entrySet()) {
                Funktion f = funktion(e.getKey());
                String m = e.getValue() == null ? "" : e.getValue().strip();
                if (!m.isEmpty() && !MODELLNAME.matcher(m).matches()) {
                    throw fehler("Der Modellname \"" + kurz(m) + "\" ist ungültig (erlaubt sind Buchstaben, Ziffern, Punkt, Strich).");
                }
                neu.put(f, m);
            }
        }
        for (Map.Entry<Funktion, String> e : neu.entrySet()) {
            einstellungen.save(PRAEFIX + e.getKey().schluessel, e.getValue(), "Gewähltes Gemini-Modell für: " + e.getKey().anzeige);
        }
        if (websuche != null) {
            einstellungen.save(WEBSUCHE, String.valueOf(websuche), "Websuche der KI-Hilfe (Google-Suche)");
        }
        return uebersicht();
    }

    /** Die Modelle, die der Schlüssel zum Erzeugen von Text kann (ohne Bild- und Einbettungsmodelle), nach Name sortiert. */
    public List<VerfuegbaresModell> verfuegbare() {
        String schluessel = apiKey();
        try {
            HttpResponse<String> antwort = http.send(HttpRequest.newBuilder(URI.create(BASIS + "models?pageSize=1000"))
                    .header("x-goog-api-key", schluessel).timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (antwort.statusCode() != 200) {
                throw fehler(klartext(antwort.statusCode(), "Modelle laden"));
            }
            return modelleAus(json.readTree(antwort.body()));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Modellliste konnte nicht geladen werden: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Die Modelle konnten nicht geladen werden (keine Verbindung zu Google).");
        }
    }

    static List<VerfuegbaresModell> modelleAus(JsonNode wurzel) {
        List<VerfuegbaresModell> liste = new ArrayList<>();
        for (JsonNode m : wurzel.path("models")) {
            boolean kannText = false;
            for (JsonNode methode : m.path("supportedGenerationMethods")) {
                if ("generateContent".equals(methode.asText())) {
                    kannText = true;
                }
            }
            String name = m.path("name").asText("");
            if (!kannText || name.isBlank()) {
                continue;
            }
            name = name.startsWith("models/") ? name.substring("models/".length()) : name;
            String lower = name.toLowerCase();
            if (lower.contains("embedding") || lower.contains("imagen") || lower.contains("-image") || lower.contains("tts") || lower.contains("aqa")) {
                continue;
            }
            liste.add(new VerfuegbaresModell(name, m.path("displayName").asText(name), m.path("description").asText("")));
        }
        liste.sort(Comparator.comparing(VerfuegbaresModell::name));
        return liste;
    }

    /** Schickt eine Mini-Anfrage an das Modell und erklärt das Ergebnis in Klartext. */
    public Testergebnis teste(String modell) {
        String m = modell == null ? "" : modell.strip();
        if (!MODELLNAME.matcher(m).matches()) {
            return new Testergebnis(false, "FEHLER", "Bitte einen gültigen Modellnamen angeben.", 400);
        }
        String schluessel = apiKey();
        try {
            String body = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"Antworte nur mit dem Wort OK.\"}]}],\"generationConfig\":{\"maxOutputTokens\":16}}";
            HttpResponse<String> antwort = http.send(HttpRequest.newBuilder(URI.create(BASIS + "models/" + URLEncoder.encode(m, StandardCharsets.UTF_8) + ":generateContent"))
                    .header("Content-Type", "application/json").header("x-goog-api-key", schluessel).timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return ergebnis(m, antwort.statusCode());
        } catch (Exception e) {
            log.warn("Modelltest fehlgeschlagen: {}", e.getMessage());
            return new Testergebnis(false, "FEHLER", "Keine Verbindung zu Google. Bitte die Internetverbindung des Servers prüfen.", 0);
        }
    }

    static Testergebnis ergebnis(String modell, int status) {
        if (status == 200) {
            return new Testergebnis(true, "OK", "Das Modell \"" + modell + "\" antwortet.", status);
        }
        String art = switch (status) {
            case 404 -> "NICHT_GEFUNDEN";
            case 429 -> "KONTINGENT";
            case 403 -> "KEIN_ZUGRIFF";
            case 400 -> "SCHLUESSEL";
            default -> "FEHLER";
        };
        return new Testergebnis(false, art, klartext(status, "Modell \"" + modell + "\""), status);
    }

    /** Was ein Google-Fehlercode für den Anwender bedeutet. */
    public static String klartext(int status, String womit) {
        return switch (status) {
            case 429 -> "Das Google-Kontingent für dieses Modell ist erschöpft oder im Tarif nicht freigegeben (Fehler 429). Anderes Modell wählen, später erneut versuchen oder die Abrechnung im Google-Projekt aktivieren.";
            case 404 -> womit + ": Das Modell gibt es für diesen Schlüssel nicht (Fehler 404). Bitte aus der Liste der verfügbaren Modelle wählen.";
            case 403 -> womit + ": Der Schlüssel hat dafür keine Berechtigung (Fehler 403).";
            case 400 -> womit + ": Google lehnt die Anfrage ab, der Schlüssel ist ungültig oder die Anfrage nicht erlaubt (Fehler 400).";
            case 500, 502, 503, 504 -> "Google ist gerade überlastet oder gestört (Fehler " + status + "). Bitte später erneut versuchen.";
            default -> womit + ": Unerwartete Antwort von Google (Fehler " + status + ").";
        };
    }

    private String gewaehlt(Funktion f) {
        String wert = einstellungen.get(PRAEFIX + f.schluessel, null);
        return wert == null || wert.isBlank() ? null : wert.strip();
    }

    private String standardVon(Funktion f) {
        return umgebung.getProperty(f.eigenschaft, f.reserve);
    }

    private String apiKey() {
        String k = einstellungen.getGeminiApiKey();
        if (k == null || k.isBlank()) {
            throw fehler("Es ist noch kein Gemini API Key hinterlegt.");
        }
        return k;
    }

    private static Funktion funktion(String schluessel) {
        for (Funktion f : Funktion.values()) {
            if (f.schluessel.equals(schluessel)) {
                return f;
            }
        }
        throw fehler("Unbekannte KI-Funktion: " + kurz(schluessel));
    }

    private static String kurz(String s) {
        return s == null ? "" : s.length() > 40 ? s.substring(0, 40) + "…" : s;
    }

    private static ResponseStatusException fehler(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
