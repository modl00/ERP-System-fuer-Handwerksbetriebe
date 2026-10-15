package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

/** Gegen einen echten kleinen Server auf dem eigenen Rechner (Ersatz für Ollama), ohne Netz nach außen. */
class LokalerKiServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final Map<String, String> gespeichert = new HashMap<>();
    private LokalerKiService service;
    private HttpServer server;
    private String adresse;
    private final AtomicReference<String> letzteAnfrage = new AtomicReference<>();
    private final AtomicReference<String> letzterSchluessel = new AtomicReference<>();
    private volatile int chatStatus = 200;
    private volatile String chatInhalt = "OK";

    @BeforeEach
    void setUp() throws IOException {
        when(einstellungen.get(anyString(), any())).thenAnswer(a -> gespeichert.getOrDefault(a.<String>getArgument(0), a.getArgument(1)));
        org.mockito.Mockito.doAnswer(a -> { gespeichert.put(a.getArgument(0), a.getArgument(1)); return null; })
                .when(einstellungen).save(anyString(), any(), any());
        service = new LokalerKiService(einstellungen);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/models", ex -> antwort(ex, 200, "{\"data\":[{\"id\":\"qwen2.5:7b\"},{\"id\":\"llama3.1:8b\"}]}"));
        server.createContext("/v1/chat/completions", ex -> {
            letzteAnfrage.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            letzterSchluessel.set(ex.getRequestHeaders().getFirst("Authorization"));
            String body = chatStatus == 200
                    ? "{\"choices\":[{\"message\":{\"content\":" + json.writeValueAsString(chatInhalt) + "}}]}"
                    : "{\"error\":\"x\"}";
            antwort(ex, chatStatus, body);
        });
        server.start();
        adresse = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static void antwort(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    private void einrichten() {
        service.speichern(adresse, "qwen2.5:7b", null, false);
    }

    @Test
    void adresseBekommtV1AngehaengtOhneDoppelteSchraegstriche() {
        assertEquals("http://pc:11434/v1", LokalerKiService.basis("http://pc:11434"));
        assertEquals("http://pc:11434/v1", LokalerKiService.basis("http://pc:11434//"));
        assertEquals("http://pc:11434/v1", LokalerKiService.basis("http://pc:11434/v1"));
    }

    @Test
    void ungueltigeAdressenWerdenAbgelehnt() {
        for (String falsch : List.of("pc:11434", "ftp://pc", "http://", "http://user:pw@pc:11434", "http://pc:11434?x=1", "file:///etc/passwd")) {
            assertThrows(ResponseStatusException.class, () -> service.speichern(falsch, "m", null, false), falsch);
        }
        assertTrue(gespeichert.isEmpty(), "bei einer ungültigen Eingabe wird nichts gespeichert");
    }

    @Test
    void ungueltigerModellnameWirdAbgelehnt() {
        assertThrows(ResponseStatusException.class, () -> service.speichern(adresse, "../etc passwd", null, false));
        assertThrows(ResponseStatusException.class, () -> service.speichern(adresse, "m\"x", null, false));
    }

    @Test
    void kiHilfeLokalVerlangtAdresseUndModell() {
        assertThrows(ResponseStatusException.class, () -> service.speichern("", "", null, true));
        assertThrows(ResponseStatusException.class, () -> service.speichern(adresse, "", null, true));
        assertFalse(service.konfiguration().hilfeLokal());
    }

    @Test
    void schluesselBleibtUnveraendertWennNullUndWirdNieZurueckgegeben() {
        service.speichern(adresse, "qwen2.5:7b", "geheim", true);
        var k = service.speichern(adresse, "qwen2.5:7b", null, true);

        assertTrue(k.schluesselGesetzt());
        assertTrue(k.hilfeLokal());
        assertFalse(k.toString().contains("geheim"), "der Schlüssel taucht in der Konfiguration nicht auf");

        assertFalse(service.speichern(adresse, "qwen2.5:7b", "", false).schluesselGesetzt(), "leer entfernt den Schlüssel");
    }

    @Test
    void chatSendetModellUndGespraechUndEntferntDenDenktext() throws Exception {
        einrichten();
        chatInhalt = "<think>überlege</think>\nDie Antwort.";

        String text = service.chat("Sei kurz.", List.of(new LokalerKiService.Nachricht("user", "Hallo"),
                new LokalerKiService.Nachricht("assistant", "Hi"), new LokalerKiService.Nachricht("user", "Wie geht's?")), 0.2, 100);

        assertEquals("Die Antwort.", text);
        JsonNode anfrage = json.readTree(letzteAnfrage.get());
        assertEquals("qwen2.5:7b", anfrage.path("model").asText());
        assertFalse(anfrage.path("stream").asBoolean());
        assertEquals(4, anfrage.path("messages").size());
        assertEquals("system", anfrage.path("messages").get(0).path("role").asText());
        assertEquals("assistant", anfrage.path("messages").get(2).path("role").asText());
    }

    @Test
    void einSchluesselWirdAlsBearerMitgeschickt() throws Exception {
        service.speichern(adresse, "qwen2.5:7b", "abc123", false);
        service.chat(null, List.of(new LokalerKiService.Nachricht("user", "Hi")), 0, 10);
        assertEquals("Bearer abc123", letzterSchluessel.get());
    }

    @Test
    void ohneSchluesselKommtKeinAuthorizationHeader() throws Exception {
        einrichten();
        service.chat(null, List.of(new LokalerKiService.Nachricht("user", "Hi")), 0, 10);
        assertEquals(null, letzterSchluessel.get());
    }

    @Test
    void ohneEinrichtungGibtEsEineKlareMeldung() {
        var e = assertThrows(LokalerKiService.LokaleKiFehler.class, () -> service.chat(null, List.of(), 0, 10));
        assertTrue(e.getMessage().contains("noch nicht eingerichtet"));
    }

    @Test
    void modelleWerdenSortiertAufgelistet() {
        einrichten();
        assertEquals(List.of("llama3.1:8b", "qwen2.5:7b"), service.modelle());
    }

    @Test
    void testMeldetErfolg() {
        einrichten();
        var t = service.teste();
        assertTrue(t.ok(), t.meldung());
        assertEquals("OK", t.art());
        assertTrue(t.meldung().contains("qwen2.5:7b"));
    }

    @Test
    void fehlendesModellWirdMitDemOllamaBefehlErklaert() {
        einrichten();
        chatStatus = 404;
        var t = service.teste();
        assertFalse(t.ok());
        assertEquals("MODELL_FEHLT", t.art());
        assertTrue(t.meldung().contains("ollama pull qwen2.5:7b"), t.meldung());
    }

    @Test
    void abgelehnterSchluesselWirdErkannt() {
        einrichten();
        chatStatus = 401;
        var t = service.teste();
        assertEquals("KEIN_ZUGRIFF", t.art());
    }

    @Test
    void unerreichbarerRechnerWirdMitHinweisAufFirewallErklaert() throws IOException {
        int frei;
        try (ServerSocket s = new ServerSocket(0)) {
            frei = s.getLocalPort();
        }
        service.speichern("http://127.0.0.1:" + frei, "qwen2.5:7b", null, false);

        var t = service.teste();

        assertFalse(t.ok());
        assertEquals("NICHT_ERREICHBAR", t.art());
        assertTrue(t.meldung().contains("OLLAMA_HOST"), t.meldung());
    }

    @Test
    void testOhneEinrichtungFordertEintragenAn() {
        var t = service.teste();
        assertFalse(t.ok());
        assertTrue(t.meldung().contains("Adresse und Modellname"));
    }
}
