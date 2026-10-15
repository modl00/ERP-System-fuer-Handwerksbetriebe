package org.example.kalkulationsprogramm.service.testbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTextWeiche;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Test-Box: gegen einen nachgestellten Gitea-Server aus dem JDK. Nur Dummy-Daten, kein echter Zugangsschlüssel. */
class TestboxServiceTest {

    private static final String FUSS = "\n\n🤖 Generated with [Claude Code](https://claude.com/claude-code)";

    private HttpServer server;
    private final Map<String, String> werte = new HashMap<>();
    private final List<String> gesendet = new ArrayList<>();
    private final List<String> anfragen = new ArrayList<>();
    private String autorisierung;
    private int status = 200;
    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final KiTextWeiche weiche = mock(KiTextWeiche.class);
    private TestboxService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/repos/M/Demo", this::antwort);
        server.start();
        werte.put(GiteaClient.URL_SCHLUESSEL, "http://127.0.0.1:" + server.getAddress().getPort());
        werte.put(GiteaClient.REPO_SCHLUESSEL, "M/Demo");
        werte.put(GiteaClient.TOKEN_SCHLUESSEL, "geheim-123");
        when(einstellungen.get(anyString(), any())).thenAnswer(i -> werte.getOrDefault(i.getArgument(0), i.getArgument(1)));
        org.mockito.Mockito.doAnswer(i -> werte.put(i.getArgument(0), i.getArgument(1))).when(einstellungen).save(anyString(), any(), any());
        service = new TestboxService(new GiteaClient(einstellungen), einstellungen, weiche);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void antwort(HttpExchange x) throws IOException {
        autorisierung = x.getRequestHeaders().getFirst("Authorization");
        String pfad = x.getRequestURI().getPath();
        String query = x.getRequestURI().getRawQuery() == null ? "" : x.getRequestURI().getRawQuery();
        anfragen.add(x.getRequestMethod() + " " + pfad + (query.isEmpty() ? "" : "?" + query));
        String body;
        if (status != 200) {
            body = "{}";
        } else if (x.getRequestMethod().equals("POST")) {
            gesendet.add(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            body = "{\"id\":1}";
        } else if (pfad.endsWith("/issues") && query.contains("Wartet")) {
            body = "[{\"number\":5,\"title\":\"Plantafel: Lieferzeit\",\"html_url\":\"http://g/5\",\"body\":\"Beschreibung\",\"labels\":[{\"name\":\"Wartet auf Antwort\"}]},"
                    + "{\"number\":7,\"title\":\"Menü\",\"html_url\":\"http://g/7\",\"body\":\"Menü-Text\",\"labels\":[{\"name\":\"Wartet auf Antwort\"},{\"name\":\"Im Pull Request\"}]}]";
        } else if (pfad.endsWith("/issues") && query.contains("Pull")) {
            body = "[{\"number\":7,\"title\":\"Menü\",\"html_url\":\"http://g/7\",\"body\":\"Menü-Text\",\"labels\":[{\"name\":\"Im Pull Request\"}]},"
                    + "{\"number\":9,\"title\":\"Startbildschirm\",\"html_url\":\"http://g/9\",\"body\":\"Start\",\"labels\":[{\"name\":\"Im Pull Request\"}]}]";
        } else if (pfad.endsWith("/issues/5/comments")) {
            body = "[{\"body\":\"Frage 1" + FUSS.replace("\n", "\\n") + "\"},{\"body\":\"Meine Antwort\"},{\"body\":\"Frage 2: Welche Reihenfolge?" + FUSS.replace("\n", "\\n") + "\"}]";
        } else if (pfad.endsWith("/issues/7/comments")) {
            body = "[{\"body\":\"Testweg: Menü öffnen" + FUSS.replace("\n", "\\n") + "\"},{\"body\":\"Funktioniert\"}]";
        } else if (pfad.endsWith("/issues/9/comments")) {
            body = "[]";
        } else if (pfad.matches(".*/issues/\\d+")) {
            body = "{\"number\":5}";
        } else {
            status = 404;
            body = "{}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status == 200 ? 200 : status, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }

    @Test
    void diePunkteKommenAusBeidenLabelsOhneDoppelteUndSortiertMitDenWartendenZuerst() {
        List<TestboxService.Punkt> punkte = service.punkte();

        assertThat(punkte).extracting(TestboxService.Punkt::nummer).containsExactly(9, 5, 7);
        TestboxService.Punkt fuenf = punkte.get(1);
        assertThat(fuenf.wartet()).isTrue();
        assertThat(fuenf.frage()).contains("Frage 2").doesNotContain("Frage 1").doesNotContain("🤖");
        TestboxService.Punkt sieben = punkte.get(2);
        assertThat(sieben.wartet()).as("die letzte Nachricht stammt vom Anwender").isFalse();
        assertThat(sieben.frage()).startsWith("Testweg: Menü öffnen");
        assertThat(sieben.labels()).containsExactly("Wartet auf Antwort", "Im Pull Request");
        assertThat(punkte.get(0).frage()).as("ohne Kommentar gilt die Beschreibung").isEqualTo("Start");
        assertThat(autorisierung).isEqualTo("token geheim-123");
    }

    @Test
    void ohneEinrichtungKommtEinKlartext() {
        werte.put(GiteaClient.TOKEN_SCHLUESSEL, "");

        assertThatThrownBy(() -> service.punkte()).hasMessageContaining("noch nicht eingerichtet");
    }

    @Test
    void einAbgelehnterSchluesselUndEinFalschesRepositoryKommenMitKlartext() {
        status = 403;
        assertThatThrownBy(() -> service.punkte()).hasMessageContaining("nimmt den Zugangsschlüssel nicht an");
        status = 404;
        assertThatThrownBy(() -> service.punkte()).hasMessageContaining("kennt dieses Repository");
    }

    @Test
    void eineNichtErreichbareAdresseKommtMitKlartext() {
        werte.put(GiteaClient.URL_SCHLUESSEL, "http://127.0.0.1:1");

        assertThatThrownBy(() -> service.punkte()).hasMessageContaining("nicht erreichbar");
    }

    @Test
    void dieKiBereitetDieAntwortAufUndOrdnetSieEin() throws Exception {
        when(weiche.frage(eq(KiFunktion.TESTBOX), anyString(), anyString(), anyDouble(), anyInt(), eq(true)))
                .thenReturn("{\"einordnung\":\"ENTSCHEIDUNG\",\"kommentar\":\"Die Reihenfolge soll der Planer festlegen.\"}");

        var v = service.aufbereiten(5, "der planer soll das entscheiden", "ANTWORT");

        assertThat(v.ki()).isTrue();
        assertThat(v.einordnung()).isEqualTo("ENTSCHEIDUNG");
        assertThat(v.kommentar()).isEqualTo("Die Reihenfolge soll der Planer festlegen.");
        verify(weiche).frage(eq(KiFunktion.TESTBOX), anyString(), org.mockito.ArgumentMatchers.contains("Frage 2"), anyDouble(), anyInt(), eq(true));
    }

    @Test
    void ohneKiBleibtDerTextDesAnwendersStehen() throws Exception {
        when(weiche.frage(any(), anyString(), anyString(), anyDouble(), anyInt(), org.mockito.ArgumentMatchers.anyBoolean())).thenThrow(new IOException("kein Schlüssel"));

        var v = service.aufbereiten(5, "klappt nicht beim Ziehen", "PROBLEM");

        assertThat(v.ki()).isFalse();
        assertThat(v.einordnung()).isEqualTo("PROBLEM");
        assertThat(v.kommentar()).isEqualTo("klappt nicht beim Ziehen");
        assertThat(v.hinweis()).contains("nicht erreichbar");
    }

    @Test
    void eineKurzeSchnellantwortFunktioniertBrauchtKeineKi() {
        var v = service.aufbereiten(7, "ok", "FUNKTIONIERT");

        assertThat(v.ki()).isFalse();
        assertThat(v.kommentar()).isEqualTo("Funktioniert wie erwartet.");
        org.mockito.Mockito.verifyNoInteractions(weiche);
    }

    @Test
    void sendenSchreibtEinenKommentarMitKennzeichnungOriginalUndOhneClaudeFuss() {
        service.senden(5, "ENTSCHEIDUNG", "Die Reihenfolge soll der Planer festlegen.", "der planer soll das entscheiden");

        assertThat(gesendet).hasSize(1);
        String k = gesendet.get(0);
        assertThat(k).contains("Rückmeldung über die Test-Box").contains("(Entscheidung)").contains("Die Reihenfolge soll der Planer festlegen.")
                .contains("Originaltext: der planer soll das entscheiden").doesNotContain("Claude Code");
        assertThat(anfragen).contains("POST /api/v1/repos/M/Demo/issues/5/comments");
    }

    @Test
    void leereOderZuLangeTexteWerdenAbgelehnt() {
        assertThatThrownBy(() -> service.senden(5, "ANTWORT", "  ", null)).hasMessageContaining("etwas eintragen");
        assertThatThrownBy(() -> service.senden(5, "ANTWORT", "x".repeat(4001), null)).hasMessageContaining("zu lang");
        assertThatThrownBy(() -> service.senden(0, "ANTWORT", "text", null)).hasMessageContaining("Ticket");
        assertThat(gesendet).isEmpty();
    }

    @Test
    void dieEinstellungZeigtDenSchluesselNieUndPrueftDieEingaben() {
        var e = service.einstellung();
        assertThat(e.tokenGesetzt()).isTrue();
        assertThat(e.toString()).doesNotContain("geheim-123");

        var neu = service.speichereEinstellung("http://gitea.lan:3050/", "M/Demo", null);
        assertThat(neu.url()).isEqualTo("http://gitea.lan:3050/");
        assertThat(werte.get(GiteaClient.TOKEN_SCHLUESSEL)).as("null = unverändert").isEqualTo("geheim-123");
        service.speichereEinstellung("http://gitea.lan:3050", "M/Demo", "");
        assertThat(werte.get(GiteaClient.TOKEN_SCHLUESSEL)).as("leer = entfernen").isEmpty();

        assertThatThrownBy(() -> service.speichereEinstellung("ftp://x", "M/Demo", null)).hasMessageContaining("http");
        assertThatThrownBy(() -> service.speichereEinstellung("http://x", "kaputt", null)).hasMessageContaining("Besitzer/Name");
    }

}
