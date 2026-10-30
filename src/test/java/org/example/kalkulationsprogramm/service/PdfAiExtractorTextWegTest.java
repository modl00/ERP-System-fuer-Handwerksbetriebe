package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Path;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTextWeiche;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.ObjectMapper;

/** PDF auslesen über einen anderen Anbieter als Gemini: Text aus dem PDF, nur Text an die KI; Scans ohne Textschicht gehen nirgends hin (#232). Nur Dummy-Daten. */
class PdfAiExtractorTextWegTest {

    @TempDir
    Path ordner;

    private final KiTextWeiche weiche = mock(KiTextWeiche.class);
    private final PdfAiExtractorService service = new PdfAiExtractorService(new ObjectMapper(), mock(SystemSettingsService.class));

    private Path pdfMitText(String text) throws Exception {
        Path datei = ordner.resolve("rechnung.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage seite = new PDPage();
            doc.addPage(seite);
            try (PDPageContentStream s = new PDPageContentStream(doc, seite)) {
                s.beginText();
                s.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                s.newLineAtOffset(50, 700);
                s.showText(text);
                s.endText();
            }
            doc.save(datei.toFile());
        }
        return datei;
    }

    @Test
    void ohneGemini_gehtNurDerTextAnDenGewaehltenAnbieter() throws Exception {
        ReflectionTestUtils.setField(service, "weiche", weiche);
        when(weiche.istGemini(KiFunktion.PDF_AUSLESEN)).thenReturn(false);
        when(weiche.frage(eq(KiFunktion.PDF_AUSLESEN), any(), contains("Rechnung Nr. 4711 Max Mustermann GmbH Gesamtbetrag 119,00 EUR"), anyDouble(), anyInt(), anyBoolean()))
                .thenReturn("```json\n{\"dokumenttyp\":\"RECHNUNG\",\"dokumentnummer\":\"4711\",\"rechnungsdatum\":\"2026-10-01\",\"betrag\":119.00,\"kundenname\":\"Max Mustermann GmbH\"}\n```");

        var ergebnis = service.analyze(pdfMitText("Rechnung Nr. 4711 Max Mustermann GmbH Gesamtbetrag 119,00 EUR Zahlbar innerhalb von 14 Tagen netto").toString());

        assertThat(ergebnis).isPresent();
        assertThat(ergebnis.get().getRechnungsnummer()).isEqualTo("4711");
        assertThat(ergebnis.get().getBetrag()).isEqualByComparingTo(new BigDecimal("119.00"));
    }

    @Test
    void einScanOhneTextschichtGehtAnKeinenAnbieter() throws Exception {
        ReflectionTestUtils.setField(service, "weiche", weiche);
        when(weiche.istGemini(KiFunktion.PDF_AUSLESEN)).thenReturn(false);
        when(weiche.anbieterName(KiFunktion.PDF_AUSLESEN)).thenReturn("lokal");

        var ergebnis = service.analyze(pdfMitText("Seite 1").toString());

        assertThat(ergebnis).isEmpty();
        verify(weiche, never()).frage(any(), any(), any(), anyDouble(), anyInt(), anyBoolean());
    }

    @Test
    void einKaputteAntwortLiefertNichtsStattEinesFehlers() throws Exception {
        ReflectionTestUtils.setField(service, "weiche", weiche);
        when(weiche.istGemini(KiFunktion.PDF_AUSLESEN)).thenReturn(false);
        when(weiche.anbieterName(KiFunktion.PDF_AUSLESEN)).thenReturn("mistral");
        when(weiche.frage(any(), any(), any(), anyDouble(), anyInt(), anyBoolean())).thenReturn("Das ist leider kein JSON");

        assertThat(service.analyze(pdfMitText("Rechnung Nr. 1 Beispiel GmbH Gesamtbetrag 10,00 EUR zahlbar sofort ohne Abzug nach Erhalt").toString())).isEmpty();
    }

    @Test
    void dieFunktionIstEinstellbarMitGeminiAlsStandard() {
        assertThat(KiFunktion.ausSchluessel("pdf-auslesen")).contains(KiFunktion.PDF_AUSLESEN);
        assertThat(KiFunktion.PDF_AUSLESEN.standardAnbieter()).isEqualTo(org.example.kalkulationsprogramm.service.ki.AnbieterTyp.GEMINI);
    }
}
