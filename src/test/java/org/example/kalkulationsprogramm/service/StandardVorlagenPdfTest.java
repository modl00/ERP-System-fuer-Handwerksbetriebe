package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.FormBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/** Die Standardvorlage muss sich wie eine im Editor gespeicherte Vorlage lesen lassen und im PDF die Firmendaten zeigen. */
class StandardVorlagenPdfTest {

    private RechnungPdfService pdf;
    private List<FormBlockDto> bloecke;

    @BeforeEach
    void setUp() {
        Firmeninformation f = new Firmeninformation();
        f.setFirmenname("Muster Metallbau GmbH");
        f.setStrasse("Werkstraße 5");
        f.setPlz("97070");
        f.setOrt("Würzburg");
        f.setTelefon("0931 123456");
        f.setIban("DE00 0000 0000 0000 0000 00");
        f.setSteuernummer("123/456/78901");
        FirmeninformationService firmiServ = mock(FirmeninformationService.class);
        when(firmiServ.ladeEntityFuerPdf()).thenReturn(f);
        pdf = new RechnungPdfService(firmiServ);
        bloecke = AutoAuftragsbestaetigungVersandService.parseVorlagenHtml(StandardVorlagenInhalt.layoutHtml()).formBlocks();
    }

    @Test
    void layoutEnthaeltAlleBloeckeAufBeidenSeiten() {
        assertEquals(2, bloecke.stream().filter(b -> b.page() == 2 && "table".equals(b.type())).count() + 1);
        assertEquals(1, bloecke.stream().filter(b -> b.page() == 1 && "table".equals(b.type())).count());
        assertTrue(bloecke.stream().anyMatch(b -> "seitenzahl".equals(b.type()) && b.page() == 2));
        assertTrue(bloecke.stream().allMatch(b -> b.x() >= 0 && b.x() + b.width() <= 595 && b.y() + b.height() <= 842),
                "kein Block ragt über den A4-Rand");
        assertTrue(bloecke.stream().anyMatch(b -> "{{KUNDENADRESSE}}".equals(b.content())));
    }

    @Test
    void pdfZeigtFirmendatenKundeUndDokumentangaben() throws Exception {
        KopfdatenDto kopf = new KopfdatenDto("RE-2026/03/00042", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 2, 28),
                "Beispiel GmbH", "Beispiel GmbH\nMusterweg 1\n12345 Musterstadt", "Geländer Musterweg 1", "KD-10023",
                "Rechnung", null, "PRJ-1", "Anbau Musterweg 1");
        List<ContentBlockDto> inhalt = List.of(new ContentBlockDto("SERVICE", null, false, 0, "1", "Geländer",
                "Edelstahl", BigDecimal.ONE, "Stk", BigDecimal.valueOf(100), BigDecimal.valueOf(100), false, null, null, null));
        RechnungPdfService.LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(bloecke, 595f, 842f);

        byte[] bytes = pdf.generatePdfBytes(new RechnungDto(layout, kopf, inhalt, bloecke, null, null, null));
        PdfReader leser = new PdfReader(bytes);
        String text = new PdfTextExtractor(leser).getTextFromPage(1);

        assertTrue(text.contains("Muster Metallbau GmbH"), text);
        assertTrue(text.contains("Werkstraße 5"), text);
        assertTrue(text.contains("97070 Würzburg"), text);
        assertTrue(text.contains("0931 123456"), "Telefon der Firma als Ansprechpartner: " + text);
        assertTrue(text.contains("IBAN: DE00 0000 0000 0000 0000 00"), text);
        assertTrue(text.contains("Steuernr.: 123/456/78901"), text);
        assertTrue(text.contains("Musterstadt"), text);
        assertTrue(text.contains("RE-2026/03/00042"), text);
        assertTrue(text.contains("KD-10023"), text);
        assertFalse(text.contains("{{"), "kein Platzhalter bleibt stehen: " + text);
    }

    @Test
    void jederDokumenttypHatEineFamilieUndStandardtexte() {
        for (Dokumenttyp typ : Dokumenttyp.values()) {
            assertTrue(StandardVorlagenInhalt.FAMILIEN.stream().anyMatch(f -> f.typen().contains(typ)), "Familie fehlt für " + typ);
            StandardVorlagenInhalt.Texte t = StandardVorlagenInhalt.texte(typ);
            assertTrue(t != null && !t.vortext().isBlank() && !t.nachtext().isBlank(), "Texte fehlen für " + typ);
        }
    }
}
