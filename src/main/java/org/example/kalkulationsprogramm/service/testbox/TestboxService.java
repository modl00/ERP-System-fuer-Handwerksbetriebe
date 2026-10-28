package org.example.kalkulationsprogramm.service.testbox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTextWeiche;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Test-Box: Der Administrator sieht die offenen Rückfragen und die Dinge zum Testen aus Gitea, antwortet in einem Satz, die KI bereitet die Antwort als Kommentar auf,
 * und nach Bestätigung geht sie als Kommentar ins Issue. Die KI formuliert und ordnet nur ein: Sie schließt nichts und ändert keine Labels.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestboxService {

    static final List<String> LABELS = List.of("Wartet auf Antwort", "Im Pull Request");
    static final Set<String> EINORDNUNGEN = Set.of("FUNKTIONIERT", "PROBLEM", "WUNSCH", "ENTSCHEIDUNG", "ANTWORT");
    static final int MAX_FRAGE = 1500;
    static final int MAX_ANTWORT = 4000;
    private static final Pattern REPO = Pattern.compile("^[\\w.-]+/[\\w.-]+$");
    private static final String CLAUDE_FUSS = "Generated with [Claude Code]";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM = """
            Du hilfst einem Handwerker, seine Rückmeldung zu einer neuen Programmfunktion als kurzen, sachlichen Kommentar für ein Entwickler-Ticket aufzuschreiben.
            Du bekommst den Titel des Tickets, die letzte Frage oder den Testhinweis und die Antwort des Handwerkers.
            Schreibe auf Deutsch in ein bis vier kurzen Sätzen, was der Handwerker gesagt hat. Erfinde nichts dazu und lass nichts weg. Keine Anrede, kein Gruß.
            Ordne die Antwort ein als: FUNKTIONIERT (es klappt), PROBLEM (Fehler oder Verhalten, das nicht stimmt), WUNSCH (Verbesserung oder neue Idee),
            ENTSCHEIDUNG (beantwortet eine Frage mit einer Wahl) oder ANTWORT (sonstige Antwort).
            Antworte ausschließlich als JSON: {"einordnung":"<eine der fünf>","kommentar":"<der Text>"}
            """;

    private final GiteaClient gitea;
    private final SystemSettingsService einstellungen;
    private final KiTextWeiche weiche;

    // ───────────────────────────── Einstellung ─────────────────────────────

    public record Einstellung(String url, String repo, boolean tokenGesetzt) { }

    public Einstellung einstellung() {
        return new Einstellung(einstellungen.get(GiteaClient.URL_SCHLUESSEL, ""), einstellungen.get(GiteaClient.REPO_SCHLUESSEL, ""),
                !einstellungen.get(GiteaClient.TOKEN_SCHLUESSEL, "").isBlank());
    }

    /** {@code token} null = unverändert, leer = entfernen. */
    public Einstellung speichereEinstellung(String url, String repo, String token) {
        String u = url == null ? "" : url.strip();
        String r = repo == null ? "" : repo.strip();
        if (!u.isEmpty() && !(u.startsWith("http://") || u.startsWith("https://"))) {
            throw new IllegalArgumentException("Die Adresse muss mit http:// oder https:// beginnen.");
        }
        if (u.length() > 200) {
            throw new IllegalArgumentException("Die Adresse ist zu lang.");
        }
        if (!r.isEmpty() && !REPO.matcher(r).matches()) {
            throw new IllegalArgumentException("Das Repository bitte so angeben: Besitzer/Name.");
        }
        if (token != null && token.strip().length() > 200) {
            throw new IllegalArgumentException("Der Zugangsschlüssel ist zu lang.");
        }
        einstellungen.save(GiteaClient.URL_SCHLUESSEL, u, "Test-Box: Adresse von Gitea");
        einstellungen.save(GiteaClient.REPO_SCHLUESSEL, r, "Test-Box: Repository in Gitea");
        if (token != null) {
            einstellungen.save(GiteaClient.TOKEN_SCHLUESSEL, token.strip(), "Test-Box: Zugangsschlüssel für Gitea");
        }
        return einstellung();
    }

    // ───────────────────────────── Offene Punkte ─────────────────────────────

    /** {@code wartet}: Die letzte Nachricht stammt von Claude (oder es gibt noch keine), jetzt ist der Anwender dran. */
    public record Punkt(int nummer, String titel, String url, List<String> labels, String frage, boolean wartet) { }

    public List<Punkt> punkte() {
        Map<Integer, Punkt> ergebnis = new LinkedHashMap<>();
        for (String label : LABELS) {
            JsonNode issues = gitea.lies("/issues?state=open&type=issues&limit=50&labels=" + GiteaClient.kodiere(label));
            if (!issues.isArray()) {
                continue;
            }
            for (JsonNode i : issues) {
                int nummer = i.path("number").asInt();
                if (nummer > 0 && !ergebnis.containsKey(nummer)) {
                    ergebnis.put(nummer, punkt(i, nummer));
                }
            }
        }
        List<Punkt> liste = new ArrayList<>(ergebnis.values());
        liste.sort((a, b) -> Boolean.compare(b.wartet(), a.wartet()) != 0 ? Boolean.compare(b.wartet(), a.wartet()) : Integer.compare(b.nummer(), a.nummer()));
        return liste;
    }

    private Punkt punkt(JsonNode issue, int nummer) {
        JsonNode kommentare = gitea.lies("/issues/" + nummer + "/comments");
        String frage = null;
        boolean letzteVonClaude = true;
        if (kommentare.isArray()) {
            for (JsonNode k : kommentare) {
                boolean claude = k.path("body").asText("").contains(CLAUDE_FUSS);
                letzteVonClaude = claude;
                if (claude) {
                    frage = k.path("body").asText();
                }
            }
        }
        if (frage == null) {
            frage = issue.path("body").asText("");
        }
        List<String> labels = new ArrayList<>();
        issue.path("labels").forEach(l -> labels.add(l.path("name").asText()));
        return new Punkt(nummer, issue.path("title").asText(), issue.path("html_url").asText(""), labels, kuerze(bereinige(frage), MAX_FRAGE), letzteVonClaude);
    }

    static String bereinige(String text) {
        return text == null ? "" : text.replaceAll("(?m)^\\s*🤖.*$", "").replaceAll("<img[^>]*>", "").strip();
    }

    static String kuerze(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max).stripTrailing() + " …";
    }

    // ───────────────────────────── Aufbereiten und Senden ─────────────────────────────

    /** Der Vorschlag für den Kommentar. {@code ki}: wurde die KI genutzt? Fehlt sie oder antwortet sie nicht, bleibt der Text des Anwenders, wie er ist. */
    public record Vorschlag(String einordnung, String kommentar, boolean ki, String hinweis) { }

    public Vorschlag aufbereiten(int nummer, String antwort, String einordnung) {
        String text = pruefeAntwort(antwort);
        String wahl = EINORDNUNGEN.contains(einordnung) ? einordnung : "ANTWORT";
        if ("FUNKTIONIERT".equals(wahl) && text.length() <= 3) {
            return new Vorschlag("FUNKTIONIERT", "Funktioniert wie erwartet.", false, null);
        }
        Punkt p = punkte().stream().filter(x -> x.nummer() == nummer).findFirst().orElse(null);
        String nutzer = "Titel des Tickets: " + (p == null ? "#" + nummer : p.titel()) + "\n\nLetzte Frage oder Testhinweis:\n" + (p == null ? "(unbekannt)" : p.frage())
                + "\n\nAntwort des Handwerkers:\n" + text;
        try {
            JsonNode a = JSON.readTree(weiche.frage(KiFunktion.TESTBOX, SYSTEM, nutzer, 0.2, 600, true));
            String k = a.path("kommentar").asText("").strip();
            String e = a.path("einordnung").asText("");
            if (k.isEmpty()) {
                return new Vorschlag(wahl, text, false, "Die KI hat nichts geliefert. Ihr Text bleibt, wie er ist.");
            }
            return new Vorschlag(EINORDNUNGEN.contains(e) ? e : wahl, kuerze(k, MAX_ANTWORT), true, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Vorschlag(wahl, text, false, "Die Anfrage an die KI wurde abgebrochen. Ihr Text bleibt, wie er ist.");
        } catch (IOException | RuntimeException e) {
            log.warn("[Testbox] KI nicht verfügbar: {}", e.getMessage());
            return new Vorschlag(wahl, text, false, "Die KI ist nicht erreichbar oder nicht eingerichtet. Ihr Text bleibt, wie er ist.");
        }
    }

    /** Schreibt den Kommentar ins Issue. Er trägt den Zusatz „über die Test-Box“ und den Originaltext, aber nicht den Claude-Fuß. */
    public void senden(int nummer, String einordnung, String kommentar, String original) {
        if (nummer <= 0) {
            throw new IllegalArgumentException("Bitte ein Ticket wählen.");
        }
        String text = pruefeAntwort(kommentar);
        String wahl = EINORDNUNGEN.contains(einordnung) ? einordnung : "ANTWORT";
        StringBuilder sb = new StringBuilder("**Rückmeldung über die Test-Box** (").append(anzeige(wahl)).append(")\n\n").append(text);
        if (original != null && !original.isBlank() && !original.strip().equals(text)) {
            sb.append("\n\n> Originaltext: ").append(kuerze(original.strip(), MAX_ANTWORT).replace("\n", "\n> "));
        }
        gitea.lies("/issues/" + nummer);
        gitea.schreibe("/issues/" + nummer + "/comments", Map.of("body", sb.toString()));
    }

    private static String pruefeAntwort(String antwort) {
        String t = antwort == null ? "" : antwort.strip();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Bitte zuerst etwas eintragen.");
        }
        if (t.length() > MAX_ANTWORT) {
            throw new IllegalArgumentException("Der Text ist zu lang (höchstens " + MAX_ANTWORT + " Zeichen).");
        }
        return t;
    }

    static String anzeige(String einordnung) {
        return switch (einordnung) {
            case "FUNKTIONIERT" -> "funktioniert";
            case "PROBLEM" -> "Problem";
            case "WUNSCH" -> "Wunsch";
            case "ENTSCHEIDUNG" -> "Entscheidung";
            default -> "Antwort";
        };
    }
}
