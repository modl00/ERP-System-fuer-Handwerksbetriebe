package org.example.kalkulationsprogramm.service.importe;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.service.PasswortSchutz;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Verbindung zur GraphQL-Schnittstelle von Hero Handwerkersoftware (Issue #76, Stufe 1: Verbindung und Erkundung). <b>Nur lesen:</b> Es werden nur Abfragen geschickt,
 * nie Änderungen. Der Schlüssel liegt verschlüsselt in den Einstellungen und kommt in keiner Antwort und in keinem Log vor. Damit niemand den Server für fremde Ziele
 * benutzt, ist nur eine {@code https}-Adresse bei {@code hero-software.de} erlaubt.
 */
@Service
public class HeroVerbindungService {

    private static final Logger log = LoggerFactory.getLogger(HeroVerbindungService.class);
    static final String SCHLUESSEL_EINSTELLUNG = "import.hero.schluessel";
    static final String STANDARD_URL = "https://login.hero-software.de/api/external/v7/graphql";
    static final int MAX_ANFRAGE = 5_000;
    static final int MAX_ANTWORT = 100_000;
    private static final Pattern SCHREIBEND = Pattern.compile("(?i)\\b(mutation|subscription)\\b");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SystemSettingsService einstellungen;
    private final PasswortSchutz schutz;
    private final String url;
    private final HttpClient http;

    @org.springframework.beans.factory.annotation.Autowired
    public HeroVerbindungService(SystemSettingsService einstellungen, PasswortSchutz schutz, @Value("${hero.api.url:" + STANDARD_URL + "}") String url) {
        this(einstellungen, schutz, url, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    HeroVerbindungService(SystemSettingsService einstellungen, PasswortSchutz schutz, String url, HttpClient http) {
        this.einstellungen = einstellungen;
        this.schutz = schutz;
        this.url = pruefeUrl(url);
        this.http = http;
    }

    public record Status(boolean schluesselGesetzt, String adresse) { }

    /** {@code ok}: die Schnittstelle hat geantwortet und keine Fehler gemeldet. {@code antwort}: die Antwort als Text (gekürzt), {@code meldung}: kurze Erklärung. */
    public record Ergebnis(boolean ok, String meldung, String antwort) { }

    public record Feld(String name, String argumente, String typ) { }

    public Status status() {
        return new Status(!einstellungen.get(SCHLUESSEL_EINSTELLUNG, "").isBlank(), url);
    }

    /** Speichert den Schlüssel verschlüsselt; ein leerer Wert entfernt ihn. */
    public Status speichereSchluessel(String schluessel) {
        String s = schluessel == null ? "" : schluessel.strip();
        // Eine eingefügte Zeile „Bearer …“ ist ein häufiger Fehler: Das Wort gehört nicht zum Schlüssel
        if (s.toLowerCase(Locale.ROOT).startsWith("bearer")) {
            throw new IllegalArgumentException("Bitte nur den Schlüssel eintragen, ohne das Wort „Bearer“.");
        }
        if (s.length() > 500 || s.matches(".*\\s.*")) {
            throw new IllegalArgumentException("Der Schlüssel ist ungültig (keine Leerzeichen, höchstens 500 Zeichen).");
        }
        einstellungen.save(SCHLUESSEL_EINSTELLUNG, s.isEmpty() ? "" : schutz.verschluessele(s), "Hero: Schlüssel der GraphQL-Schnittstelle (verschlüsselt)");
        return status();
    }

    /** Die einfachste Abfrage: Sagt, ob Adresse und Schlüssel stimmen. */
    public Ergebnis teste() {
        Ergebnis e = abfrage("{ __typename }");
        if (e.ok()) {
            return new Ergebnis(true, "Die Verbindung zu Hero klappt.", e.antwort());
        }
        return e;
    }

    /** Listet, welche Abfragen die Schnittstelle anbietet (mit Argumenten). Hilft beim Zuordnen der Felder. */
    public List<Feld> schnittstelle() {
        String q = "{ __schema { queryType { fields { name args { name type { name kind ofType { name kind ofType { name kind } } } } type { name kind ofType { name kind ofType { name kind } } } } } } }";
        Ergebnis e = rohAbfrage(q, MAX_ANTWORT * 20);
        if (!e.ok()) {
            throw new IllegalStateException(e.meldung());
        }
        try {
            List<Feld> felder = new ArrayList<>();
            for (JsonNode f : JSON.readTree(e.antwort()).path("data").path("__schema").path("queryType").path("fields")) {
                List<String> args = new ArrayList<>();
                for (JsonNode a : f.path("args")) {
                    args.add(a.path("name").asText() + ": " + typName(a.path("type")));
                }
                felder.add(new Feld(f.path("name").asText(), String.join(", ", args), typName(f.path("type"))));
            }
            return felder;
        } catch (Exception ex) {
            throw new IllegalStateException("Die Antwort von Hero war nicht lesbar.");
        }
    }

    /** Eine frei eingegebene Abfrage ausführen (nur lesend); die Antwort ist auf {@value #MAX_ANTWORT} Zeichen begrenzt. */
    public Ergebnis abfrage(String query) {
        return rohAbfrage(query, MAX_ANTWORT);
    }

    private Ergebnis rohAbfrage(String query, int maxAntwort) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Bitte eine Abfrage eingeben.");
        }
        if (query.length() > MAX_ANFRAGE) {
            throw new IllegalArgumentException("Die Abfrage ist zu lang (höchstens " + MAX_ANFRAGE + " Zeichen).");
        }
        if (SCHREIBEND.matcher(query).find()) {
            throw new IllegalArgumentException("Es sind nur Abfragen erlaubt, keine Änderungen an Hero.");
        }
        String gespeichert = einstellungen.get(SCHLUESSEL_EINSTELLUNG, "");
        if (gespeichert.isBlank()) {
            throw new IllegalArgumentException("Es ist noch kein Hero-Schlüssel eingetragen.");
        }
        String schluessel = schutz.entschluessele(gespeichert);
        try {
            String body = JSON.writeValueAsString(JSON.createObjectNode().put("query", query));
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                    .header("Accept", "application/json").header("Authorization", "Bearer " + schluessel).POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            String text = res.body() == null ? "" : res.body();
            if (res.statusCode() == 401 || res.statusCode() == 403) {
                return new Ergebnis(false, "Hero hat den Schlüssel abgelehnt (Status " + res.statusCode() + "). Bitte den Schlüssel prüfen oder beim Hero-Support neu anfordern.", "");
            }
            if (res.statusCode() < 200 || res.statusCode() >= 300) {
                return new Ergebnis(false, "Hero antwortet mit Status " + res.statusCode() + ".", kuerze(text, 2_000));
            }
            JsonNode json = JSON.readTree(text);
            if (json.has("errors") && json.path("errors").size() > 0) {
                String fehler = json.path("errors").get(0).path("message").asText("Unbekannter Fehler");
                return new Ergebnis(false, "Hero meldet einen Fehler: " + kuerze(fehler, 300), kuerze(text, maxAntwort));
            }
            return new Ergebnis(true, "OK", kuerze(text, maxAntwort));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Ergebnis(false, "Die Abfrage wurde unterbrochen.", "");
        } catch (Exception ex) {
            // Ohne Details aus der Ausnahme: Sie könnte die Adresse, nie aber den Schlüssel enthalten; trotzdem nur der Typ ins Log
            log.warn("[Hero] Verbindung fehlgeschlagen: {}", ex.getClass().getSimpleName());
            return new Ergebnis(false, "Keine Verbindung zu Hero. Ist der Server im Internet erreichbar?", "");
        }
    }

    static String pruefeUrl(String url) {
        URI u;
        try {
            u = URI.create(url == null ? "" : url.strip());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Die Hero-Adresse ist ungültig.");
        }
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equalsIgnoreCase(u.getScheme()) || !(host.equals("hero-software.de") || host.endsWith(".hero-software.de")) || u.getUserInfo() != null) {
            throw new IllegalArgumentException("Die Hero-Adresse muss eine https-Adresse bei hero-software.de sein.");
        }
        return u.toString();
    }

    private static String typName(JsonNode t) {
        if (t == null || t.isMissingNode() || t.isNull()) {
            return "?";
        }
        String kind = t.path("kind").asText();
        if ("NON_NULL".equals(kind)) {
            return typName(t.path("ofType")) + "!";
        }
        if ("LIST".equals(kind)) {
            return "[" + typName(t.path("ofType")) + "]";
        }
        return t.path("name").asText("?");
    }

    private static String kuerze(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n… (gekürzt)";
    }
}
