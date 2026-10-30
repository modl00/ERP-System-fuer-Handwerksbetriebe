package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.service.KiModellService.Funktion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.ObjectMapper;

/** Nur Dummy-Daten, kein Zugriff auf Google. */
class KiModellServiceTest {

    SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    MockEnvironment umgebung = new MockEnvironment();
    KiModellService service;
    Map<String, String> gespeichert = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new KiModellService(einstellungen, umgebung);
        ReflectionTestUtils.setField(service, "websucheStandard", true);
        when(einstellungen.get(anyString(), any())).thenAnswer(a -> gespeichert.getOrDefault(a.<String>getArgument(0), a.getArgument(1)));
        org.mockito.Mockito.doAnswer(a -> { gespeichert.put(a.getArgument(0), a.getArgument(1)); return null; })
                .when(einstellungen).save(anyString(), any(), any());
    }

    @Test
    void ohneAuswahlGiltDerStandardDesDienstes() {
        assertEquals("gemini-a", service.modell(Funktion.HILFE, "gemini-a"));
        assertEquals("gemini-2.5-flash", service.modell(Funktion.HILFE, null), "ohne alles: die eingebaute Reserve");
    }

    @Test
    void eineGewaehlteAuswahlSchlaegtDenStandard() {
        gespeichert.put("ki.modell.ki-hilfe", " gemini-2.5-flash ");

        assertEquals("gemini-2.5-flash", service.modell(Funktion.HILFE, "gemini-a"));
        assertEquals("gemini-a", service.modell(Funktion.SCANNER, "gemini-a"), "andere Funktionen bleiben beim Standard");
    }

    @Test
    void eineLeereAuswahlBedeutetStandardWieder() {
        gespeichert.put("ki.modell.scanner", "   ");
        assertEquals("gemini-a", service.modell(Funktion.SCANNER, "gemini-a"));
    }

    @Test
    void dieUebersichtZeigtAuswahlUndStandardAusDerKonfiguration() {
        umgebung.setProperty("ai.ki-hilfe.model", "gemini-konfig");
        gespeichert.put("ki.modell.pro", "gemini-pro-x");

        KiModellService.Uebersicht u = service.uebersicht();

        var hilfe = u.funktionen().stream().filter(f -> f.schluessel().equals("ki-hilfe")).findFirst().orElseThrow();
        assertEquals("gemini-konfig", hilfe.modell());
        assertEquals("gemini-konfig", hilfe.standard());
        assertFalse(hilfe.gewaehlt());
        var pro = u.funktionen().stream().filter(f -> f.schluessel().equals("pro")).findFirst().orElseThrow();
        assertEquals("gemini-pro-x", pro.modell());
        assertTrue(pro.gewaehlt());
        assertEquals(Funktion.values().length, u.funktionen().size());
        assertTrue(u.websuche());
    }

    @Test
    void speichernSetztDieAuswahlUndDieWebsuche() {
        service.speichern(Map.of("ki-hilfe", "gemini-2.5-flash", "scanner", ""), false);

        assertEquals("gemini-2.5-flash", gespeichert.get("ki.modell.ki-hilfe"));
        assertEquals("", gespeichert.get("ki.modell.scanner"));
        assertEquals("false", gespeichert.get("ki.hilfe.websuche"));
        assertFalse(service.websuche());
    }

    @Test
    void speichernLehntUnbekannteFunktionenUndUngueltigeNamenAbOhneEtwasZuSchreiben() {
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(Map.of("gibt-es-nicht", "gemini-a"), null)).getReason().contains("Unbekannte"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(Map.of("ki-hilfe", "../etc/passwd"), null)).getReason().contains("ungültig"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(Map.of("ki-hilfe", "gemini a;drop"), null)).getReason().contains("ungültig"));
        verify(einstellungen, never()).save(anyString(), any(), any());
    }

    @Test
    void ohneWebsucheEintragGiltDerStandard() {
        ReflectionTestUtils.setField(service, "websucheStandard", false);
        assertFalse(service.websuche());
        gespeichert.put("ki.hilfe.websuche", "true");
        assertTrue(service.websuche());
    }

    @Test
    void ausDerModellListeBleibenNurTextModelleUebrig() throws Exception {
        String antwort = """
                {"models":[
                 {"name":"models/gemini-2.5-flash","displayName":"Gemini 2.5 Flash","description":"schnell","supportedGenerationMethods":["generateContent","countTokens"]},
                 {"name":"models/text-embedding-004","displayName":"Embedding","supportedGenerationMethods":["embedContent"]},
                 {"name":"models/imagen-4","displayName":"Bild","supportedGenerationMethods":["predict"]},
                 {"name":"models/gemini-2.0-flash-preview-image-generation","displayName":"Bild2","supportedGenerationMethods":["generateContent"]},
                 {"name":"models/gemini-2.5-flash-preview-tts","displayName":"Sprache","supportedGenerationMethods":["generateContent"]},
                 {"name":"models/gemini-1.5-pro","displayName":"Pro","supportedGenerationMethods":["generateContent"]}
                ]}""";

        List<KiModellService.VerfuegbaresModell> liste = KiModellService.modelleAus(new ObjectMapper().readTree(antwort));

        assertEquals(List.of("gemini-1.5-pro", "gemini-2.5-flash"), liste.stream().map(KiModellService.VerfuegbaresModell::name).toList());
        assertEquals("Gemini 2.5 Flash", liste.get(1).anzeigename());
    }

    @Test
    void fehlercodesWerdenInKlartextErklaert() {
        assertTrue(KiModellService.ergebnis("m", 200).ok());
        KiModellService.Testergebnis kontingent = KiModellService.ergebnis("m", 429);
        assertFalse(kontingent.ok());
        assertEquals("KONTINGENT", kontingent.art());
        assertTrue(kontingent.meldung().contains("Kontingent"));
        assertEquals("NICHT_GEFUNDEN", KiModellService.ergebnis("gemini-x", 404).art());
        assertTrue(KiModellService.ergebnis("gemini-x", 404).meldung().contains("gemini-x"));
        assertEquals("KEIN_ZUGRIFF", KiModellService.ergebnis("m", 403).art());
        assertEquals("SCHLUESSEL", KiModellService.ergebnis("m", 400).art());
        assertEquals("FEHLER", KiModellService.ergebnis("m", 503).art());
        assertTrue(KiModellService.klartext(503, "x").contains("überlastet"));
    }

    @Test
    void derTestPrueftDenModellnamenUndDenSchluesselVorDemAufruf() {
        assertFalse(service.teste("../x").ok());
        when(einstellungen.getGeminiApiKey()).thenReturn("");
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.teste("gemini-2.5-flash")).getReason().contains("API Key"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.verfuegbare()).getReason().contains("API Key"));
        verify(einstellungen, org.mockito.Mockito.times(2)).getGeminiApiKey();
    }
}
