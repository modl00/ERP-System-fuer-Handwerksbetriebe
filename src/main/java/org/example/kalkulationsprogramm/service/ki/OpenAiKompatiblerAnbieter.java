package org.example.kalkulationsprogramm.service.ki;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.service.ki.KiTypen.Assistent;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAntwort;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiNachricht;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiWerkzeugAufruf;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Nutzer;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Werkzeugergebnis;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Anbieter im OpenAI-Format ({@code /chat/completions}): die lokale KI (Ollama, LM Studio, vLLM, llama.cpp) und Mistral. Beide sprechen dasselbe Format
 * mit Werkzeugaufrufen ({@code tools}, {@code tool_calls}).
 */
public final class OpenAiKompatiblerAnbieter implements KiAnbieter {

    private static final Pattern DENKTEXT = Pattern.compile("(?s)<think>.*?</think>");

    private final AnbieterTyp typ;
    private final String basis;
    private final String schluessel;
    private final String modell;
    private final HttpClient http;
    private final Duration frist;

    public OpenAiKompatiblerAnbieter(AnbieterTyp typ, String basis, String schluessel, String modell, HttpClient http, Duration frist) {
        this.typ = typ;
        this.basis = basis;
        this.schluessel = schluessel == null ? "" : schluessel;
        this.modell = modell;
        this.http = http == null ? HttpHilfe.STANDARD : http;
        this.frist = frist == null ? Duration.ofSeconds(180) : frist;
    }

    @Override
    public AnbieterTyp typ() {
        return typ;
    }

    @Override
    public String modell() {
        return modell;
    }

    /** Bilder gehen als Bild-Anhang; PDF und Ton kennt das gemeinsame Format nicht (bei PDFs liest das Programm vorher den Text). */
    @Override
    public boolean liest(String mime) {
        return mime != null && (mime.equals("image/jpeg") || mime.equals("image/png") || mime.equals("image/gif") || mime.equals("image/webp"));
    }

    @Override
    public KiAntwort chat(KiAnfrage a) throws KiAnbieterFehler, InterruptedException {
        pruefeAnhaenge(a);
        ObjectNode body = HttpHilfe.JSON.createObjectNode();
        body.put("model", modell);
        body.put("stream", false);
        body.put("temperature", a.temperatur());
        body.put("max_tokens", a.maxTokens());
        body.set("messages", nachrichten(a));
        if (!a.werkzeuge().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (var w : a.werkzeuge()) {
                ObjectNode f = tools.addObject().put("type", "function").putObject("function");
                f.put("name", w.name()).put("description", w.beschreibung());
                f.set("parameters", w.parameter());
            }
        }
        Map<String, String> kopf = new HashMap<>();
        if (!schluessel.isEmpty()) {
            kopf.put("Authorization", "Bearer " + schluessel);
        }
        String womit = typ == AnbieterTyp.LOKAL ? "Die lokale KI" : typ.anzeige();
        String hinweis = typ == AnbieterTyp.LOKAL ? " Läuft Ollama, stimmt die Adresse und ist Port 11434 in der Firewall frei?" : "";
        JsonNode root = HttpHilfe.sende(http, basis + "/chat/completions", kopf, body, frist, womit, hinweis);
        return antwort(root, womit);
    }

    private static ArrayNode nachrichten(KiAnfrage a) {
        ArrayNode m = HttpHilfe.JSON.createArrayNode();
        if (a.system() != null && !a.system().isBlank()) {
            m.addObject().put("role", "system").put("content", a.system());
        }
        for (KiNachricht n : a.nachrichten()) {
            if (n instanceof Nutzer u) {
                if (u.anhaenge().isEmpty()) {
                    m.addObject().put("role", "user").put("content", u.text());
                } else {
                    ArrayNode inhalt = m.addObject().put("role", "user").putArray("content");
                    for (var anhang : u.anhaenge()) {
                        inhalt.addObject().put("type", "image_url").putObject("image_url").put("url", "data:" + anhang.mime() + ";base64," + anhang.base64());
                    }
                    if (u.text() != null && !u.text().isEmpty()) {
                        inhalt.addObject().put("type", "text").put("text", u.text());
                    }
                }
            } else if (n instanceof Assistent as) {
                ObjectNode o = m.addObject().put("role", "assistant");
                if (as.text().isBlank()) {
                    o.putNull("content");
                } else {
                    o.put("content", as.text());
                }
                if (!as.aufrufe().isEmpty()) {
                    ArrayNode calls = o.putArray("tool_calls");
                    for (KiWerkzeugAufruf c : as.aufrufe()) {
                        ObjectNode call = calls.addObject().put("id", c.id()).put("type", "function");
                        call.putObject("function").put("name", c.name()).put("arguments", c.argumente() == null ? "{}" : c.argumente().toString());
                    }
                }
            } else if (n instanceof Werkzeugergebnis r) {
                m.addObject().put("role", "tool").put("tool_call_id", r.aufrufId()).put("content", r.inhalt());
            }
        }
        return m;
    }

    private static KiAntwort antwort(JsonNode root, String womit) throws KiAnbieterFehler {
        JsonNode msg = root.path("choices").path(0).path("message");
        if (msg.isMissingNode()) {
            throw new KiAnbieterFehler(womit + " hat keine Antwort geliefert.");
        }
        String text = DENKTEXT.matcher(msg.path("content").asText("")).replaceAll("").strip();
        List<KiWerkzeugAufruf> aufrufe = new ArrayList<>();
        for (JsonNode c : msg.path("tool_calls")) {
            JsonNode f = c.path("function");
            String name = f.path("name").asText("");
            if (name.isBlank()) {
                continue;
            }
            aufrufe.add(new KiWerkzeugAufruf(c.path("id").asText("call_" + UUID.randomUUID()), name, argumente(f.path("arguments"))));
        }
        if (text.isEmpty() && aufrufe.isEmpty()) {
            throw new KiAnbieterFehler(womit + " hat keine Antwort geliefert.");
        }
        return new KiAntwort(text, aufrufe);
    }

    /** Die Argumente kommen meist als Text (JSON in einer Zeichenkette), manche Server liefern schon ein Objekt. Unlesbares wird zu einem leeren Objekt. */
    static JsonNode argumente(JsonNode roh) {
        if (roh == null || roh.isMissingNode() || roh.isNull()) {
            return HttpHilfe.JSON.createObjectNode();
        }
        if (roh.isObject()) {
            return roh;
        }
        try {
            JsonNode n = HttpHilfe.JSON.readTree(roh.asText("{}"));
            return n != null && n.isObject() ? n : HttpHilfe.JSON.createObjectNode();
        } catch (Exception e) {
            return HttpHilfe.JSON.createObjectNode();
        }
    }
}
