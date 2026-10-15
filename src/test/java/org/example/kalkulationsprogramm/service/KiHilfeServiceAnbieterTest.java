package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.example.kalkulationsprogramm.service.ki.AnbieterTyp;
import org.example.kalkulationsprogramm.service.ki.KiAnbieter;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService.FunktionsEinstellung;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiFunktion.Datenschutz;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Assistent;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAntwort;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Nutzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/** Die KI-Hilfe über einen anderen Anbieter als Gemini (lokal, Claude, Mistral): kein Gemini-Schlüssel nötig, Datenschutz-Stufe wird beachtet. */
class KiHilfeServiceAnbieterTest {

    private final KiAnbieterService anbieterService = mock(KiAnbieterService.class);
    private final KiAnbieter anbieter = mock(KiAnbieter.class);
    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final LocalRagService rag = mock(LocalRagService.class);
    private final KiWissenService wissen = mock(KiWissenService.class);
    private final CodebaseIndexService index = mock(CodebaseIndexService.class);
    private KiHilfeService service;

    private static FunktionsEinstellung einstellung(AnbieterTyp typ, Datenschutz datenschutz) {
        return new FunktionsEinstellung("hilfe", "KI-Hilfe (Chat)", typ, datenschutz);
    }

    @BeforeEach
    void setUp() throws Exception {
        service = new KiHilfeService(new ObjectMapper(), index, rag, einstellungen, anbieterService, wissen, mock(org.example.kalkulationsprogramm.repository.FirmeninformationRepository.class));
        ReflectionTestUtils.setField(service, "temperature", 0.3);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 512);
        when(anbieterService.einstellung(KiFunktion.HILFE)).thenReturn(einstellung(AnbieterTyp.LOKAL, Datenschutz.CLOUD_ERLAUBT));
        when(anbieterService.waehle(KiFunktion.HILFE)).thenReturn(anbieter);
        when(rag.isAvailable()).thenReturn(false);
        when(wissen.grenzen()).thenReturn("GRENZEN: Dokumentnummern sind nicht einstellbar.");
        when(wissen.relevantes(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn("WISSEN: Seite Lager");
        when(index.getIndex()).thenReturn("");
    }

    @Test
    void antwortKommtVomAnbieterOhneGeminiSchluessel() throws Exception {
        when(anbieter.chat(any())).thenReturn(new KiAntwort("Klicken Sie auf Anfragen.", List.of()));

        var ergebnis = service.chat(List.of(new KiHilfeService.ChatMessage("user", "Wo sind Anfragen?")));

        assertEquals("Klicken Sie auf Anfragen.", ergebnis.reply());
        assertTrue(ergebnis.sources().isEmpty());
        verify(einstellungen, never()).getGeminiApiKey();
    }

    @Test
    void verlaufRollenUndSeitenkontextKommenAn() throws Exception {
        when(anbieter.chat(any())).thenReturn(new KiAntwort("ok", List.of()));

        service.chat(List.of(new KiHilfeService.ChatMessage("user", "Hallo"), new KiHilfeService.ChatMessage("model", "Hi"), new KiHilfeService.ChatMessage("user", "Und jetzt?")),
                new KiHilfeService.PageContext("/anfragen", "Anfragen", "Liste der Anfragen", null, null, null));

        ArgumentCaptor<KiAnfrage> anfrage = ArgumentCaptor.forClass(KiAnfrage.class);
        verify(anbieter).chat(anfrage.capture());
        KiAnfrage a = anfrage.getValue();
        assertEquals(3, a.nachrichten().size());
        assertTrue(a.nachrichten().get(0) instanceof Nutzer);
        assertTrue(a.nachrichten().get(1) instanceof Assistent);
        assertTrue(((Nutzer) a.nachrichten().get(2)).text().startsWith("Und jetzt?"));
        assertTrue(((Nutzer) a.nachrichten().get(2)).text().contains("Liste der Anfragen"));
        assertTrue(a.system().contains("Kalkulationsprogramm"), "der Grundauftrag bleibt gleich");
        assertEquals(0.3, a.temperatur());
        assertEquals(512, a.maxTokens());
        assertTrue(a.werkzeuge().isEmpty());
    }

    @Test
    void einAnbieterFehlerKommtAlsKlartextBeimAnwenderAn() throws Exception {
        when(anbieter.chat(any())).thenThrow(new KiAnbieterFehler("Die lokale KI ist nicht erreichbar."));

        var e = assertThrows(KiHilfeService.KiHilfeNichtVerfuegbar.class, () -> service.chat(List.of(new KiHilfeService.ChatMessage("user", "Hallo"))));

        assertEquals("Die lokale KI ist nicht erreichbar.", e.getMessage());
    }

    @Test
    void nichtEingerichteterAnbieterGibtKlartext() throws Exception {
        when(anbieterService.waehle(KiFunktion.HILFE)).thenThrow(new KiAnbieterFehler("Es ist noch kein Claude-Schlüssel hinterlegt."));

        var e = assertThrows(KiHilfeService.KiHilfeNichtVerfuegbar.class, () -> service.chat(List.of(new KiHilfeService.ChatMessage("user", "Hallo"))));

        assertTrue(e.getMessage().contains("Claude-Schlüssel"));
    }

    @Test
    void zuGrosserKontextWirdFuerKleineModelleGekuerzt() throws Exception {
        when(rag.isAvailable()).thenReturn(true);
        when(rag.search(any(), any(), any())).thenReturn(List.of(new LocalRagService.CodeChunkResult("x", "a.tsx", "page", "component", "A", 0.9)));
        when(rag.buildContextFromResults(any())).thenReturn("x".repeat(50_000));
        when(anbieter.chat(any())).thenReturn(new KiAntwort("ok", List.of()));

        service.chat(List.of(new KiHilfeService.ChatMessage("user", "Hallo")));

        ArgumentCaptor<KiAnfrage> anfrage = ArgumentCaptor.forClass(KiAnfrage.class);
        verify(anbieter).chat(anfrage.capture());
        assertTrue(anfrage.getValue().system().length() < 20_000, "Länge: " + anfrage.getValue().system().length());
    }

    @Test
    void geminiMitNurLokalWirdGesperrtOhneAufruf() throws Exception {
        when(anbieterService.einstellung(KiFunktion.HILFE)).thenReturn(einstellung(AnbieterTyp.GEMINI, Datenschutz.NUR_LOKAL));
        doThrow(new KiAnbieterFehler("Für „KI-Hilfe (Chat)“ sind nur lokale KI-Anbieter erlaubt (Datenschutz).")).when(anbieterService).pruefeDatenschutz(KiFunktion.HILFE);

        var e = assertThrows(KiHilfeService.KiHilfeNichtVerfuegbar.class, () -> service.chat(List.of(new KiHilfeService.ChatMessage("user", "Hallo"))));

        assertTrue(e.getMessage().contains("nur lokale"));
        verify(einstellungen, never()).getGeminiApiKey();
    }

    @Test
    void geminiOhneSchluesselGibtDenHinweisAufDenSchluessel() throws Exception {
        when(anbieterService.einstellung(KiFunktion.HILFE)).thenReturn(einstellung(AnbieterTyp.GEMINI, Datenschutz.CLOUD_ERLAUBT));
        when(einstellungen.getGeminiApiKey()).thenReturn("");

        var e = assertThrows(KiHilfeService.KiHilfeNichtVerfuegbar.class, () -> service.chat(List.of(new KiHilfeService.ChatMessage("user", "Hallo"))));

        assertTrue(e.getMessage().contains("Gemini API Key"));
        verify(anbieterService, never()).waehle(any());
    }

    @Test
    void jederAnbieterBekommtWissenGrenzenUndDieRegelZurEhrlichkeit() throws Exception {
        when(anbieter.chat(any())).thenReturn(new KiAntwort("ok", List.of()));

        service.chat(List.of(new KiHilfeService.ChatMessage("user", "Wo stelle ich Dokumentennummern ein?")));

        ArgumentCaptor<KiAnfrage> anfrage = ArgumentCaptor.forClass(KiAnfrage.class);
        verify(anbieter).chat(anfrage.capture());
        String system = anfrage.getValue().system();
        assertTrue(system.contains("WISSEN: Seite Lager"), "passende Auszüge der Wissensbasis");
        assertTrue(system.contains("Bekannte Grenzen") && system.contains("Dokumentnummern sind nicht einstellbar"));
        assertTrue(system.contains("Erfinde nie Menüpunkte"));
        verify(wissen).relevantes(org.mockito.ArgumentMatchers.eq("Wo stelle ich Dokumentennummern ein?"), org.mockito.ArgumentMatchers.eq(KiHilfeService.LOKAL_MAX_KONTEXT));
    }

    @Test
    void geminiBekommtOhneQuellcodeDieWissensbasisMitGrenzen() {
        when(index.getIndex()).thenReturn("x".repeat(473));

        String system = service.buildSystemPrompt(null, "Wo ist das Lager?");

        assertTrue(system.contains("## Wissen über das Programm (passende Auszüge)") && system.contains("WISSEN: Seite Lager"));
        assertTrue(system.contains("Dokumentnummern sind nicht einstellbar"));
        assertTrue(system.contains("Erfinde nie Menüpunkte"));
    }

    @Test
    void mitVollemCodeIndexBleibtEsBeimIndexUndDieGrenzenKommenTrotzdemDazu() {
        when(index.getIndex()).thenReturn("x".repeat(KiHilfeService.INDEX_MINDESTLAENGE + 1));

        String system = service.buildSystemPrompt(null, "Wo ist das Lager?");

        assertTrue(system.contains("KOMPLETTEN Frontend-Quellcode"));
        assertTrue(!system.contains("WISSEN: Seite Lager"), "der Index genügt, kein Doppelwissen");
        assertTrue(system.contains("Bekannte Grenzen"));
    }

    @Test
    void ohneTrefferInDerWissensbasisBleibtDerGrundauftragMitGrenzen() {
        when(index.getIndex()).thenReturn("");
        when(wissen.relevantes(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn("");

        String system = service.buildSystemPrompt(null, "Quatsch");

        assertTrue(!system.contains("## Wissen über das Programm"));
        assertTrue(system.contains("Ehrlich bleiben"));
    }

    @Test
    void dieLetzteFrageDesBenutzersWirdGefunden() {
        assertEquals("B", KiHilfeService.letzteFrage(List.of(new KiHilfeService.ChatMessage("user", "A"), new KiHilfeService.ChatMessage("model", "x"), new KiHilfeService.ChatMessage("user", "B"))));
        assertEquals("", KiHilfeService.letzteFrage(List.of()));
        assertEquals("", KiHilfeService.letzteFrage(List.of(new KiHilfeService.ChatMessage("model", "x"))));
    }

    @Test
    void bleibenProgrammdatenGesperrtGehtDerSichtbareSeitentextNichtAnDenAnbieter() throws Exception {
        when(anbieter.chat(any())).thenReturn(new KiAntwort("ok", List.of()));
        when(anbieterService.sperrtProgrammdaten(any())).thenReturn(true);

        service.chat(List.of(new KiHilfeService.ChatMessage("user", "Und jetzt?")),
                new KiHilfeService.PageContext("/kunden", "Kunden", "Max Mustermann, Musterweg 1, 12345 Musterstadt", null, null, null));

        ArgumentCaptor<KiAnfrage> anfrage = ArgumentCaptor.forClass(KiAnfrage.class);
        verify(anbieter).chat(anfrage.capture());
        String text = ((Nutzer) anfrage.getValue().nachrichten().get(0)).text();
        assertEquals("Und jetzt?", text, "kein Seitentext, nur die Frage");
        assertFalse(text.contains("Musterweg"));
    }

    @Test
    void eineSperreDerObergrenzeKommtAlsKlartextBeimAnwenderAn() throws Exception {
        when(anbieterService.waehle(KiFunktion.HILFE)).thenThrow(new KiAnbieterFehler("Die KI ist ausgeschaltet."));

        var e = assertThrows(KiHilfeService.KiHilfeNichtVerfuegbar.class, () -> service.chat(List.of(new KiHilfeService.ChatMessage("user", "Hallo"))));

        assertTrue(e.getMessage().contains("ausgeschaltet"));
    }
}
