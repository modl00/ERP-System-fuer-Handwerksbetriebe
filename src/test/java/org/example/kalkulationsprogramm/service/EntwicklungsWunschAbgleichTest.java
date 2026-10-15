package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Abgleich neuer Wünsche mit offenen Issues (#249) gegen einen nachgebauten Gitea-Server. Nur Dummy-Zugangsdaten. */
class EntwicklungsWunschAbgleichTest {

    private final Map<String, String> speicher = new HashMap<>();
    private final List<String> anfragen = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private EntwicklungsWunschService service;

    private static final String ISSUES = "[" +
            "{\"number\":251,\"title\":\"KI-Chatfenster: Größe anpassen und frei verschieben\",\"body\":\"Das Fenster soll sich per Drag and Drop verschieben lassen.\",\"html_url\":\"http://x/251\",\"state\":\"open\"}," +
            "{\"number\":252,\"title\":\"Artikelstamm: Massenupdate für Gewichte\",\"body\":\"Fehlende Gewichte ergänzen.\",\"html_url\":\"http://x/252\",\"state\":\"open\"}," +
            "{\"number\":260,\"title\":\"Pull Request\",\"body\":\"\",\"html_url\":\"http://x/260\",\"state\":\"open\",\"pull_request\":{}}]";

    @BeforeEach
    void setUp() throws IOException {
        SystemSettingsService einstellungen = mock(SystemSettingsService.class);
        when(einstellungen.get(anyString(), anyString())).thenAnswer(i -> speicher.getOrDefault(i.getArgument(0), i.getArgument(1)));
        doAnswer(i -> speicher.put(i.getArgument(0), i.getArgument(1))).when(einstellungen).save(anyString(), anyString(), anyString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/repos/M/ERP", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String pfad = ex.getRequestURI().getPath();
            anfragen.add(ex.getRequestMethod() + " " + pfad + " " + (ex.getRequestURI().getQuery() == null ? "" : ex.getRequestURI().getQuery()) + " body=" + body);
            int status = 200;
            String antwort;
            if (pfad.endsWith("/issues/251")) {
                antwort = "{\"number\":251,\"title\":\"KI-Chatfenster: Größe anpassen\",\"html_url\":\"http://x/251\",\"state\":\"open\"}";
            } else if (pfad.endsWith("/issues/999")) {
                status = 404;
                antwort = "{}";
            } else if (pfad.endsWith("/issues/200")) {
                antwort = "{\"number\":200,\"title\":\"Erledigt\",\"state\":\"closed\"}";
            } else if (pfad.endsWith("/comments")) {
                status = 201;
                antwort = "{\"html_url\":\"http://x/251#c1\"}";
            } else if (pfad.endsWith("/issues")) {
                antwort = ISSUES;
            } else {
                antwort = "{}";
            }
            byte[] b = antwort.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        service = new EntwicklungsWunschService(einstellungen, new MailSecretService("test-schluessel-test-schluessel-12"));
        service.speichern("http://127.0.0.1:" + server.getAddress().getPort(), "M/ERP", "dummy-token-1234567890");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void einAehnlichesOffenesIssueWirdGefundenPullRequestsUndUnpassendesNicht() {
        var treffer = service.sucheAehnliche("Chatfenster verschieben: Das KI-Fenster soll frei verschiebbar und in der Größe änderbar sein");

        assertThat(treffer).extracting(EntwicklungsWunschService.Aehnlich::nummer).containsExactly(251);
        assertThat(anfragen.get(0)).contains("state=open").contains("type=issues");
    }

    @Test
    void einWunschOhneGemeinsamesThemaFindetNichts() {
        assertThat(service.sucheAehnliche("Kalender fremder Dienste anzeigen und Plantafel für Monteure")).isEmpty();
    }

    @Test
    void zuKurzeOderLeereTexteSuchenGarNicht() {
        assertThat(service.sucheAehnliche("Fenster")).isEmpty();
        assertThat(service.sucheAehnliche("   ")).isEmpty();
        assertThat(service.sucheAehnliche(null)).isEmpty();
        assertThat(anfragen).isEmpty();
    }

    @Test
    void ohneZugangGibtDieSucheEineLeereListeStattEinesFehlers() {
        service.speichern("", "", "");
        assertThat(service.sucheAehnliche("Chatfenster verschieben und Größe ändern")).isEmpty();
    }

    @Test
    void ein_offenes_Issue_wird_geholt_ein_geschlossenes_oder_fehlendes_nicht() {
        assertThat(service.holeOffenes(251).titel()).contains("Chatfenster");
        assertThat(service.holeOffenes(200)).isNull();
        assertThat(service.holeOffenes(999)).isNull();
        assertThat(service.holeOffenes(0)).isNull();
    }

    @Test
    void einKommentarGehtAnDasRichtigeIssueMitDemText() {
        var r = service.kommentiere(251, "Auch Touch bedienen bitte");
        assertThat(r.nummer()).isEqualTo(251);
        assertThat(anfragen.get(anfragen.size() - 1)).startsWith("POST /api/v1/repos/M/ERP/issues/251/comments").contains("Auch Touch bedienen bitte");
        assertThatThrownBy(() -> service.kommentiere(251, " ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void woerterIgnorierenKurzesFuellwoerterUndUmlaute() {
        assertThat(EntwicklungsWunschService.woerter("Das Fenster soll größer werden über Nacht")).containsExactly("fenster", "groesser", "nacht");
    }
}
