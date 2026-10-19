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
import java.util.concurrent.atomic.AtomicInteger;

import org.example.kalkulationsprogramm.service.EntwicklungsWunschService.Angelegt;
import org.example.kalkulationsprogramm.service.EntwicklungsWunschService.Stand;
import org.example.kalkulationsprogramm.service.EntwicklungsWunschService.Testergebnis;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Wunsch an die Entwicklung gegen einen nachgebauten Gitea-Server (JDK-HttpServer). Nur Dummy-Zugangsdaten. */
class EntwicklungsWunschServiceTest {

    private static final String TOKEN = "dummy-token-1234567890";
    private final Map<String, String> speicher = new HashMap<>();
    private SystemSettingsService einstellungen;
    private HttpServer server;
    private String adresse;
    private final List<String> anfragen = new CopyOnWriteArrayList<>();
    private final AtomicInteger issueStatus = new AtomicInteger(201);
    private final AtomicInteger repoStatus = new AtomicInteger(200);

    @BeforeEach
    void setUp() throws IOException {
        einstellungen = mock(SystemSettingsService.class);
        when(einstellungen.get(anyString(), anyString())).thenAnswer(i -> speicher.getOrDefault(i.getArgument(0), i.getArgument(1)));
        doAnswer(i -> speicher.put(i.getArgument(0), i.getArgument(1))).when(einstellungen).save(anyString(), anyString(), anyString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/repos/M/ERP", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            anfragen.add(ex.getRequestMethod() + " " + ex.getRequestURI().getPath() + " auth=" + ex.getRequestHeaders().getFirst("Authorization") + " body=" + body);
            boolean issue = ex.getRequestURI().getPath().endsWith("/issues");
            int status = issue ? issueStatus.get() : repoStatus.get();
            byte[] antwort = (issue ? "{\"number\":181,\"html_url\":\"" + adresse + "/M/ERP/issues/181\"}" : "{\"full_name\":\"M/ERP\"}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, antwort.length);
            ex.getResponseBody().write(antwort);
            ex.close();
        });
        server.start();
        adresse = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private EntwicklungsWunschService mitSchluessel(String schluessel) {
        return new EntwicklungsWunschService(einstellungen, new MailSecretService(schluessel, "uploads"));
    }

    @Test
    void speichernNimmtNurSauberAdresseRepositoryUndTokenAnUndGibtDenTokenNieZurueck() {
        EntwicklungsWunschService s = mitSchluessel("");
        Stand st = s.speichern(adresse + "/", "M/ERP", TOKEN);

        assertThat(st.adresse()).isEqualTo(adresse);
        assertThat(st.repository()).isEqualTo("M/ERP");
        assertThat(st.tokenGesetzt()).isTrue();
        assertThat(st.bereit()).isTrue();
        assertThat(st.toString()).doesNotContain(TOKEN);

        assertThatThrownBy(() -> s.speichern("ftp://nas", "M/ERP", null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("http://");
        assertThatThrownBy(() -> s.speichern("http://user:pw@nas:3050", "M/ERP", null)).hasMessageContaining("keinen Benutzer");
        assertThatThrownBy(() -> s.speichern(adresse, "nur-ein-name", null)).hasMessageContaining("Besitzer/Name");
        assertThatThrownBy(() -> s.speichern(adresse, "M/ERP", "zu kurz")).hasMessageContaining("Zugangsschlüssel");
        assertThatThrownBy(() -> s.speichern(adresse, "M/ERP", "mit Leerzeichen im Token 1234")).hasMessageContaining("Zugangsschlüssel");
    }

    @Test
    void tokenNullLaesstDenAltenStehenLeerEntferntIhn() {
        EntwicklungsWunschService s = mitSchluessel("");
        s.speichern(adresse, "M/ERP", TOKEN);
        assertThat(s.speichern(adresse, "M/ERP", null).tokenGesetzt()).isTrue();
        assertThat(s.speichern(adresse, "M/ERP", "").tokenGesetzt()).isFalse();
        assertThat(s.stand().bereit()).isFalse();
    }

    @Test
    void mitEingerichtetemSchluesselLiegtDerTokenVerschluesseltInDerDatenbankUndWirdBeimSendenEntschluesselt() {
        EntwicklungsWunschService s = mitSchluessel("0123456789abcdef0123456789abcdef");
        s.speichern(adresse, "M/ERP", TOKEN);

        assertThat(speicher.get(EntwicklungsWunschService.TOKEN)).startsWith("v1:").doesNotContain(TOKEN);
        assertThat(s.stand().verschluesselt()).isTrue();
        assertThat(s.legeAn("Titel", "Text")).isEqualTo(new Angelegt(181, adresse + "/M/ERP/issues/181"));
        assertThat(anfragen.get(0)).contains("auth=token " + TOKEN);
    }

    @Test
    void ohneSchluesselLiegtDerTokenWieDieAnderenKiSchluesselUndDieOberflaecheSagtEs() {
        EntwicklungsWunschService s = mitSchluessel("");
        s.speichern(adresse, "M/ERP", TOKEN);
        assertThat(speicher.get(EntwicklungsWunschService.TOKEN)).isEqualTo(TOKEN);
        assertThat(s.stand().verschluesselt()).isFalse();
    }

    @Test
    void legeAnSendetTitelUndTextMitDemTokenUndLiefertNummerUndLink() {
        EntwicklungsWunschService s = mitSchluessel("");
        s.speichern(adresse, "M/ERP", TOKEN);

        Angelegt a = s.legeAn("  Lieferschein: Unterschrift löschen  ", "Beim Unterschreiben fehlt ein Zurück-Knopf.");

        assertThat(a.nummer()).isEqualTo(181);
        assertThat(a.adresse()).endsWith("/M/ERP/issues/181");
        assertThat(anfragen).hasSize(1);
        assertThat(anfragen.get(0)).startsWith("POST /api/v1/repos/M/ERP/issues auth=token " + TOKEN)
                .contains("\"title\":\"Lieferschein: Unterschrift löschen\"").contains("Zurück-Knopf");
    }

    @Test
    void fehlerVonGiteaKommenAlsKlartextOhneDenTokenZuNennen() {
        EntwicklungsWunschService s = mitSchluessel("");
        s.speichern(adresse, "M/ERP", TOKEN);

        issueStatus.set(401);
        assertThatThrownBy(() -> s.legeAn("Titel", "Text")).isInstanceOf(IllegalStateException.class).hasMessageContaining("Zugangsschlüssel").hasMessageNotContaining(TOKEN);
        issueStatus.set(404);
        assertThatThrownBy(() -> s.legeAn("Titel", "Text")).hasMessageContaining("Repository");
        issueStatus.set(500);
        assertThatThrownBy(() -> s.legeAn("Titel", "Text")).hasMessageContaining("Fehler 500");
    }

    @Test
    void ohneZugangOderMitUnbrauchbarenAngabenWirdNichtGesendet() {
        EntwicklungsWunschService s = mitSchluessel("");
        assertThatThrownBy(() -> s.legeAn("Titel", "Text")).hasMessageContaining("noch nicht eingerichtet");
        s.speichern(adresse, "M/ERP", TOKEN);
        assertThatThrownBy(() -> s.legeAn(" ", "Text")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Titel");
        assertThatThrownBy(() -> s.legeAn("x".repeat(201), "Text")).hasMessageContaining("Titel");
        assertThatThrownBy(() -> s.legeAn("Titel", "x".repeat(10_001))).hasMessageContaining("zu lang");
        assertThat(anfragen).isEmpty();
    }

    @Test
    void derVerbindungstestLiestNurDasRepositoryUndErklaertFehler() {
        EntwicklungsWunschService s = mitSchluessel("");
        assertThat(s.teste().ok()).isFalse();
        s.speichern(adresse, "M/ERP", TOKEN);

        Testergebnis ok = s.teste();
        assertThat(ok.ok()).isTrue();
        assertThat(ok.meldung()).contains("M/ERP");
        assertThat(anfragen.get(0)).startsWith("GET /api/v1/repos/M/ERP auth=token");

        repoStatus.set(401);
        assertThat(s.teste().meldung()).contains("lehnt den Zugangsschlüssel ab");
        repoStatus.set(404);
        assertThat(s.teste().meldung()).contains("gibt es unter dieser Adresse nicht");
        server.stop(0);
        assertThat(s.teste().meldung()).contains("nicht erreichbar");
    }
}
