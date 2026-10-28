package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;

import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.FunktionsEinstellung;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAntwort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Text-Funktionen (E-Mail) laufen über den gewählten Anbieter, bei Gemini bleibt der bisherige Weg. Nur Dummy-Daten. */
class KiTextWeicheTest {

    KiAnbieterService anbieterService = mock(KiAnbieterService.class);
    KiAnbieter mistral = mock(KiAnbieter.class);
    KiTextWeiche weiche = new KiTextWeiche(anbieterService);

    private void waehle(KiFunktion f, AnbieterTyp typ) {
        when(anbieterService.einstellung(f)).thenReturn(new FunktionsEinstellung(f.schluessel(), f.anzeige(), typ, KiFunktion.Datenschutz.CLOUD_ERLAUBT));
    }

    @BeforeEach
    void setUp() throws Exception {
        when(anbieterService.waehle(KiFunktion.EMAIL_EINORDNEN)).thenReturn(mistral);
    }

    @Test
    void geminiBleibtDerBisherigeWeg() {
        waehle(KiFunktion.EMAIL_EINORDNEN, AnbieterTyp.GEMINI);
        assertThat(weiche.istGemini(KiFunktion.EMAIL_EINORDNEN)).isTrue();
    }

    @Test
    void einAndererAnbieterWieMistralBekommtSystemTextUndFrage() throws Exception {
        waehle(KiFunktion.EMAIL_EINORDNEN, AnbieterTyp.MISTRAL);
        when(mistral.chat(any())).thenReturn(new KiAntwort("{\"key\":\"P1\"}", null));

        assertThat(weiche.istGemini(KiFunktion.EMAIL_EINORDNEN)).isFalse();
        String antwort = weiche.frage(KiFunktion.EMAIL_EINORDNEN, "Ordne zu", "Hallo, Anbau Halle", 0.1, 2048, true);

        assertThat(antwort).isEqualTo("{\"key\":\"P1\"}");
        ArgumentCaptor<KiAnfrage> c = ArgumentCaptor.forClass(KiAnfrage.class);
        verify(mistral).chat(c.capture());
        assertThat(c.getValue().system()).isEqualTo("Ordne zu");
        assertThat(c.getValue().nachrichten()).hasSize(1);
        assertThat(c.getValue().werkzeuge()).isEmpty();
        assertThat(c.getValue().temperatur()).isEqualTo(0.1);
    }

    @Test
    void codeZaeuneUmEineJsonAntwortWerdenEntfernt() throws Exception {
        waehle(KiFunktion.EMAIL_EINORDNEN, AnbieterTyp.MISTRAL);
        when(mistral.chat(any())).thenReturn(new KiAntwort("```json\n{\"key\":\"P1\"}\n```", null));
        assertThat(weiche.frage(KiFunktion.EMAIL_EINORDNEN, "s", "u", 0.1, 100, true)).isEqualTo("{\"key\":\"P1\"}");
        assertThat(weiche.frage(KiFunktion.EMAIL_EINORDNEN, "s", "u", 0.1, 100, false)).startsWith("```json");
        assertThat(KiTextWeiche.ohneCodeZaeune("  {\"a\":1}  ")).isEqualTo("{\"a\":1}");
        assertThat(KiTextWeiche.ohneCodeZaeune(null)).isEmpty();
        // Thinking-Text vor dem Code-Block (Gemini 2.5 Flash)
        assertThat(KiTextWeiche.ohneCodeZaeune("Hier ist das Ergebnis:\n```json\n{\"b\":2}\n```")).isEqualTo("{\"b\":2}");
    }

    @Test
    void nichtEingerichteterAnbieterGibtKlartextFehlerUndBereitIstFalse() throws Exception {
        when(anbieterService.waehle(KiFunktion.EMAIL_TEXT)).thenThrow(new KiAnbieterFehler("Für Mistral ist noch kein Schlüssel eingetragen (Einstellungen → KI)."));
        assertThat(weiche.bereit(KiFunktion.EMAIL_TEXT)).isFalse();
        assertThatThrownBy(() -> weiche.frage(KiFunktion.EMAIL_TEXT, "s", "u", 0.2, 100, true)).isInstanceOf(IOException.class).hasMessageContaining("Für Mistral ist noch kein Schlüssel");
        assertThat(weiche.bereit(KiFunktion.EMAIL_EINORDNEN)).isTrue();
    }

    @Test
    void dieEmailFunktionenStehenInDerListeDerWaehlbarenFunktionen() {
        assertThat(KiFunktion.ausSchluessel("email-einordnen")).contains(KiFunktion.EMAIL_EINORDNEN);
        assertThat(KiFunktion.ausSchluessel("email-text")).contains(KiFunktion.EMAIL_TEXT);
        assertThat(KiFunktion.EMAIL_EINORDNEN.standardAnbieter()).as("Standard bleibt wie bisher").isEqualTo(AnbieterTyp.GEMINI);
        assertThat(KiFunktion.EMAIL_TEXT.standardAnbieter()).isEqualTo(AnbieterTyp.GEMINI);
    }

    @Test
    void dateienGehenAlsAnhangDerNutzernachrichtAnDenAnbieter() throws Exception {
        waehle(KiFunktion.EMAIL_EINORDNEN, AnbieterTyp.MISTRAL);
        when(mistral.chat(any())).thenReturn(new KiAntwort("Rechnung_Muster", null));

        String antwort = weiche.frageMitDateien(KiFunktion.EMAIL_EINORDNEN, "Benenne", "Name?",
                java.util.List.of(new KiTypen.Anhang("image/jpeg", new byte[] { 1, 2 })), 0.0, 100, false);

        assertThat(antwort).isEqualTo("Rechnung_Muster");
        ArgumentCaptor<KiAnfrage> c = ArgumentCaptor.forClass(KiAnfrage.class);
        verify(mistral).chat(c.capture());
        var nutzer = (KiTypen.Nutzer) c.getValue().nachrichten().get(0);
        assertThat(nutzer.text()).isEqualTo("Name?");
        assertThat(nutzer.anhaenge()).hasSize(1);
        assertThat(nutzer.anhaenge().get(0).mime()).isEqualTo("image/jpeg");
    }

    @Test
    void kannDerAnbieterDieDateiNichtLesenKommtEinKlartextFehlerAlsIoException() throws Exception {
        waehle(KiFunktion.EMAIL_EINORDNEN, AnbieterTyp.CLAUDE);
        when(anbieterService.waehle(KiFunktion.EMAIL_EINORDNEN)).thenThrow(new KiAnbieterFehler("Claude kann keine Aufnahmen lesen."));
        assertThatThrownBy(() -> weiche.frageMitDateien(KiFunktion.EMAIL_EINORDNEN, "s", "u",
                java.util.List.of(new KiTypen.Anhang("audio/webm", new byte[] { 1 })), 0.0, 100, false))
                .isInstanceOf(IOException.class).hasMessageContaining("Aufnahmen");
    }
}
