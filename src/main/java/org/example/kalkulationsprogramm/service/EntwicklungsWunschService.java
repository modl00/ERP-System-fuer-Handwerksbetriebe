package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * „Wunsch an die Entwicklung“ (#138, #123): Verbesserungswünsche aus dem Programm als Issue in Gitea anlegen. Adresse, Repository und Zugangsschlüssel (Token) stellt ein
 * Administrator in den Einstellungen ein. Der Token wird <b>nie</b> zurückgegeben oder protokolliert; ist {@code mail.credentials.encryption-key} gesetzt, liegt er
 * verschlüsselt in der Datenbank, sonst wie die anderen KI-Schlüssel im Klartext in den Einstellungen (die Oberfläche sagt es).
 */
@Service
@RequiredArgsConstructor
public class EntwicklungsWunschService {

    static final String ADRESSE = "ki.wunsch.gitea.adresse";
    static final String REPOSITORY = "ki.wunsch.gitea.repository";
    static final String TOKEN = "ki.wunsch.gitea.token";
    static final int MAX_TOKEN = 200;
    public static final int MAX_TITEL = 200;
    public static final int MAX_TEXT = 10_000;
    private static final Pattern REPO = Pattern.compile("^[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}$");
    private static final Pattern TOKEN_ZEICHEN = Pattern.compile("^[A-Za-z0-9_.:-]{8,200}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SystemSettingsService einstellungen;
    private final MailSecretService geheim;

    /** Was die Oberfläche sehen darf: nie der Token, nur ob einer da ist. */
    public record Stand(String adresse, String repository, boolean tokenGesetzt, boolean verschluesselt, boolean bereit) { }

    public record Testergebnis(boolean ok, String meldung) { }

    public record Angelegt(int nummer, String adresse) { }

    /** Ein offenes Issue, das zu einem neuen Wunsch passen könnte. {@code treffer}: wie viele wichtige Wörter des Wunsches im Titel (und Text) vorkommen. */
    public record Aehnlich(int nummer, String titel, String adresse, int treffer) { }

    public Stand stand() {
        String adresse = wert(ADRESSE);
        String repo = wert(REPOSITORY);
        boolean token = !wert(TOKEN).isEmpty();
        return new Stand(adresse, repo, token, token && wert(TOKEN).startsWith("v1:"), !adresse.isEmpty() && !repo.isEmpty() && token);
    }

    public boolean istBereit() {
        return stand().bereit();
    }

    /** {@code token}: {@code null} = unverändert lassen, leer = entfernen, sonst neu setzen. */
    public Stand speichern(String adresse, String repository, String token) {
        String a = adresse == null ? "" : adresse.strip();
        while (a.endsWith("/")) {
            a = a.substring(0, a.length() - 1);
        }
        String r = repository == null ? "" : repository.strip();
        if (!a.isEmpty()) {
            pruefeAdresse(a);
        }
        if (!r.isEmpty() && !REPO.matcher(r).matches()) {
            throw new IllegalArgumentException("Das Repository muss so aussehen: Besitzer/Name, zum Beispiel M/ERP-System-fuer-Handwerksbetriebe.");
        }
        einstellungen.save(ADRESSE, a, "Gitea-Adresse für Wünsche an die Entwicklung");
        einstellungen.save(REPOSITORY, r, "Gitea-Repository für Wünsche an die Entwicklung");
        if (token != null) {
            String t = token.strip();
            if (t.isEmpty()) {
                einstellungen.save(TOKEN, "", "Gitea-Zugangsschlüssel für Wünsche an die Entwicklung");
            } else {
                if (!TOKEN_ZEICHEN.matcher(t).matches()) {
                    throw new IllegalArgumentException("Der Zugangsschlüssel sieht nicht richtig aus (8 bis 200 Zeichen, ohne Leerzeichen).");
                }
                einstellungen.save(TOKEN, geheim.isConfigured() ? geheim.encrypt(t) : t, "Gitea-Zugangsschlüssel für Wünsche an die Entwicklung");
            }
        }
        return stand();
    }

    /** Prüft die Verbindung mit dem gespeicherten Zugang: Das Repository wird gelesen, es wird nichts angelegt. */
    public Testergebnis teste() {
        Stand s = stand();
        if (!s.bereit()) {
            return new Testergebnis(false, "Adresse, Repository und Zugangsschlüssel sind noch nicht alle eingetragen.");
        }
        try {
            HttpResponse<String> r = senden(HttpRequest.newBuilder(URI.create(basis() + "/api/v1/repos/" + s.repository())).GET());
            return switch (r.statusCode()) {
                case 200 -> new Testergebnis(true, "Die Verbindung klappt: Das Repository " + s.repository() + " ist erreichbar.");
                case 401, 403 -> new Testergebnis(false, "Gitea lehnt den Zugangsschlüssel ab (Fehler " + r.statusCode() + "). Bitte den Schlüssel prüfen und neu eintragen.");
                case 404 -> new Testergebnis(false, "Das Repository " + s.repository() + " gibt es unter dieser Adresse nicht, oder der Schlüssel darf es nicht sehen.");
                default -> new Testergebnis(false, "Gitea antwortet mit Fehler " + r.statusCode() + ". Bitte Adresse und Repository prüfen.");
            };
        } catch (IOException e) {
            return new Testergebnis(false, "Gitea ist unter dieser Adresse nicht erreichbar. Bitte Adresse und Netzwerk prüfen.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Testergebnis(false, "Der Test wurde abgebrochen.");
        }
    }

    public Angelegt legeAn(String titel, String text) {
        return legeAn(titel, text, List.of());
    }

    /**
     * Legt das Issue an und setzt die Labels, die es im Repository gibt (nach Namen; unbekannte werden übersprungen). Wirft {@link IllegalStateException} mit Klartext, wenn
     * der Zugang fehlt oder Gitea ablehnt.
     */
    public Angelegt legeAn(String titel, String text, List<String> labelNamen) {
        if (!istBereit()) {
            throw new IllegalStateException("Der Zugang zu Gitea ist noch nicht eingerichtet (Einstellungen → KI → Wünsche an die Entwicklung).");
        }
        String t = titel == null ? "" : titel.replaceAll("\\p{Cntrl}", " ").strip();
        if (t.isEmpty() || t.length() > MAX_TITEL) {
            throw new IllegalArgumentException("Der Titel muss zwischen 1 und " + MAX_TITEL + " Zeichen lang sein.");
        }
        String b = text == null ? "" : text.strip();
        if (b.length() > MAX_TEXT) {
            throw new IllegalArgumentException("Die Beschreibung ist zu lang (höchstens " + MAX_TEXT + " Zeichen).");
        }
        try {
            var anfrage = JSON.createObjectNode().put("title", t).put("body", b);
            var ids = anfrage.putArray("labels");
            labelIds(labelNamen).forEach(ids::add);
            String body = JSON.writeValueAsString(anfrage);
            HttpResponse<String> r = senden(HttpRequest.newBuilder(URI.create(basis() + "/api/v1/repos/" + stand().repository() + "/issues"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
            if (r.statusCode() != 201 && r.statusCode() != 200) {
                String grund = r.statusCode() == 401 || r.statusCode() == 403 ? "Gitea lehnt den Zugangsschlüssel ab." : r.statusCode() == 404 ? "Das Repository wurde nicht gefunden." : "Gitea antwortet mit Fehler " + r.statusCode() + ".";
                throw new IllegalStateException("Der Wunsch konnte nicht angelegt werden. " + grund);
            }
            JsonNode n = JSON.readTree(r.body());
            return new Angelegt(n.path("number").asInt(), n.path("html_url").asText(""));
        } catch (IOException e) {
            throw new IllegalStateException("Gitea ist nicht erreichbar. Der Wunsch wurde nicht angelegt.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Das Anlegen wurde abgebrochen.");
        }
    }

    /**
     * Offene Issues, die zu diesem Text passen (höchstens 5, die besten zuerst): Vergleich der wichtigen Wörter (ab 4 Buchstaben, ohne Füllwörter) von Titel und Anfang des
     * Textes mit denen des Wunsches. Lesend; ohne Zugang oder bei Fehlern eine leere Liste, damit ein neuer Wunsch nie am Abgleich scheitert.
     */
    public List<Aehnlich> sucheAehnliche(String text) {
        if (!istBereit() || text == null || text.isBlank()) {
            return List.of();
        }
        java.util.Set<String> gesucht = woerter(text);
        if (gesucht.size() < 2) {
            return List.of();
        }
        List<Aehnlich> treffer = new ArrayList<>();
        try {
            for (int seite = 1; seite <= 3; seite++) {
                HttpResponse<String> r = senden(HttpRequest.newBuilder(URI.create(basis() + "/api/v1/repos/" + stand().repository() + "/issues?state=open&type=issues&limit=50&page=" + seite)).GET());
                if (r.statusCode() != 200) {
                    break;
                }
                JsonNode liste = JSON.readTree(r.body());
                for (JsonNode i : liste) {
                    String titel = i.path("title").asText("");
                    String inhalt = titel + " " + kopf(i.path("body").asText(""), 600);
                    long gemeinsam = woerter(inhalt).stream().filter(gesucht::contains).count();
                    // Mindestens zwei gemeinsame Wörter und die Hälfte der Wörter des Wunsches (bei langen Wünschen höchstens fünf nötig)
                    if (gemeinsam >= 2 && gemeinsam >= Math.min(5, (gesucht.size() + 1) / 2)) {
                        treffer.add(new Aehnlich(i.path("number").asInt(), titel, i.path("html_url").asText(""), (int) gemeinsam));
                    }
                }
                if (liste.size() < 50) {
                    break;
                }
            }
        } catch (IOException e) {
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        treffer.sort((a, b) -> Integer.compare(b.treffer(), a.treffer()));
        return treffer.size() > 5 ? new ArrayList<>(treffer.subList(0, 5)) : treffer;
    }

    /** Titel und Adresse eines offenen Issues; {@code null}, wenn es das nicht gibt (oder nicht offen ist). */
    public Aehnlich holeOffenes(int nummer) {
        if (!istBereit() || nummer <= 0) {
            return null;
        }
        try {
            HttpResponse<String> r = senden(HttpRequest.newBuilder(URI.create(basis() + "/api/v1/repos/" + stand().repository() + "/issues/" + nummer)).GET());
            if (r.statusCode() != 200) {
                return null;
            }
            JsonNode i = JSON.readTree(r.body());
            if (i.has("pull_request") && !i.path("pull_request").isNull() || !"open".equals(i.path("state").asText())) {
                return null;
            }
            return new Aehnlich(i.path("number").asInt(), i.path("title").asText(""), i.path("html_url").asText(""), 0);
        } catch (IOException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /** Schreibt einen Kommentar unter ein vorhandenes Issue statt ein neues anzulegen. */
    public Angelegt kommentiere(int nummer, String text) {
        if (!istBereit()) {
            throw new IllegalStateException("Der Zugang zu Gitea ist noch nicht eingerichtet (Einstellungen → KI → Wünsche an die Entwicklung).");
        }
        String b = text == null ? "" : text.strip();
        if (b.isEmpty() || b.length() > MAX_TEXT) {
            throw new IllegalArgumentException("Der Kommentar muss zwischen 1 und " + MAX_TEXT + " Zeichen lang sein.");
        }
        try {
            String body = JSON.writeValueAsString(JSON.createObjectNode().put("body", b));
            HttpResponse<String> r = senden(HttpRequest.newBuilder(URI.create(basis() + "/api/v1/repos/" + stand().repository() + "/issues/" + nummer + "/comments"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
            if (r.statusCode() != 201 && r.statusCode() != 200) {
                String grund = r.statusCode() == 401 || r.statusCode() == 403 ? "Gitea lehnt den Zugangsschlüssel ab." : r.statusCode() == 404 ? "Das Issue " + nummer + " gibt es nicht." : "Gitea antwortet mit Fehler " + r.statusCode() + ".";
                throw new IllegalStateException("Der Kommentar konnte nicht geschrieben werden. " + grund);
            }
            return new Angelegt(nummer, JSON.readTree(r.body()).path("html_url").asText(""));
        } catch (IOException e) {
            throw new IllegalStateException("Gitea ist nicht erreichbar. Der Kommentar wurde nicht geschrieben.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Das Schreiben wurde abgebrochen.");
        }
    }

    private static final java.util.Set<String> FUELLWOERTER = java.util.Set.of("dass", "diese", "dieser", "dieses", "wird", "werden", "soll", "sollen", "kann", "können", "koennen", "auch", "oder",
            "wenn", "dann", "eine", "einen", "einem", "einer", "eines", "sind", "nicht", "nach", "beim", "über", "ueber", "aber", "mehr", "noch", "wurde", "wurden", "sowie", "damit", "dabei");

    /** Wichtige Wörter eines Textes: klein, ohne Umlaute, mindestens vier Buchstaben, ohne Füllwörter. */
    static java.util.Set<String> woerter(String text) {
        String n = java.text.Normalizer.normalize(text.toLowerCase(java.util.Locale.GERMAN).replace("ß", "ss").replace("ä", "ae").replace("ö", "oe").replace("ü", "ue"), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        java.util.Set<String> menge = new java.util.LinkedHashSet<>();
        for (String w : n.split("[^a-z0-9]+")) {
            if (w.length() >= 4 && !FUELLWOERTER.contains(w)) {
                menge.add(w);
            }
        }
        return menge;
    }

    private static String kopf(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ------------------------------------------------------------------ innen

    /** Die Nummern der Labels mit diesen Namen im Repository; was es nicht gibt oder nicht lesbar ist, fehlt einfach (das Issue entsteht trotzdem). */
    private List<Long> labelIds(List<String> namen) {
        List<Long> ids = new ArrayList<>();
        if (namen == null || namen.isEmpty()) {
            return ids;
        }
        try {
            HttpResponse<String> r = senden(HttpRequest.newBuilder(URI.create(basis() + "/api/v1/repos/" + stand().repository() + "/labels?limit=100")).GET());
            if (r.statusCode() == 200) {
                for (JsonNode l : JSON.readTree(r.body())) {
                    if (namen.contains(l.path("name").asText()) && l.path("id").canConvertToLong()) {
                        ids.add(l.path("id").asLong());
                    }
                }
            }
        } catch (IOException e) {
            // ohne Labels weiter
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ids;
    }

    private String basis() {
        return wert(ADRESSE);
    }

    private String token() {
        String roh = wert(TOKEN);
        return roh.startsWith("v1:") ? geheim.decrypt(roh) : roh;
    }

    private String wert(String schluessel) {
        String w = einstellungen.get(schluessel, "");
        return w == null ? "" : w.strip();
    }

    private HttpResponse<String> senden(HttpRequest.Builder b) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
        return client.send(b.timeout(Duration.ofSeconds(20)).header("Authorization", "token " + token()).header("Accept", "application/json").build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void pruefeAdresse(String a) {
        URI u;
        try {
            u = URI.create(a);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Die Adresse sieht nicht richtig aus. Beispiel: http://192.168.1.20:3050");
        }
        if (u.getScheme() == null || !(u.getScheme().equals("http") || u.getScheme().equals("https")) || u.getHost() == null) {
            throw new IllegalArgumentException("Die Adresse muss mit http:// oder https:// beginnen. Beispiel: http://192.168.1.20:3050");
        }
        if (u.getUserInfo() != null || u.getQuery() != null || u.getFragment() != null) {
            throw new IllegalArgumentException("Die Adresse darf keinen Benutzer, keine Parameter und kein # enthalten.");
        }
        if (a.length() > 200) {
            throw new IllegalArgumentException("Die Adresse ist zu lang.");
        }
    }
}
