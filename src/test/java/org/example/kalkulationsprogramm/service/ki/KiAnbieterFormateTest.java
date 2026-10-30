package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.example.kalkulationsprogramm.service.ki.KiTypen.Anhang;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Assistent;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAntwort;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiWerkzeugAufruf;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiWerkzeugBeschreibung;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Nutzer;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Werkzeugergebnis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Die drei Formate (OpenAI-kompatibel für lokal und Mistral, Claude, Gemini) gegen einen kleinen Ersatz-Server auf dem eigenen Rechner:
 * Was geht raus (Werkzeuge, Verlauf, Schlüssel), was kommt zurück (Text, Werkzeugaufrufe, Fehler in Klartext).
 */
class KiAnbieterFormateTest {

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private String basis;
    private final AtomicReference<String> pfad = new AtomicReference<>();
    private final AtomicReference<JsonNode> anfrage = new AtomicReference<>();
    private final AtomicReference<com.sun.net.httpserver.Headers> kopf = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String antwort = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::behandle);
        server.start();
        basis = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopp() {
        server.stop(0);
    }

    private void behandle(HttpExchange ex) throws IOException {
        pfad.set(ex.getRequestURI().getPath());
        kopf.set(ex.getRequestHeaders());
        anfrage.set(json.readTree(ex.getRequestBody().readAllBytes()));
        byte[] b = antwort.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private KiWerkzeugBeschreibung werkzeug() throws IOException {
        return new KiWerkzeugBeschreibung("artikel_suchen", "Sucht Artikel", json.readTree("{\"type\":\"object\",\"properties\":{\"q\":{\"type\":\"string\"}},\"required\":[\"q\"]}"));
    }

    private KiAnfrage gespraech() throws IOException {
        JsonNode args = json.readTree("{\"q\":\"rundrohr\"}");
        return new KiAnfrage("Sei kurz.", List.of(new Nutzer("Finde Rundrohr"), new Assistent("Ich suche.", List.of(new KiWerkzeugAufruf("id1", "artikel_suchen", args))),
                new Werkzeugergebnis("id1", "artikel_suchen", "ID 7: Rundrohr"), new Nutzer("Danke")), List.of(werkzeug()), 0.2, 300);
    }

    // ------------------------------------------------------------------ OpenAI-kompatibel (lokal, Mistral)

    @Test
    void openAiSendetModellVerlaufWerkzeugeUndSchluessel() throws Exception {
        antwort = "{\"choices\":[{\"message\":{\"content\":\"Gefunden.\"}}]}";
        var a = new OpenAiKompatiblerAnbieter(AnbieterTyp.MISTRAL, basis + "/v1", "geheim", "mistral-small-latest", null, Duration.ofSeconds(10));

        KiAntwort r = a.chat(gespraech());

        assertThat(r.text()).isEqualTo("Gefunden.");
        assertThat(pfad.get()).isEqualTo("/v1/chat/completions");
        assertThat(kopf.get().getFirst("Authorization")).isEqualTo("Bearer geheim");
        JsonNode b = anfrage.get();
        assertThat(b.path("model").asText()).isEqualTo("mistral-small-latest");
        assertThat(b.path("stream").asBoolean()).isFalse();
        JsonNode m = b.path("messages");
        assertThat(m.get(0).path("role").asText()).isEqualTo("system");
        assertThat(m.get(2).path("tool_calls").get(0).path("function").path("name").asText()).isEqualTo("artikel_suchen");
        assertThat(m.get(2).path("tool_calls").get(0).path("function").path("arguments").asText()).contains("rundrohr");
        assertThat(m.get(3).path("role").asText()).isEqualTo("tool");
        assertThat(m.get(3).path("tool_call_id").asText()).isEqualTo("id1");
        assertThat(b.path("tools").get(0).path("function").path("parameters").path("required").get(0).asText()).isEqualTo("q");
    }

    @Test
    void openAiLiestWerkzeugaufrufeMitTextargumentenUndEntferntDenkText() throws Exception {
        antwort = "{\"choices\":[{\"message\":{\"content\":\"<think>hm</think>\",\"tool_calls\":[{\"id\":\"c9\",\"type\":\"function\",\"function\":{\"name\":\"artikel_suchen\",\"arguments\":\"{\\\"q\\\":\\\"blech\\\"}\"}}]}}]}";
        var a = new OpenAiKompatiblerAnbieter(AnbieterTyp.LOKAL, basis + "/v1", "", "qwen2.5:7b", null, null);

        KiAntwort r = a.chat(gespraech());

        assertThat(r.text()).isEmpty();
        assertThat(r.aufrufe()).hasSize(1);
        assertThat(r.aufrufe().get(0).id()).isEqualTo("c9");
        assertThat(r.aufrufe().get(0).argumente().path("q").asText()).isEqualTo("blech");
        assertThat(kopf.get().getFirst("Authorization")).as("ohne Schlüssel keine Kopfzeile").isNull();
    }

    @Test
    void openAiKommtMitUnlesbarenArgumentenZurecht() throws Exception {
        antwort = "{\"choices\":[{\"message\":{\"tool_calls\":[{\"function\":{\"name\":\"x\",\"arguments\":\"das ist kein json\"}}]}}]}";
        var a = new OpenAiKompatiblerAnbieter(AnbieterTyp.LOKAL, basis + "/v1", "", "m", null, null);

        KiAntwort r = a.chat(gespraech());

        assertThat(r.aufrufe().get(0).argumente().isObject()).isTrue();
        assertThat(r.aufrufe().get(0).argumente().size()).isZero();
        assertThat(r.aufrufe().get(0).id()).startsWith("call_");
    }

    @Test
    void openAiMeldetLeereAntwortUndFehlerInKlartext() throws Exception {
        var a = new OpenAiKompatiblerAnbieter(AnbieterTyp.LOKAL, basis + "/v1", "", "m", null, null);
        antwort = "{\"choices\":[{\"message\":{\"content\":\"\"}}]}";
        assertThatThrownBy(() -> a.chat(gespraech())).isInstanceOf(KiAnbieterFehler.class).hasMessageContaining("keine Antwort");

        status = 404;
        assertThatThrownBy(() -> a.chat(gespraech())).hasMessageContaining("gibt es nicht");
        status = 401;
        assertThatThrownBy(() -> new OpenAiKompatiblerAnbieter(AnbieterTyp.MISTRAL, basis + "/v1", "x", "m", null, null).chat(gespraech()))
                .hasMessageContaining("Mistral").hasMessageContaining("Schlüssel");
        status = 429;
        assertThatThrownBy(() -> a.chat(gespraech())).hasMessageContaining("Kontingent");
    }

    @Test
    void nichtErreichbarerRechnerGibtHinweisAufOllama() throws Exception {
        int frei;
        try (ServerSocket s = new ServerSocket(0)) {
            frei = s.getLocalPort();
        }
        var a = new OpenAiKompatiblerAnbieter(AnbieterTyp.LOKAL, "http://127.0.0.1:" + frei + "/v1", "", "m", null, null);

        assertThatThrownBy(() -> a.chat(gespraech())).isInstanceOf(KiAnbieterFehler.class).hasMessageContaining("nicht erreichbar").hasMessageContaining("Ollama");
    }

    // ------------------------------------------------------------------ Claude

    @Test
    void claudeSendetSchluesselVersionWerkzeugeUndGruppiertWerkzeugergebnisse() throws Exception {
        antwort = "{\"content\":[{\"type\":\"text\",\"text\":\"Hier.\"}],\"stop_reason\":\"end_turn\"}";
        var a = new ClaudeAnbieter(basis + "/v1", "sk-geheim", "claude-sonnet-5-5", null, Duration.ofSeconds(10));
        JsonNode args = json.readTree("{\"q\":\"a\"}");
        KiAnfrage zwei = new KiAnfrage("Sei kurz.", List.of(new Nutzer("Finde"),
                new Assistent("", List.of(new KiWerkzeugAufruf("t1", "artikel_suchen", args), new KiWerkzeugAufruf("t2", "artikel_suchen", args))),
                new Werkzeugergebnis("t1", "artikel_suchen", "eins"), new Werkzeugergebnis("t2", "artikel_suchen", "zwei")), List.of(werkzeug()), 0.5, 200);

        KiAntwort r = a.chat(zwei);

        assertThat(r.text()).isEqualTo("Hier.");
        assertThat(pfad.get()).isEqualTo("/v1/messages");
        assertThat(kopf.get().getFirst("x-api-key")).isEqualTo("sk-geheim");
        assertThat(kopf.get().getFirst("anthropic-version")).isEqualTo("2023-06-01");
        JsonNode b = anfrage.get();
        assertThat(b.path("system").asText()).isEqualTo("Sei kurz.");
        assertThat(b.path("max_tokens").asInt()).isEqualTo(200);
        assertThat(b.path("tools").get(0).path("input_schema").path("type").asText()).isEqualTo("object");
        JsonNode m = b.path("messages");
        assertThat(m.size()).as("Nutzer, Assistent, EINE Nutzernachricht mit beiden Ergebnissen").isEqualTo(3);
        assertThat(m.get(1).path("content").get(0).path("type").asText()).isEqualTo("tool_use");
        assertThat(m.get(1).path("content").size()).isEqualTo(2);
        assertThat(m.get(2).path("role").asText()).isEqualTo("user");
        assertThat(m.get(2).path("content").size()).isEqualTo(2);
        assertThat(m.get(2).path("content").get(1).path("tool_use_id").asText()).isEqualTo("t2");
    }

    @Test
    void claudeLiestTextUndWerkzeugaufrufe() throws Exception {
        antwort = "{\"content\":[{\"type\":\"text\",\"text\":\"Ich suche.\"},{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"artikel_suchen\",\"input\":{\"q\":\"rohr\"}}]}";
        var a = new ClaudeAnbieter(basis + "/v1", "k", "m", null, null);

        KiAntwort r = a.chat(gespraech());

        assertThat(r.text()).isEqualTo("Ich suche.");
        assertThat(r.aufrufe()).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo("toolu_1");
            assertThat(c.argumente().path("q").asText()).isEqualTo("rohr");
        });
    }

    @Test
    void claudeFehlerInKlartext() throws Exception {
        var a = new ClaudeAnbieter(basis + "/v1", "k", "m", null, null);
        status = 401;
        assertThatThrownBy(() -> a.chat(gespraech())).hasMessageContaining("Claude").hasMessageContaining("Schlüssel");
        status = 529;
        assertThatThrownBy(() -> a.chat(gespraech())).hasMessageContaining("überlastet");
        status = 200;
        antwort = "{\"content\":[]}";
        assertThatThrownBy(() -> a.chat(gespraech())).hasMessageContaining("keine Antwort");
    }

    // ------------------------------------------------------------------ Gemini

    @Test
    void geminiSendetSchluesselInDerKopfzeileUndFunktionsdeklarationen() throws Exception {
        antwort = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Fertig.\"}]}}]}";
        var a = new GeminiAnbieter(basis + "/v1beta", "gem-key", "gemini-3-flash-preview", null, Duration.ofSeconds(10));

        KiAntwort r = a.chat(gespraech());

        assertThat(r.text()).isEqualTo("Fertig.");
        assertThat(pfad.get()).isEqualTo("/v1beta/models/gemini-3-flash-preview:generateContent");
        assertThat(kopf.get().getFirst("x-goog-api-key")).isEqualTo("gem-key");
        JsonNode b = anfrage.get();
        assertThat(b.path("systemInstruction").path("parts").get(0).path("text").asText()).isEqualTo("Sei kurz.");
        assertThat(b.path("tools").get(0).path("functionDeclarations").get(0).path("name").asText()).isEqualTo("artikel_suchen");
        JsonNode c = b.path("contents");
        assertThat(c.get(0).path("role").asText()).isEqualTo("user");
        assertThat(c.get(1).path("role").asText()).isEqualTo("model");
        assertThat(c.get(1).path("parts").get(1).path("functionCall").path("name").asText()).isEqualTo("artikel_suchen");
        assertThat(c.get(2).path("parts").get(0).path("functionResponse").path("response").path("result").asText()).isEqualTo("ID 7: Rundrohr");
        assertThat(b.path("generationConfig").path("maxOutputTokens").asInt()).isEqualTo(300);
    }

    @Test
    void geminiLiestFunktionsaufrufeOhneKennungUndVergibtEine() throws Exception {
        antwort = "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"artikel_suchen\",\"args\":{\"q\":\"blech\"}}}]}}]}";
        var a = new GeminiAnbieter(basis + "/v1beta", "k", "m", null, null);

        KiAntwort r = a.chat(gespraech());

        assertThat(r.aufrufe()).singleElement().satisfies(k -> {
            assertThat(k.name()).isEqualTo("artikel_suchen");
            assertThat(k.id()).startsWith("call_");
            assertThat(k.argumente().path("q").asText()).isEqualTo("blech");
        });
    }

    @Test
    void geminiFehlerInKlartext() throws Exception {
        var a = new GeminiAnbieter(basis + "/v1beta", "k", "m", null, null);
        status = 429;
        assertThatThrownBy(() -> a.chat(gespraech())).hasMessageContaining("Gemini").hasMessageContaining("Kontingent");
        status = 200;
        antwort = "{\"candidates\":[]}";
        assertThatThrownBy(() -> a.chat(gespraech())).hasMessageContaining("keine Antwort");
    }

    // ------------------------------------------------------------------ Anhänge (Foto, PDF, Ton)

    private KiAnfrage mitAnhang(String mime) {
        return new KiAnfrage("Lies.", List.of(new Nutzer("Was steht drauf?", List.of(new Anhang(mime, new byte[] { 1, 2, 3 })))), List.of(), 0.0, 100);
    }

    @Test
    void geminiSchicktBildPdfUndTonAlsInlineDataVorDemText() throws Exception {
        antwort = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Rechnung\"}]}}]}";
        var a = new GeminiAnbieter(basis, "k", "gemini-x", null, Duration.ofSeconds(10));

        for (String mime : List.of("image/jpeg", "application/pdf", "audio/webm")) {
            assertThat(a.chat(mitAnhang(mime)).text()).isEqualTo("Rechnung");
            JsonNode teile = anfrage.get().path("contents").path(0).path("parts");
            assertThat(teile.path(0).path("inlineData").path("mimeType").asText()).isEqualTo(mime);
            assertThat(teile.path(0).path("inlineData").path("data").asText()).isEqualTo("AQID");
            assertThat(teile.path(1).path("text").asText()).isEqualTo("Was steht drauf?");
        }
    }

    @Test
    void claudeSchicktBilderAlsImageUndPdfAlsDocument() throws Exception {
        antwort = "{\"content\":[{\"type\":\"text\",\"text\":\"Rechnung\"}]}";
        var a = new ClaudeAnbieter(basis, "k", "claude-x", null, Duration.ofSeconds(10));

        a.chat(mitAnhang("image/png"));
        JsonNode inhalt = anfrage.get().path("messages").path(0).path("content");
        assertThat(inhalt.path(0).path("type").asText()).isEqualTo("image");
        assertThat(inhalt.path(0).path("source").path("media_type").asText()).isEqualTo("image/png");
        assertThat(inhalt.path(0).path("source").path("data").asText()).isEqualTo("AQID");
        assertThat(inhalt.path(1).path("text").asText()).isEqualTo("Was steht drauf?");

        a.chat(mitAnhang("application/pdf"));
        assertThat(anfrage.get().path("messages").path(0).path("content").path(0).path("type").asText()).isEqualTo("document");
    }

    @Test
    void openAiSchicktBilderAlsDatenAdresse() throws Exception {
        antwort = "{\"choices\":[{\"message\":{\"content\":\"Rechnung\"}}]}";
        var a = new OpenAiKompatiblerAnbieter(AnbieterTyp.LOKAL, basis + "/v1", "", "llava", null, Duration.ofSeconds(10));

        a.chat(mitAnhang("image/jpeg"));

        JsonNode inhalt = anfrage.get().path("messages").path(1).path("content");
        assertThat(inhalt.path(0).path("type").asText()).isEqualTo("image_url");
        assertThat(inhalt.path(0).path("image_url").path("url").asText()).isEqualTo("data:image/jpeg;base64,AQID");
        assertThat(inhalt.path(1).path("text").asText()).isEqualTo("Was steht drauf?");
    }

    @Test
    void ohneAnhangBleibtDerNachrichtentextEinfacherText() throws Exception {
        antwort = "{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}";
        new ClaudeAnbieter(basis, "k", "claude-x", null, Duration.ofSeconds(10)).chat(new KiAnfrage("", List.of(new Nutzer("Hallo")), List.of(), 0.0, 10));
        assertThat(anfrage.get().path("messages").path(0).path("content").asText()).isEqualTo("Hallo");
    }

    @Test
    void wasEinAnbieterNichtLesenKannMeldetErInKlartextOhneAnfrage() throws Exception {
        pfad.set(null);

        assertThatThrownBy(() -> new ClaudeAnbieter(basis, "k", "claude-x", null, Duration.ofSeconds(10)).chat(mitAnhang("audio/webm")))
                .isInstanceOf(KiAnbieterFehler.class).hasMessageContaining("Aufnahmen").hasMessageContaining("Gemini");
        assertThatThrownBy(() -> new OpenAiKompatiblerAnbieter(AnbieterTyp.MISTRAL, basis + "/v1", "k", "m", null, Duration.ofSeconds(10)).chat(mitAnhang("application/pdf")))
                .isInstanceOf(KiAnbieterFehler.class).hasMessageContaining("PDF-Dateien");
        assertThat(pfad.get()).as("es darf nichts gesendet worden sein").isNull();
    }

    @Test
    void einAnhangBrauchtTypUndInhalt() {
        assertThatThrownBy(() -> new Anhang("", new byte[] { 1 })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Anhang("image/png", new byte[0])).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Anhang(" IMAGE/PNG ", new byte[] { 1 }).mime()).isEqualTo("image/png");
    }
}
