package org.example.kalkulationsprogramm.service.importe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.example.kalkulationsprogramm.service.PasswortSchutz;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Verbindung zu Hero (nur lesen, Schlüssel verschlüsselt). Nur Dummy-Werte, keine echte Verbindung. */
class HeroVerbindungServiceTest {

    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path datenordner;
    private PasswortSchutz schutz;
    private final HttpClient http = mock(HttpClient.class);
    private HeroVerbindungService service;
    private String gespeichert = "";

    @SuppressWarnings("unchecked")
    private HttpResponse<String> antwort(int status, String body) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(body);
        return r;
    }

    @BeforeEach
    void setUp() {
        schutz = new PasswortSchutz(datenordner.toString());
        when(einstellungen.get(HeroVerbindungService.SCHLUESSEL_EINSTELLUNG, "")).thenAnswer(i -> gespeichert);
        org.mockito.Mockito.doAnswer(i -> { gespeichert = i.getArgument(1); return null; }).when(einstellungen).save(org.mockito.ArgumentMatchers.eq(HeroVerbindungService.SCHLUESSEL_EINSTELLUNG), any(), any());
        service = new HeroVerbindungService(einstellungen, schutz, HeroVerbindungService.STANDARD_URL, http);
    }

    @Test
    void derSchluesselWirdVerschluesseltGespeichertUndNieAngezeigt() {
        var status = service.speichereSchluessel("  geheim-abc123  ");
        assertThat(status.schluesselGesetzt()).isTrue();
        assertThat(status.toString()).doesNotContain("geheim");
        assertThat(gespeichert).startsWith("v1:").doesNotContain("geheim");
        assertThat(schutz.entschluessele(gespeichert)).isEqualTo("geheim-abc123");
        assertThat(service.speichereSchluessel("").schluesselGesetzt()).as("leer entfernt").isFalse();
    }

    @Test
    void ungueltigeSchluesselWerdenAbgewiesen() {
        assertThatThrownBy(() -> service.speichereSchluessel("Bearer abc")).hasMessageContaining("Bearer");
        assertThatThrownBy(() -> service.speichereSchluessel("mit leerzeichen")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.speichereSchluessel("x".repeat(501))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nurHeroAdressenMitHttpsSindErlaubt() {
        assertThatThrownBy(() -> new HeroVerbindungService(einstellungen, schutz, "http://login.hero-software.de/x", http)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HeroVerbindungService(einstellungen, schutz, "https://evil.example/hero-software.de", http)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HeroVerbindungService(einstellungen, schutz, "https://hero-software.de.evil.example/x", http)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HeroVerbindungService(einstellungen, schutz, "https://user:pw@login.hero-software.de/x", http)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new HeroVerbindungService(einstellungen, schutz, "https://login.hero-software.de/api/external/v7/graphql", http).status().adresse()).contains("hero-software.de");
    }

    @Test
    void ohneSchluesselOderMitAenderungenWirdNichtGesendet() throws Exception {
        assertThatThrownBy(() -> service.abfrage("{ contacts { id } }")).hasMessageContaining("noch kein Hero-Schlüssel");
        service.speichereSchluessel("k");
        assertThatThrownBy(() -> service.abfrage("mutation { create_contact(x: 1) { id } }")).hasMessageContaining("keine Änderungen");
        assertThatThrownBy(() -> service.abfrage("  Subscription { x }")).hasMessageContaining("keine Änderungen");
        assertThatThrownBy(() -> service.abfrage(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.abfrage("x".repeat(5_001))).hasMessageContaining("zu lang");
        verify(http, never()).send(any(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void eineAbfrageGehtMitBearerSchluesselAnHeroUndGibtDieAntwortZurueck() throws Exception {
        service.speichereSchluessel("geheim-abc123");
        { HttpResponse<String> r = antwort(200, "{\"data\":{\"__typename\":\"Query\"}}"); when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r); }

        var e = service.teste();

        assertThat(e.ok()).isTrue();
        assertThat(e.meldung()).contains("klappt");
        ArgumentCaptor<HttpRequest> req = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(req.capture(), any(HttpResponse.BodyHandler.class));
        assertThat(req.getValue().headers().firstValue("Authorization")).contains("Bearer geheim-abc123");
        assertThat(req.getValue().uri().getHost()).isEqualTo("login.hero-software.de");
        assertThat(req.getValue().method()).isEqualTo("POST");
    }

    @Test
    @SuppressWarnings("unchecked")
    void abgelehnteSchluesselFehlerMeldungenUndVerbindungsfehlerGebenKlartextOhneSchluessel() throws Exception {
        service.speichereSchluessel("geheim-abc123");
        { HttpResponse<String> r = antwort(401, "Unauthorized"); when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r); }
        assertThat(service.teste().meldung()).contains("abgelehnt").doesNotContain("geheim");

        { HttpResponse<String> r = antwort(200, "{\"errors\":[{\"message\":\"Field x not found\"}]}"); when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r); }
        var fehler = service.abfrage("{ x }");
        assertThat(fehler.ok()).isFalse();
        assertThat(fehler.meldung()).contains("Field x not found");

        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("geheim-abc123 kaputt"));
        assertThat(service.teste().meldung()).contains("Keine Verbindung").doesNotContain("geheim");
    }

    @Test
    @SuppressWarnings("unchecked")
    void langeAntwortenWerdenGekuerzt() throws Exception {
        service.speichereSchluessel("k");
        { HttpResponse<String> r = antwort(200, "{\"data\":{\"a\":\"" + "x".repeat(200_000) + "\"}}"); when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r); }
        var e = service.abfrage("{ a }");
        assertThat(e.antwort().length()).isLessThan(HeroVerbindungService.MAX_ANTWORT + 50);
        assertThat(e.antwort()).endsWith("(gekürzt)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void dieSchnittstelleWirdAlsListeMitArgumentenUndTypenGelesen() throws Exception {
        service.speichereSchluessel("k");
        String antwort = "{\"data\":{\"__schema\":{\"queryType\":{\"fields\":[{\"name\":\"contacts\",\"args\":[{\"name\":\"offset\",\"type\":{\"name\":\"Int\",\"kind\":\"SCALAR\"}}],"
                + "\"type\":{\"kind\":\"NON_NULL\",\"ofType\":{\"kind\":\"LIST\",\"ofType\":{\"name\":\"Contact\",\"kind\":\"OBJECT\"}}}}]}}}}";
        { HttpResponse<String> r = antwort(200, antwort); when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(r); }

        List<HeroVerbindungService.Feld> felder = service.schnittstelle();

        assertThat(felder).singleElement().satisfies(f -> {
            assertThat(f.name()).isEqualTo("contacts");
            assertThat(f.argumente()).isEqualTo("offset: Int");
            assertThat(f.typ()).isEqualTo("[Contact]!");
        });
    }
}
