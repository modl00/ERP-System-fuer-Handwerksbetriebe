package org.example.kalkulationsprogramm.service.testbox;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Der Zugang zu Gitea für die Test-Box: Issues und Kommentare lesen, Kommentare schreiben. Adresse, Repository und Zugangsschlüssel stehen in den Einstellungen
 * ({@code testbox.gitea.*}); der Schlüssel wird nie wieder angezeigt. Der Schlüssel sollte nur für Issues berechtigt sein (nicht für den Code).
 */
@Component
@RequiredArgsConstructor
public class GiteaClient {

    static final String URL_SCHLUESSEL = "testbox.gitea.url";
    static final String REPO_SCHLUESSEL = "testbox.gitea.repo";
    static final String TOKEN_SCHLUESSEL = "testbox.gitea.token";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SystemSettingsService einstellungen;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    /** Ist alles eingetragen, was für den Zugang nötig ist? */
    public boolean eingerichtet() {
        return !einstellungen.get(URL_SCHLUESSEL, "").isBlank() && !einstellungen.get(REPO_SCHLUESSEL, "").isBlank() && !einstellungen.get(TOKEN_SCHLUESSEL, "").isBlank();
    }

    public JsonNode lies(String pfad) {
        return sende(HttpRequest.newBuilder(uri(pfad)).GET(), pfad);
    }

    public JsonNode schreibe(String pfad, Map<String, String> koerper) {
        try {
            return sende(HttpRequest.newBuilder(uri(pfad)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(koerper), StandardCharsets.UTF_8)), pfad);
        } catch (IOException e) {
            throw new IllegalArgumentException("Der Text konnte nicht gesendet werden.");
        }
    }

    /** Die Adresse im Repository, zum Beispiel {@code /issues?state=open}. */
    private URI uri(String pfad) {
        String basis = einstellungen.get(URL_SCHLUESSEL, "").strip().replaceAll("/+$", "");
        String repo = einstellungen.get(REPO_SCHLUESSEL, "").strip();
        if (basis.isBlank() || repo.isBlank() || einstellungen.get(TOKEN_SCHLUESSEL, "").isBlank()) {
            throw new IllegalArgumentException("Der Zugang zu Gitea ist noch nicht eingerichtet. Bitte Adresse, Repository und Zugangsschlüssel eintragen.");
        }
        try {
            return URI.create(basis + "/api/v1/repos/" + repo + pfad);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Die Adresse von Gitea ist nicht gültig.");
        }
    }

    private JsonNode sende(HttpRequest.Builder anfrage, String pfad) {
        HttpRequest r = anfrage.header("Authorization", "token " + einstellungen.get(TOKEN_SCHLUESSEL, "").strip()).header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15)).build();
        try {
            HttpResponse<String> antwort = http.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = antwort.statusCode();
            if (status == 401 || status == 403) {
                throw new IllegalArgumentException("Gitea nimmt den Zugangsschlüssel nicht an. Bitte Schlüssel und Berechtigung (Issues lesen und schreiben) prüfen.");
            }
            if (status == 404) {
                throw new IllegalArgumentException("Gitea kennt dieses Repository oder diesen Eintrag nicht (" + pfad.split("\\?")[0] + ").");
            }
            if (status >= 300) {
                throw new IllegalArgumentException("Gitea antwortet mit einem Fehler (" + status + ").");
            }
            return JSON.readTree(antwort.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Die Anfrage an Gitea wurde abgebrochen.");
        } catch (IOException e) {
            throw new IllegalArgumentException("Gitea ist nicht erreichbar. Bitte die Adresse prüfen.");
        }
    }

    static String kodiere(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }
}
