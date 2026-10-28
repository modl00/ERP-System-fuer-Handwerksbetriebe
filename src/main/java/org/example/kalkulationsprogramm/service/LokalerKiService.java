package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * Anbindung einer lokalen KI über die OpenAI-kompatible Schnittstelle ({@code /v1/chat/completions}). Das sprechen Ollama, LM Studio,
 * vLLM und llama.cpp. Die Daten verlassen dabei das eigene Netz nicht.
 *
 * <p>Einstellungen: Adresse (zum Beispiel {@code http://192.168.178.50:11434}), Modellname und optional ein Schlüssel. Der Schlüssel wird nie
 * zurückgegeben, nur ob einer gesetzt ist.</p>
 */
@Service
@Slf4j
public class LokalerKiService {

    /** Eine Nachricht des Gesprächs; {@code rolle} ist {@code user} oder {@code assistant}. */
    public record Nachricht(String rolle, String text) { }

    public record Konfiguration(String adresse, String modell, boolean schluesselGesetzt, boolean hilfeLokal) { }

    /** Ergebnis des Tests. {@code art}: OK, NICHT_ERREICHBAR, MODELL_FEHLT, KEIN_ZUGRIFF, FEHLER. */
    public record Testergebnis(boolean ok, String art, String meldung) { }

    /** Fehler mit einer Meldung, die der Anwender lesen darf. */
    public static class LokaleKiFehler extends IOException {
        public LokaleKiFehler(String nachricht) {
            super(nachricht);
        }
    }

    static final String ADRESSE = "ki.lokal.adresse";
    static final String MODELL = "ki.lokal.modell";
    static final String SCHLUESSEL = "ki.lokal.schluessel";
    static final String HILFE_LOKAL = "ki.hilfe.anbieter";
    private static final Pattern MODELLNAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._:/@-]{0,119}");
    private static final Pattern DENKTEXT = Pattern.compile("(?s)<think>.*?</think>");
    private static final int MAX_ADRESSE = 200;

    private final SystemSettingsService einstellungen;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public LokalerKiService(SystemSettingsService einstellungen) {
        this.einstellungen = einstellungen;
    }

    public Konfiguration konfiguration() {
        return new Konfiguration(text(ADRESSE), text(MODELL), !text(SCHLUESSEL).isEmpty(), "LOKAL".equals(text(HILFE_LOKAL)));
    }

    /** Der gespeicherte Schlüssel (leer, wenn keiner gesetzt ist). Nur für den Aufruf der KI gedacht, wird nie an die Oberfläche gegeben. */
    public String schluessel() {
        return text(SCHLUESSEL);
    }

    public boolean istKonfiguriert() {
        return !text(ADRESSE).isEmpty() && !text(MODELL).isEmpty();
    }

    /**
     * Speichert Adresse, Modell und Schlüssel. {@code schluessel}: {@code null} = unverändert, leer = entfernen.
     * Die Adresse muss mit http:// oder https:// beginnen und darf weder Zugangsdaten noch Parameter enthalten.
     */
    @Transactional
    public Konfiguration speichern(String adresse, String modell, String schluessel, boolean hilfeLokal) {
        String a = adresse == null ? "" : adresse.strip();
        String m = modell == null ? "" : modell.strip();
        if (!a.isEmpty()) {
            pruefeAdresse(a);
        }
        if (!m.isEmpty() && !MODELLNAME.matcher(m).matches()) {
            throw fehler("Der Modellname ist ungültig (erlaubt sind Buchstaben, Ziffern und . : / @ - _).");
        }
        if (schluessel != null && schluessel.length() > 500) {
            throw fehler("Der Schlüssel ist zu lang.");
        }
        if (hilfeLokal && (a.isEmpty() || m.isEmpty())) {
            throw fehler("Für die KI-Hilfe mit lokaler KI müssen Adresse und Modellname eingetragen sein.");
        }
        einstellungen.save(HILFE_LOKAL, hilfeLokal ? "LOKAL" : "GEMINI", "Anbieter der KI-Hilfe (GEMINI oder LOKAL)");
        einstellungen.save(ADRESSE, a, "Adresse der lokalen KI (OpenAI-kompatibel, zum Beispiel Ollama)");
        einstellungen.save(MODELL, m, "Modellname der lokalen KI");
        if (schluessel != null) {
            einstellungen.save(SCHLUESSEL, schluessel.strip(), "Schlüssel der lokalen KI (optional)");
        }
        return konfiguration();
    }

    /** Die Basis-Adresse mit {@code /v1}: aus {@code http://host:11434} wird {@code http://host:11434/v1}. */
    public static String basis(String adresse) {
        String a = adresse.strip();
        while (a.endsWith("/")) {
            a = a.substring(0, a.length() - 1);
        }
        return a.endsWith("/v1") ? a : a + "/v1";
    }

    static void pruefeAdresse(String adresse) {
        if (adresse.length() > MAX_ADRESSE) {
            throw fehler("Die Adresse ist zu lang.");
        }
        try {
            URI uri = new URI(adresse);
            String schema = uri.getScheme();
            if (schema == null || !(schema.equalsIgnoreCase("http") || schema.equalsIgnoreCase("https"))) {
                throw fehler("Die Adresse muss mit http:// oder https:// beginnen, zum Beispiel http://192.168.178.50:11434");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw fehler("In der Adresse fehlt der Rechnername oder die IP-Adresse.");
            }
            if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw fehler("Die Adresse darf keine Zugangsdaten oder Parameter enthalten.");
            }
        } catch (URISyntaxException e) {
            throw fehler("Die Adresse ist ungültig.");
        }
    }

    /** Schickt das Gespräch an das lokale Modell und gibt dessen Antwort zurück. */
    public String chat(String system, List<Nachricht> nachrichten, double temperatur, int maxTokens) throws IOException, InterruptedException {
        Konfiguration k = konfiguration();
        if (!istKonfiguriert()) {
            throw new LokaleKiFehler("Die lokale KI ist noch nicht eingerichtet (Einstellungen → KI → Lokale KI).");
        }
        ObjectNode body = json.createObjectNode();
        body.put("model", k.modell());
        body.put("stream", false);
        body.put("temperature", temperatur);
        body.put("max_tokens", maxTokens);
        ArrayNode messages = body.putArray("messages");
        if (system != null && !system.isBlank()) {
            messages.addObject().put("role", "system").put("content", system);
        }
        for (Nachricht n : nachrichten) {
            messages.addObject().put("role", "assistant".equals(n.rolle()) ? "assistant" : "user").put("content", n.text());
        }
        JsonNode antwort = sende("POST", basis(k.adresse()) + "/chat/completions", body.toString(), k, Duration.ofSeconds(180));
        String text = antwort.path("choices").path(0).path("message").path("content").asText("");
        text = DENKTEXT.matcher(text).replaceAll("").strip();
        if (text.isEmpty()) {
            throw new LokaleKiFehler("Das lokale Modell hat keine Antwort geliefert.");
        }
        return text;
    }

    /** Die Modelle, die der lokale Server anbietet (bei Ollama die installierten). */
    public List<String> modelle() {
        Konfiguration k = konfiguration();
        if (k.adresse().isEmpty()) {
            throw fehler("Bitte zuerst die Adresse der lokalen KI eintragen.");
        }
        try {
            JsonNode antwort = sende("GET", basis(k.adresse()) + "/models", null, k, Duration.ofSeconds(15));
            List<String> liste = new ArrayList<>();
            for (JsonNode m : antwort.path("data")) {
                String id = m.path("id").asText("");
                if (!id.isBlank()) {
                    liste.add(id);
                }
            }
            liste.sort(String::compareTo);
            return liste;
        } catch (LokaleKiFehler e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Die lokale KI ist nicht erreichbar.");
        }
    }

    /** Prüft Erreichbarkeit und Modell mit einer Mini-Anfrage und erklärt das Ergebnis. */
    public Testergebnis teste() {
        Konfiguration k = konfiguration();
        if (k.adresse().isEmpty() || k.modell().isEmpty()) {
            return new Testergebnis(false, "FEHLER", "Bitte Adresse und Modellname eintragen und speichern.");
        }
        try {
            String antwort = chat(null, List.of(new Nachricht("user", "Antworte nur mit dem Wort OK.")), 0.0, 16);
            return new Testergebnis(true, "OK", "Die lokale KI antwortet (Modell " + k.modell() + "): " + kurz(antwort));
        } catch (LokaleKiFehler e) {
            return new Testergebnis(false, art(e.getMessage()), e.getMessage());
        } catch (IOException e) {
            return new Testergebnis(false, "FEHLER", "Die lokale KI konnte nicht befragt werden.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Testergebnis(false, "FEHLER", "Der Test wurde unterbrochen.");
        }
    }

    private JsonNode sende(String methode, String adresse, String body, Konfiguration k, Duration frist) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(adresse)).timeout(frist).header("Accept", "application/json");
        String schluessel = text(SCHLUESSEL);
        if (!schluessel.isEmpty()) {
            b.header("Authorization", "Bearer " + schluessel);
        }
        if (body == null) {
            b.GET();
        } else {
            b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        }
        HttpResponse<String> antwort;
        try {
            antwort = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException | ConnectException e) {
            throw new LokaleKiFehler("Die lokale KI ist unter " + k.adresse() + " nicht erreichbar. Läuft Ollama, ist die Adresse richtig und erlaubt die Firewall Port 11434 (und ist OLLAMA_HOST=0.0.0.0 gesetzt)?");
        } catch (HttpTimeoutException e) {
            throw new LokaleKiFehler("Die lokale KI hat nicht rechtzeitig geantwortet. Das Modell ist vielleicht zu groß oder lädt noch; bitte erneut versuchen.");
        } catch (IOException e) {
            log.warn("Lokale KI nicht erreichbar: {}", e.toString());
            throw new LokaleKiFehler("Die Verbindung zur lokalen KI ist fehlgeschlagen (" + k.adresse() + ").");
        }
        int status = antwort.statusCode();
        if (status == 200) {
            return json.readTree(antwort.body());
        }
        log.warn("Lokale KI antwortet mit Fehler {}: {}", status, kurz(antwort.body()));
        throw new LokaleKiFehler(switch (status) {
            case 404 -> "Das Modell \"" + k.modell() + "\" ist auf dem lokalen KI-Rechner nicht vorhanden. Bei Ollama installieren Sie es mit: ollama pull " + k.modell();
            case 401, 403 -> "Die lokale KI verlangt einen Schlüssel oder lehnt ihn ab (Fehler " + status + ").";
            case 400 -> "Die lokale KI lehnt die Anfrage ab (Fehler 400). Meist ist der Modellname falsch oder die Eingabe zu lang für das Modell.";
            default -> "Die lokale KI meldet einen Fehler (" + status + ").";
        });
    }

    private static String art(String meldung) {
        if (meldung.contains("nicht erreichbar") || meldung.contains("nicht rechtzeitig") || meldung.contains("Verbindung")) {
            return "NICHT_ERREICHBAR";
        }
        if (meldung.contains("nicht vorhanden")) {
            return "MODELL_FEHLT";
        }
        if (meldung.contains("Schlüssel")) {
            return "KEIN_ZUGRIFF";
        }
        return "FEHLER";
    }

    private String text(String schluessel) {
        String w = einstellungen.get(schluessel, "");
        return w == null ? "" : w.strip();
    }

    private static String kurz(String s) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").strip();
        return t.length() > 80 ? t.substring(0, 80) + "…" : t;
    }

    private static ResponseStatusException fehler(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
