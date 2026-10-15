package org.example.kalkulationsprogramm.service.ki;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

/** Claude (Anthropic) über die Messages-Schnittstelle, mit Werkzeugaufrufen ({@code tool_use} und {@code tool_result}). */
public final class ClaudeAnbieter implements KiAnbieter {

    public static final String BASIS = "https://api.anthropic.com/v1";
    private static final String VERSION = "2023-06-01";

    private final String basis;
    private final String schluessel;
    private final String modell;
    private final HttpClient http;
    private final Duration frist;

    public ClaudeAnbieter(String basis, String schluessel, String modell, HttpClient http, Duration frist) {
        this.basis = basis == null || basis.isBlank() ? BASIS : basis;
        this.schluessel = schluessel;
        this.modell = modell;
        this.http = http == null ? HttpHilfe.STANDARD : http;
        this.frist = frist == null ? Duration.ofSeconds(120) : frist;
    }

    @Override
    public AnbieterTyp typ() {
        return AnbieterTyp.CLAUDE;
    }

    @Override
    public String modell() {
        return modell;
    }

    @Override
    public boolean liest(String mime) {
        return mime != null && (mime.equals("image/jpeg") || mime.equals("image/png") || mime.equals("image/gif") || mime.equals("image/webp") || mime.equals("application/pdf"));
    }

    @Override
    public KiAntwort chat(KiAnfrage a) throws KiAnbieterFehler, InterruptedException {
        pruefeAnhaenge(a);
        ObjectNode body = HttpHilfe.JSON.createObjectNode();
        body.put("model", modell);
        body.put("max_tokens", a.maxTokens());
        body.put("temperature", Math.min(1.0, Math.max(0.0, a.temperatur())));
        if (a.system() != null && !a.system().isBlank()) {
            body.put("system", a.system());
        }
        body.set("messages", nachrichten(a.nachrichten()));
        if (!a.werkzeuge().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (var w : a.werkzeuge()) {
                ObjectNode t = tools.addObject().put("name", w.name()).put("description", w.beschreibung());
                t.set("input_schema", w.parameter());
            }
        }
        JsonNode root = HttpHilfe.sende(http, basis + "/messages", Map.of("x-api-key", schluessel == null ? "" : schluessel, "anthropic-version", VERSION),
                body, frist, "Claude", "");
        StringBuilder text = new StringBuilder();
        List<KiWerkzeugAufruf> aufrufe = new ArrayList<>();
        for (JsonNode block : root.path("content")) {
            String typ = block.path("type").asText("");
            if ("text".equals(typ)) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(block.path("text").asText(""));
            } else if ("tool_use".equals(typ)) {
                JsonNode input = block.path("input");
                aufrufe.add(new KiWerkzeugAufruf(block.path("id").asText(""), block.path("name").asText(""),
                        input.isObject() ? input : HttpHilfe.JSON.createObjectNode()));
            }
        }
        if (text.toString().isBlank() && aufrufe.isEmpty()) {
            throw new KiAnbieterFehler("Claude hat keine Antwort geliefert.");
        }
        return new KiAntwort(text.toString().strip(), aufrufe);
    }

    /** Aufeinanderfolgende Werkzeugergebnisse gehören bei Claude in <b>eine</b> Nutzernachricht. */
    private static ArrayNode nachrichten(List<KiNachricht> liste) {
        ArrayNode m = HttpHilfe.JSON.createArrayNode();
        ArrayNode ergebnisse = null;
        for (KiNachricht n : liste) {
            if (n instanceof Werkzeugergebnis r) {
                if (ergebnisse == null) {
                    ObjectNode o = m.addObject().put("role", "user");
                    ergebnisse = o.putArray("content");
                }
                ergebnisse.addObject().put("type", "tool_result").put("tool_use_id", r.aufrufId()).put("content", r.inhalt());
                continue;
            }
            ergebnisse = null;
            if (n instanceof Nutzer u) {
                if (u.anhaenge().isEmpty()) {
                    m.addObject().put("role", "user").put("content", u.text());
                } else {
                    // Dateien stehen vor dem Text, so liest Claude sie am besten
                    ArrayNode inhalt = m.addObject().put("role", "user").putArray("content");
                    for (var anhang : u.anhaenge()) {
                        ObjectNode block = inhalt.addObject().put("type", anhang.istPdf() ? "document" : "image");
                        ObjectNode quelle = block.putObject("source");
                        quelle.put("type", "base64").put("media_type", anhang.mime()).put("data", anhang.base64());
                    }
                    if (u.text() != null && !u.text().isEmpty()) {
                        inhalt.addObject().put("type", "text").put("text", u.text());
                    }
                }
            } else if (n instanceof Assistent as) {
                ObjectNode o = m.addObject().put("role", "assistant");
                ArrayNode inhalt = o.putArray("content");
                if (!as.text().isBlank()) {
                    inhalt.addObject().put("type", "text").put("text", as.text());
                }
                for (KiWerkzeugAufruf c : as.aufrufe()) {
                    ObjectNode t = inhalt.addObject().put("type", "tool_use").put("id", c.id()).put("name", c.name());
                    t.set("input", c.argumente() == null ? HttpHilfe.JSON.createObjectNode() : c.argumente());
                }
            }
        }
        return m;
    }
}
