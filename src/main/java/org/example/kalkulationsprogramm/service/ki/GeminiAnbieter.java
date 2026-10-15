package org.example.kalkulationsprogramm.service.ki;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.example.kalkulationsprogramm.service.ki.KiTypen.Assistent;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAntwort;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiNachricht;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiWerkzeugAufruf;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Nutzer;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Werkzeugergebnis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Gemini (Google) mit Werkzeugaufrufen ({@code functionDeclarations}, {@code functionCall}, {@code functionResponse}). */
public final class GeminiAnbieter implements KiAnbieter {

    public static final String BASIS = "https://generativelanguage.googleapis.com/v1beta";

    private final String basis;
    private final String schluessel;
    private final String modell;
    private final HttpClient http;
    private final Duration frist;

    public GeminiAnbieter(String basis, String schluessel, String modell, HttpClient http, Duration frist) {
        this.basis = basis == null || basis.isBlank() ? BASIS : basis;
        this.schluessel = schluessel;
        this.modell = modell;
        this.http = http == null ? HttpHilfe.STANDARD : http;
        this.frist = frist == null ? Duration.ofSeconds(90) : frist;
    }

    @Override
    public AnbieterTyp typ() {
        return AnbieterTyp.GEMINI;
    }

    @Override
    public String modell() {
        return modell;
    }

    @Override
    public boolean liest(String mime) {
        return mime != null && (mime.startsWith("image/") || mime.startsWith("audio/") || mime.equals("application/pdf"));
    }

    @Override
    public KiAntwort chat(KiAnfrage a) throws KiAnbieterFehler, InterruptedException {
        pruefeAnhaenge(a);
        ObjectNode body = HttpHilfe.JSON.createObjectNode();
        if (a.system() != null && !a.system().isBlank()) {
            body.putObject("systemInstruction").putArray("parts").addObject().put("text", a.system());
        }
        body.set("contents", inhalte(a.nachrichten()));
        if (!a.werkzeuge().isEmpty()) {
            ArrayNode d = body.putArray("tools").addObject().putArray("functionDeclarations");
            for (var w : a.werkzeuge()) {
                ObjectNode f = d.addObject().put("name", w.name()).put("description", w.beschreibung());
                f.set("parameters", w.parameter());
            }
        }
        ObjectNode cfg = body.putObject("generationConfig");
        cfg.put("temperature", a.temperatur());
        cfg.put("maxOutputTokens", a.maxTokens());
        JsonNode root = HttpHilfe.sende(http, basis + "/models/" + modell + ":generateContent", Map.of("x-goog-api-key", schluessel == null ? "" : schluessel),
                body, frist, "Gemini", "");
        StringBuilder text = new StringBuilder();
        List<KiWerkzeugAufruf> aufrufe = new ArrayList<>();
        for (JsonNode teil : root.path("candidates").path(0).path("content").path("parts")) {
            if (teil.hasNonNull("text")) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(teil.path("text").asText(""));
            }
            JsonNode fc = teil.path("functionCall");
            if (fc.isObject()) {
                JsonNode args = fc.path("args");
                aufrufe.add(new KiWerkzeugAufruf("call_" + UUID.randomUUID(), fc.path("name").asText(""), args.isObject() ? args : HttpHilfe.JSON.createObjectNode()));
            }
        }
        if (text.toString().isBlank() && aufrufe.isEmpty()) {
            throw new KiAnbieterFehler("Gemini hat keine Antwort geliefert.");
        }
        return new KiAntwort(text.toString().strip(), aufrufe);
    }

    private static ArrayNode inhalte(List<KiNachricht> liste) {
        ArrayNode c = HttpHilfe.JSON.createArrayNode();
        ArrayNode ergebnisse = null;
        for (KiNachricht n : liste) {
            if (n instanceof Werkzeugergebnis r) {
                // Mehrere Ergebnisse hintereinander gehören in eine Nachricht
                if (ergebnisse == null) {
                    ergebnisse = c.addObject().put("role", "user").putArray("parts");
                }
                ObjectNode fr = ergebnisse.addObject().putObject("functionResponse");
                fr.put("name", r.name());
                fr.putObject("response").put("result", r.inhalt());
                continue;
            }
            ergebnisse = null;
            if (n instanceof Nutzer u) {
                ArrayNode teile = c.addObject().put("role", "user").putArray("parts");
                for (var anhang : u.anhaenge()) {
                    ObjectNode inline = teile.addObject().putObject("inlineData");
                    inline.put("mimeType", anhang.mime());
                    inline.put("data", anhang.base64());
                }
                if (u.text() != null && !u.text().isEmpty() || u.anhaenge().isEmpty()) {
                    teile.addObject().put("text", u.text() == null ? "" : u.text());
                }
            } else if (n instanceof Assistent as) {
                ArrayNode teile = c.addObject().put("role", "model").putArray("parts");
                if (!as.text().isBlank()) {
                    teile.addObject().put("text", as.text());
                }
                for (KiWerkzeugAufruf k : as.aufrufe()) {
                    ObjectNode fc = teile.addObject().putObject("functionCall");
                    fc.put("name", k.name());
                    fc.set("args", k.argumente() == null ? HttpHilfe.JSON.createObjectNode() : k.argumente());
                }
            }
        }
        return c;
    }
}
