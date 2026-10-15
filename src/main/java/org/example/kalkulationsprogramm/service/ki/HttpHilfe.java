package org.example.kalkulationsprogramm.service.ki;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Gemeinsame HTTP-Hilfe der Anbieter: JSON senden, Fehler in Klartext übersetzen. */
final class HttpHilfe {

    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient STANDARD = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private HttpHilfe() {
    }

    /**
     * Schickt einen JSON-Aufruf und gibt die Antwort zurück. {@code womit} steht in Fehlermeldungen ("Claude"), {@code erreichbarHinweis} wird bei
     * Verbindungsfehlern angehängt.
     */
    static JsonNode sende(HttpClient http, String url, Map<String, String> kopfzeilen, JsonNode body, Duration frist, String womit,
            String erreichbarHinweis) throws KiAnbieterFehler, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(frist).header("Content-Type", "application/json").header("Accept", "application/json");
        kopfzeilen.forEach(b::header);
        b.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        return ausfuehren(http, b.build(), womit, erreichbarHinweis);
    }

    /** Fragt etwas ab (GET), zum Beispiel die Liste der Modelle eines Anbieters. Fehler kommen wie bei {@link #sende} in Klartext. */
    static JsonNode hole(HttpClient http, String url, Map<String, String> kopfzeilen, Duration frist, String womit, String erreichbarHinweis)
            throws KiAnbieterFehler, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(frist).header("Accept", "application/json");
        kopfzeilen.forEach(b::header);
        return ausfuehren(http, b.GET().build(), womit, erreichbarHinweis);
    }

    private static JsonNode ausfuehren(HttpClient http, HttpRequest anfrage, String womit, String erreichbarHinweis) throws KiAnbieterFehler, InterruptedException {
        HttpResponse<String> antwort;
        try {
            antwort = http.send(anfrage, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpConnectTimeoutException | ConnectException e) {
            throw new KiAnbieterFehler(womit + " ist nicht erreichbar." + erreichbarHinweis);
        } catch (HttpTimeoutException e) {
            throw new KiAnbieterFehler(womit + " hat nicht rechtzeitig geantwortet. Bitte erneut versuchen.");
        } catch (IOException e) {
            throw new KiAnbieterFehler("Die Verbindung zu " + womit + " ist fehlgeschlagen.", e);
        }
        if (antwort.statusCode() == 200) {
            try {
                return JSON.readTree(antwort.body());
            } catch (IOException e) {
                throw new KiAnbieterFehler("Die Antwort von " + womit + " ist nicht lesbar.", e);
            }
        }
        throw new KiAnbieterFehler(klartext(antwort.statusCode(), womit));
    }

    static String klartext(int status, String womit) {
        return switch (status) {
            case 400 -> womit + " lehnt die Anfrage ab (Fehler 400). Meist ist der Modellname falsch oder die Eingabe zu lang.";
            case 401, 403 -> womit + " lehnt den Zugang ab (Fehler " + status + "). Bitte den Schlüssel in den Einstellungen prüfen.";
            case 404 -> "Das Modell oder die Adresse bei " + womit + " gibt es nicht (Fehler 404). Bitte Modellname und Adresse prüfen.";
            case 429 -> "Das Kontingent bei " + womit + " ist erschöpft oder es kamen zu viele Anfragen (Fehler 429). Bitte später erneut versuchen oder ein anderes Modell wählen.";
            case 500, 502, 503, 504, 529 -> womit + " ist gerade überlastet oder gestört (Fehler " + status + "). Bitte später erneut versuchen.";
            default -> womit + " meldet einen Fehler (" + status + ").";
        };
    }
}
