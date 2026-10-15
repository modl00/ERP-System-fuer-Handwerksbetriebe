package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.example.kalkulationsprogramm.service.KiModellService;
import org.example.kalkulationsprogramm.service.LokalerKiService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.FunktionsEingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** Modell je Funktion und Anbieter (Einstellungen → KI). Nur Dummy-Schlüssel. */
class KiFunktionsModellTest {

    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final LokalerKiService lokal = mock(LokalerKiService.class);
    private final KiModellService modelle = mock(KiModellService.class);
    private final Map<String, String> gespeichert = new HashMap<>();
    private KiAnbieterService service;

    @BeforeEach
    void setUp() {
        when(einstellungen.get(anyString(), any())).thenAnswer(a -> gespeichert.getOrDefault(a.<String>getArgument(0), a.getArgument(1)));
        org.mockito.Mockito.doAnswer(a -> { gespeichert.put(a.getArgument(0), a.getArgument(1)); return null; }).when(einstellungen).save(anyString(), any(), any());
        when(einstellungen.getGeminiApiKey()).thenReturn("gem-key");
        when(modelle.modell(any(), any())).thenAnswer(a -> a.getArgument(1) != null ? a.getArgument(1) : "gemini-3-flash-preview");
        when(modelle.standard(any())).thenReturn("gemini-standard");
        when(lokal.istKonfiguriert()).thenReturn(true);
        when(lokal.konfiguration()).thenReturn(new LokalerKiService.Konfiguration("http://pc:11434", "qwen2.5:7b", false, false));
        when(lokal.schluessel()).thenReturn("");
        service = new KiAnbieterService(einstellungen, lokal, modelle,
                new KiObergrenze(einstellungen, mock(org.example.kalkulationsprogramm.repository.KiAktionProtokollRepository.class)));
        gespeichert.put("ki.mistral.schluessel", "dummy-schluessel");
        gespeichert.put("ki.claude.schluessel", "dummy-schluessel");
    }

    private KiAnbieterService.FunktionsEinstellung zeile(KiAnbieterService.Uebersicht u, String schluessel) {
        return u.funktionen().stream().filter(f -> f.funktion().equals(schluessel)).findFirst().orElseThrow();
    }

    @Test
    void ohneAuswahlGiltDasStandardmodellDesAnbieters() throws Exception {
        gespeichert.put("ki.email-text.anbieter", "MISTRAL");
        var e = zeile(service.uebersicht(), "email-text");
        assertThat(e.modell()).isEqualTo("mistral-small-latest");
        assertThat(e.standardModell()).isEqualTo("mistral-small-latest");
        assertThat(e.modellGewaehlt()).isFalse();
        assertThat(service.waehle(KiFunktion.EMAIL_TEXT).modell()).isEqualTo("mistral-small-latest");
    }

    @Test
    void jedeFunktionKannBeimGleichenAnbieterEinAnderesModellHaben() throws Exception {
        service.speichern(Map.of(
                "email-text", new FunktionsEingabe("MISTRAL", "CLOUD_ERLAUBT", "mistral-large-latest"),
                "email-einordnen", new FunktionsEingabe("MISTRAL", "CLOUD_ERLAUBT", "ministral-3b-latest")), null, null);

        assertThat(gespeichert).containsEntry("ki.funktion-modell.email-text.MISTRAL", "mistral-large-latest")
                .containsEntry("ki.funktion-modell.email-einordnen.MISTRAL", "ministral-3b-latest");
        assertThat(service.waehle(KiFunktion.EMAIL_TEXT).modell()).isEqualTo("mistral-large-latest");
        assertThat(service.waehle(KiFunktion.EMAIL_EINORDNEN).modell()).isEqualTo("ministral-3b-latest");
        assertThat(zeile(service.uebersicht(), "email-text").modellGewaehlt()).isTrue();
    }

    @Test
    void dieAuswahlGehoertZumAnbieterUndBleibtBeimWechselErhalten() throws Exception {
        service.speichern(Map.of("email-text", new FunktionsEingabe("MISTRAL", "CLOUD_ERLAUBT", "mistral-large-latest")), null, null);
        service.speichern(Map.of("email-text", new FunktionsEingabe("CLAUDE", "CLOUD_ERLAUBT", null)), null, null);

        assertThat(service.waehle(KiFunktion.EMAIL_TEXT).modell()).as("Claude hat keine eigene Wahl: Standard von Claude").isEqualTo("claude-sonnet-5-5");
        service.speichern(Map.of("email-text", new FunktionsEingabe("MISTRAL", "CLOUD_ERLAUBT", null)), null, null);
        assertThat(service.waehle(KiFunktion.EMAIL_TEXT).modell()).as("null = unverändert, die Wahl für Mistral gilt wieder").isEqualTo("mistral-large-latest");
    }

    @Test
    void leereEingabeStelltDenStandardWiederHer() throws Exception {
        service.speichern(Map.of("email-text", new FunktionsEingabe("MISTRAL", "CLOUD_ERLAUBT", "mistral-large-latest")), null, null);
        service.speichern(Map.of("email-text", new FunktionsEingabe("MISTRAL", "CLOUD_ERLAUBT", "")), null, null);
        assertThat(service.waehle(KiFunktion.EMAIL_TEXT).modell()).isEqualTo("mistral-small-latest");
    }

    @Test
    void beiGeminiGiltDieBestehendeModellwahlJeFunktion() {
        service.speichern(Map.of("email-einordnen", new FunktionsEingabe("GEMINI", "CLOUD_ERLAUBT", "gemini-2.5-flash")), null, null);

        verify(modelle).speichern(Map.of("email-classification", "gemini-2.5-flash"), null);
        assertThat(gespeichert).as("für Gemini wird keine zweite Quelle angelegt").doesNotContainKey("ki.funktion-modell.email-einordnen.GEMINI");
    }

    @Test
    void ungueltigeModellnamenWerdenAbgelehnt() {
        for (String schlecht : new String[] {"../etc/passwd", "a b", "x;y", "<script>alert(1)</script>", "'; DROP TABLE x; --", "a".repeat(10_001)}) {
            assertThatThrownBy(() -> service.speichern(Map.of("email-text", new FunktionsEingabe("MISTRAL", "CLOUD_ERLAUBT", schlecht)), null, null))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Modellname");
        }
        assertThat(gespeichert).doesNotContainKey("ki.funktion-modell.email-text.MISTRAL");
        verify(modelle, never()).speichern(any(), any());
    }

    @Test
    void testMitModellPrueftDenNamenVorDemAufruf() {
        assertThat(service.teste(AnbieterTyp.MISTRAL, "../geheim").ok()).isFalse();
        assertThat(service.teste(AnbieterTyp.MISTRAL, "../geheim").meldung()).contains("Modellname");
        assertThat(service.teste(AnbieterTyp.LOKAL, "x y").ok()).isFalse();
    }

    @Test
    void derAssistentFragtBeiGeminiOhneEigeneWahlMitDemModellDerKiHilfe() {
        when(modelle.modell(eq(KiModellService.Funktion.HILFE), any())).thenReturn("gemini-hilfe-modell");
        gespeichert.put("ki.assistent.anbieter", "GEMINI");
        gespeichert.put("ki.datenschutz.assistent", "CLOUD_ERLAUBT");
        assertThat(zeile(service.uebersicht(), "assistent").standardModell()).isEqualTo("gemini-hilfe-modell");
    }

    @Test
    void dieUebersichtNenntJeAnbieterDieAuswahlUndDenStandard() {
        service.speichern(Map.of("email-text", new FunktionsEingabe("MISTRAL", "CLOUD_ERLAUBT", "mistral-large-latest")), null, null);
        var e = zeile(service.uebersicht(), "email-text");

        assertThat(e.auswahl()).containsEntry("MISTRAL", "mistral-large-latest").containsEntry("CLAUDE", "").containsEntry("LOKAL", "");
        assertThat(e.standards()).containsEntry("MISTRAL", "mistral-small-latest").containsEntry("CLAUDE", "claude-sonnet-5-5")
                .containsEntry("LOKAL", "qwen2.5:7b").containsKey("GEMINI");
    }

    @Test
    void jedeFunktionHatEineGeminiZuordnung() {
        for (KiFunktion f : KiFunktion.values()) {
            assertThat(f.geminiFunktion()).as(f.name()).isNotNull();
        }
    }
}
