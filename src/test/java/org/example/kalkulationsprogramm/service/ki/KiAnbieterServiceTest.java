package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.example.kalkulationsprogramm.service.KiModellService;
import org.example.kalkulationsprogramm.service.LokalerKiService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.AnbieterEingabe;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.FunktionsEingabe;
import org.example.kalkulationsprogramm.service.ki.KiFunktion.Datenschutz;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.sun.net.httpserver.HttpServer;

class KiAnbieterServiceTest {

    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final LokalerKiService lokal = mock(LokalerKiService.class);
    private final KiModellService modelle = mock(KiModellService.class);
    private final Map<String, String> gespeichert = new HashMap<>();
    private KiAnbieterService service;
    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        when(einstellungen.get(anyString(), any())).thenAnswer(a -> gespeichert.getOrDefault(a.<String>getArgument(0), a.getArgument(1)));
        org.mockito.Mockito.doAnswer(a -> { gespeichert.put(a.getArgument(0), a.getArgument(1)); return null; }).when(einstellungen).save(anyString(), any(), any());
        when(einstellungen.getGeminiApiKey()).thenReturn("gem-key");
        when(modelle.modell(any(), any())).thenReturn("gemini-3-flash-preview");
        when(lokal.istKonfiguriert()).thenReturn(true);
        when(lokal.konfiguration()).thenReturn(new LokalerKiService.Konfiguration("http://pc:11434", "qwen2.5:7b", false, false));
        when(lokal.schluessel()).thenReturn("");
        service = new KiAnbieterService(einstellungen, lokal, modelle, new KiObergrenze(einstellungen, mock(org.example.kalkulationsprogramm.repository.KiAktionProtokollRepository.class)));

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            byte[] b = "{\"content\":[{\"type\":\"text\",\"text\":\"OK\"}]}".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        service.claudeBasis = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void kiGiltAlsAktivSobaldIrgendeinAnbieterEingerichtetIst() {
        // Nur die lokale KI eingerichtet, kein Gemini-, Claude- oder Mistral-Schlüssel
        when(einstellungen.getGeminiApiKey()).thenReturn("");
        when(lokal.istKonfiguriert()).thenReturn(true);
        assertThat(service.irgendeinAnbieterEingerichtet()).isTrue();

        // Nichts eingerichtet: die KI-Funktionen bleiben aus
        when(lokal.istKonfiguriert()).thenReturn(false);
        assertThat(service.irgendeinAnbieterEingerichtet()).isFalse();

        // Nur ein Claude-Schlüssel
        gespeichert.put("ki.claude.schluessel", "sk-test");
        assertThat(service.irgendeinAnbieterEingerichtet()).isTrue();
    }

    @Test
    void standardsDerFunktionen() {
        assertThat(service.einstellung(KiFunktion.HILFE).anbieter()).as("bisheriges Verhalten bleibt").isEqualTo(AnbieterTyp.GEMINI);
        assertThat(service.einstellung(KiFunktion.HILFE).datenschutz()).isEqualTo(Datenschutz.CLOUD_ERLAUBT);
        assertThat(service.einstellung(KiFunktion.ASSISTENT).anbieter()).isEqualTo(AnbieterTyp.LOKAL);
        assertThat(service.einstellung(KiFunktion.ASSISTENT).datenschutz()).as("mit Firmendaten: standardmäßig nur lokal").isEqualTo(Datenschutz.NUR_LOKAL);
    }

    @Test
    void dieAlteEinstellungDerKiHilfeBleibtGueltig() {
        gespeichert.put("ki.hilfe.anbieter", "LOKAL");
        assertThat(service.einstellung(KiFunktion.HILFE).anbieter()).isEqualTo(AnbieterTyp.LOKAL);
        gespeichert.put("ki.hilfe.anbieter", "quatsch");
        assertThat(service.einstellung(KiFunktion.HILFE).anbieter()).as("Unbekanntes fällt auf den Standard").isEqualTo(AnbieterTyp.GEMINI);
    }

    @Test
    void nurLokalSperrtCloudAnbieterOhneAufruf() {
        gespeichert.put("ki.assistent.anbieter", "GEMINI");

        assertThatThrownBy(() -> service.waehle(KiFunktion.ASSISTENT)).isInstanceOf(KiAnbieterFehler.class).hasMessageContaining("nur lokale").hasMessageContaining("KI-Assistent");
    }

    @Test
    void nurLokalMitLokalerKiLiefertDenLokalenAnbieter() throws Exception {
        KiAnbieter a = service.waehle(KiFunktion.ASSISTENT);

        assertThat(a.typ()).isEqualTo(AnbieterTyp.LOKAL);
        assertThat(a.modell()).isEqualTo("qwen2.5:7b");
    }

    @Test
    void cloudErlaubtLiefertDenGewaehltenAnbieter() throws Exception {
        assertThat(service.waehle(KiFunktion.HILFE).typ()).isEqualTo(AnbieterTyp.GEMINI);
        gespeichert.put("ki.assistent.anbieter", "CLAUDE");
        gespeichert.put("ki.datenschutz.assistent", "CLOUD_ERLAUBT");
        gespeichert.put("ki.claude.schluessel", "sk-x");
        KiAnbieter a = service.waehle(KiFunktion.ASSISTENT);
        assertThat(a.typ()).isEqualTo(AnbieterTyp.CLAUDE);
        assertThat(a.modell()).isEqualTo("claude-sonnet-5-5");
    }

    @Test
    void nichtEingerichteteAnbieterGebenKlarenHinweis() {
        when(lokal.istKonfiguriert()).thenReturn(false);
        assertThatThrownBy(() -> service.waehle(KiFunktion.ASSISTENT)).hasMessageContaining("Die lokale KI ist noch nicht eingerichtet");
        gespeichert.put("ki.hilfe.anbieter", "CLAUDE");
        assertThatThrownBy(() -> service.waehle(KiFunktion.HILFE)).hasMessageContaining("Claude-Schlüssel");
        gespeichert.put("ki.hilfe.anbieter", "MISTRAL");
        assertThatThrownBy(() -> service.waehle(KiFunktion.HILFE)).hasMessageContaining("Mistral-Schlüssel");
        when(einstellungen.getGeminiApiKey()).thenReturn("");
        gespeichert.put("ki.hilfe.anbieter", "GEMINI");
        assertThatThrownBy(() -> service.waehle(KiFunktion.HILFE)).hasMessageContaining("Gemini API Key");
    }

    @Test
    void speichernUebernimmtAnbieterDatenschutzUndZugangsdaten() {
        var u = service.speichern(Map.of("hilfe", new FunktionsEingabe("CLAUDE", "CLOUD_ERLAUBT"), "assistent", new FunktionsEingabe("LOKAL", "NUR_LOKAL")),
                new AnbieterEingabe("claude-opus-5-5", "sk-geheim"), new AnbieterEingabe("", null));

        assertThat(gespeichert).containsEntry("ki.hilfe.anbieter", "CLAUDE").containsEntry("ki.datenschutz.hilfe", "CLOUD_ERLAUBT")
                .containsEntry("ki.claude.modell", "claude-opus-5-5").containsEntry("ki.claude.schluessel", "sk-geheim");
        assertThat(gespeichert).as("Schlüssel null = unverändert, nicht angelegt").doesNotContainKey("ki.mistral.schluessel");
        var claude = u.anbieter().stream().filter(s -> s.typ() == AnbieterTyp.CLAUDE).findFirst().orElseThrow();
        assertThat(claude.schluesselGesetzt()).isTrue();
        assertThat(claude.eingerichtet()).isTrue();
        assertThat(u.toString()).as("der Schlüssel taucht nirgends auf").doesNotContain("sk-geheim");
        assertThat(u.nochGemini()).isNotEmpty();
    }

    @Test
    void speichernLehntUnstimmigesUndUngueltigesAb() {
        assertThatThrownBy(() -> service.speichern(Map.of("assistent", new FunktionsEingabe("GEMINI", "NUR_LOKAL")), null, null))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("nur lokal");
        assertThatThrownBy(() -> service.speichern(Map.of("gibtesnicht", new FunktionsEingabe("LOKAL", "NUR_LOKAL")), null, null)).hasMessageContaining("gibt es nicht");
        assertThatThrownBy(() -> service.speichern(Map.of("hilfe", new FunktionsEingabe("QUATSCH", "NUR_LOKAL")), null, null)).hasMessageContaining("Anbieter");
        assertThatThrownBy(() -> service.speichern(Map.of("hilfe", new FunktionsEingabe("LOKAL", "QUATSCH")), null, null)).hasMessageContaining("Datenschutz");
        assertThatThrownBy(() -> service.speichern(null, new AnbieterEingabe("../etc passwd", null), null)).hasMessageContaining("Modellname");
        assertThatThrownBy(() -> service.speichern(null, null, new AnbieterEingabe("", "x".repeat(501)))).hasMessageContaining("zu lang");
        assertThat(gespeichert).as("bei Fehlern wird nichts gespeichert, was davor geprüft wurde").doesNotContainKey("ki.claude.modell");
    }

    @Test
    void testMeldetErfolgUndFehlendeEinrichtung() {
        gespeichert.put("ki.claude.schluessel", "sk-x");
        var ok = service.teste(AnbieterTyp.CLAUDE);
        assertThat(ok.ok()).isTrue();
        assertThat(ok.meldung()).contains("Claude (Anthropic) antwortet").contains("OK");

        var fehlt = service.teste(AnbieterTyp.MISTRAL);
        assertThat(fehlt.ok()).isFalse();
        assertThat(fehlt.art()).isEqualTo("NICHT_EINGERICHTET");
    }

    @Test
    void dieZentraleObergrenzeSteuertUeberDerEinstellungDerFunktion() throws Exception {
        // KI-Hilfe steht auf Gemini mit "Cloud erlaubt"; die Obergrenze "nur im Haus" sperrt trotzdem
        gespeichert.put("ki.obergrenze.datenschutz", "LOKAL");

        assertThatThrownBy(() -> service.pruefeDatenschutz(KiFunktion.HILFE)).isInstanceOf(KiAnbieterFehler.class).hasMessageContaining("nur lokale KI-Anbieter");
        assertThatThrownBy(() -> service.waehle(KiFunktion.HILFE)).isInstanceOf(KiAnbieterFehler.class);
        assertThat(service.waehle(KiFunktion.ASSISTENT).typ()).as("die lokale KI bleibt erlaubt").isEqualTo(AnbieterTyp.LOKAL);

        gespeichert.put("ki.obergrenze.datenschutz", "CLOUD");
        gespeichert.put("ki.obergrenze.eingriff", "AUS");
        assertThatThrownBy(() -> service.waehle(KiFunktion.ASSISTENT)).isInstanceOf(KiAnbieterFehler.class).hasMessageContaining("ausgeschaltet");
    }

    @Test
    void ProgrammdatenUndEingriffKommenAusDerObergrenze() {
        gespeichert.put("ki.obergrenze.datenschutz", "ALLGEMEIN");
        gespeichert.put("ki.obergrenze.eingriff", "ERWEITERT");
        gespeichert.put("ki.obergrenze.ohne-rueckfrage", "zeit_buchen");

        assertThat(service.sperrtProgrammdaten(AnbieterTyp.GEMINI)).isTrue();
        assertThat(service.sperrtProgrammdaten(AnbieterTyp.LOKAL)).isFalse();
        assertThat(service.eingriff()).isEqualTo(KiObergrenze.Eingriff.ERWEITERT);
        assertThat(service.ohneRueckfrage("zeit_buchen")).isTrue();
        assertThat(service.ohneRueckfrage("lager_buchen")).isFalse();
    }

    private String gesehenerSchluessel;

    /** Ein zweiter Ersatz-Server, der Modell-Listen wie Anthropic und Mistral liefert und sich den Schlüssel merkt. */
    private HttpServer modellServer(String claudeAntwort, String mistralAntwort, int status) throws IOException {
        HttpServer s = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        s.createContext("/claude/v1/models", ex -> {
            gesehenerSchluessel = ex.getRequestHeaders().getFirst("x-api-key");
            antworte(ex, status, claudeAntwort);
        });
        s.createContext("/mistral/v1/models", ex -> {
            gesehenerSchluessel = ex.getRequestHeaders().getFirst("Authorization");
            antworte(ex, status, mistralAntwort);
        });
        s.start();
        String basis = "http://127.0.0.1:" + s.getAddress().getPort();
        service.claudeBasis = basis + "/claude/v1";
        service.mistralBasis = basis + "/mistral/v1";
        return s;
    }

    private static void antworte(com.sun.net.httpserver.HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @Test
    void claudeUndMistralNennenIhreModelleMitDemGespeichertenSchluessel() throws Exception {
        HttpServer s = modellServer("{\"data\":[{\"id\":\"claude-b\",\"display_name\":\"Claude B\"},{\"id\":\"claude-a\"},{\"id\":\"claude-b\"},{\"id\":\" \"}]}",
                "{\"data\":[{\"id\":\"mistral-small-latest\",\"name\":\"Mistral Small\"}]}", 200);
        try {
            gespeichert.put("ki.claude.schluessel", "claude-geheim");
            gespeichert.put("ki.mistral.schluessel", "mistral-geheim");

            assertThat(service.verfuegbareModelle(AnbieterTyp.CLAUDE)).as("sortiert, ohne Doppelte und Leere, Anzeigename sonst die Kennung")
                    .containsExactly(new KiAnbieterService.Modell("claude-a", "claude-a"), new KiAnbieterService.Modell("claude-b", "Claude B"));
            assertThat(gesehenerSchluessel).isEqualTo("claude-geheim");
            assertThat(service.verfuegbareModelle(AnbieterTyp.MISTRAL)).containsExactly(new KiAnbieterService.Modell("mistral-small-latest", "Mistral Small"));
            assertThat(gesehenerSchluessel).isEqualTo("Bearer mistral-geheim");
        } finally {
            s.stop(0);
        }
    }

    @Test
    void ohneSchluesselOderBeiFehlernKommtEinKlartext() throws Exception {
        assertThatThrownBy(() -> service.verfuegbareModelle(AnbieterTyp.CLAUDE)).isInstanceOf(KiAnbieterFehler.class).hasMessageContaining("Claude-Schlüssel");
        assertThatThrownBy(() -> service.verfuegbareModelle(AnbieterTyp.MISTRAL)).isInstanceOf(KiAnbieterFehler.class).hasMessageContaining("Mistral-Schlüssel");

        HttpServer s = modellServer("{}", "{}", 401);
        try {
            gespeichert.put("ki.claude.schluessel", "falsch");
            assertThatThrownBy(() -> service.verfuegbareModelle(AnbieterTyp.CLAUDE)).isInstanceOf(KiAnbieterFehler.class)
                    .hasMessageContaining("lehnt den Zugang ab").hasMessageContaining("Claude");
        } finally {
            s.stop(0);
        }
    }

    @Test
    void geminiUndLokalNutzenIhreBisherigenAbfragen() throws Exception {
        when(modelle.verfuegbare()).thenReturn(java.util.List.of(new KiModellService.VerfuegbaresModell("gemini-x", "Gemini X", "")));
        when(lokal.modelle()).thenReturn(java.util.List.of("llama3", "qwen"));

        assertThat(service.verfuegbareModelle(AnbieterTyp.GEMINI)).containsExactly(new KiAnbieterService.Modell("gemini-x", "Gemini X"));
        assertThat(service.verfuegbareModelle(AnbieterTyp.LOKAL)).extracting(KiAnbieterService.Modell::name).containsExactly("llama3", "qwen");
    }
}
