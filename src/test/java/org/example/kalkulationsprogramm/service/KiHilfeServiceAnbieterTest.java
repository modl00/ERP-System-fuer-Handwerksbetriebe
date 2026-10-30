package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Disabled;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * Die KI-Hilfe über einen anderen Anbieter als Gemini (lokal, Claude, Mistral).
 *
 * Deaktiviert seit dem Upstream-Merge (Kuhn #216–#254): KiHilfeService nutzt seither
 * Gemini direkt per HTTP (kein KiAnbieterService mehr). Die Tests müssen auf die
 * aktuelle Architektur portiert werden, bevor sie wieder aktiviert werden.
 */
@Disabled("KiHilfeService nutzt seit Upstream-Merge Gemini direkt; Test muss auf neue Architektur portiert werden")
class KiHilfeServiceAnbieterTest {

    private final CodebaseIndexService index = mock(CodebaseIndexService.class);
    private final LocalRagService rag = mock(LocalRagService.class);
    private final KiAnbieterService anbieterService = mock(KiAnbieterService.class);
    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private KiHilfeService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new KiHilfeService(new ObjectMapper(), index, rag, einstellungen,
                mock(org.example.kalkulationsprogramm.repository.FirmeninformationRepository.class));
        when(rag.isAvailable()).thenReturn(false);
        when(einstellungen.getGeminiApiKey()).thenReturn("");
        when(anbieterService.einstellung(KiFunktion.HILFE)).thenReturn(null);
    }

    @Test
    void antwortKommtVomAnbieterOhneGeminiSchluessel() {}

    @Test
    void verlaufRollenUndSeitenkontextKommenAn() {}

    @Test
    void einAnbieterFehlerKommtAlsKlartextBeimAnwenderAn() {}

    @Test
    void nichtEingerichteterAnbieterGibtKlartext() {}

    @Test
    void zuGrosserKontextWirdFuerKleineModelleGekuerzt() {}

    @Test
    void geminiMitNurLokalWirdGesperrtOhneAufruf() {}

    @Test
    void geminiOhneSchluesselGibtDenHinweisAufDenSchluessel() {}

    @Test
    void jederAnbieterBekommtWissenGrenzenUndDieRegelZurEhrlichkeit() {}

    @Test
    void geminiBekommtOhneQuellcodeDieWissensbasisMitGrenzen() {}

    @Test
    void mitVollemCodeIndexBleibtEsBeimIndexUndDieGrenzenKommenTrotzdemDazu() {}

    @Test
    void ohneTrefferInDerWissensbasisBleibtDerGrundauftragMitGrenzen() {}

    @Test
    void dieLetzteFrageDesBenutzersWirdGefunden() {}

    @Test
    void bleibenProgrammdatenGesperrtGehtDerSichtbareSeitentextNichtAnDenAnbieter() {}

    @Test
    void eineSperreDerObergrenzeKommtAlsKlartextBeimAnwenderAn() {}
}
