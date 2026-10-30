package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.FormBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.Document;
import org.openpdf.text.Image;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

/** Logo und Briefpapier im PDF der Standardvorlage. Nur Dummy-Daten (DSGVO). */
class BriefpapierPdfTest {

    RechnungPdfService pdf;
    FirmeninformationService firmenService;
    Firmeninformation firma;
    List<FormBlockDto> bloecke;

    private static byte[] bildBytes(int b, int h) throws Exception {
        BufferedImage bild = new BufferedImage(b, h, BufferedImage.TYPE_INT_RGB);
        var g = bild.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, b, h);
        g.dispose();
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        ImageIO.write(bild, "png", aus);
        return aus.toByteArray();
    }

    @BeforeEach
    void setUp() throws Exception {
        firma = new Firmeninformation();
        firma.setFirmenname("Muster Metallbau GmbH");
        firma.setStrasse("Werkstraße 5");
        firma.setPlz("97070");
        firma.setOrt("Würzburg");
        firma.setIban("DE00 0000 0000 0000 0000 00");
        firmenService = mock(FirmeninformationService.class);
        when(firmenService.ladeEntityFuerPdf()).thenReturn(firma);
        pdf = new RechnungPdfService(firmenService);
        bloecke = AutoAuftragsbestaetigungVersandService.parseVorlagenHtml(StandardVorlagenInhalt.layoutHtml()).formBlocks();
    }

    private PdfReader erzeuge() throws Exception {
        KopfdatenDto kopf = new KopfdatenDto("RE-1", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), "Beispiel GmbH", "Beispiel GmbH\nMusterweg 1\n12345 Musterstadt",
                "Geländer", "KD-1", "Rechnung", null, "PRJ-1", "Anbau");
        List<ContentBlockDto> inhalt = List.of(new ContentBlockDto("SERVICE", null, false, 0, "1", "Geländer", "Edelstahl", BigDecimal.ONE, "Stk",
                BigDecimal.valueOf(100), BigDecimal.valueOf(100), false, null, null, null));
        var layout = RechnungPdfService.createLayoutFromFormBlocks(bloecke, 595f, 842f);
        return new PdfReader(pdf.generatePdfBytes(new RechnungDto(layout, kopf, inhalt, bloecke, null, null, null)));
    }

    private static int bilder(PdfReader r) {
        var res = r.getPageN(1).getAsDict(PdfName.RESOURCES);
        var xo = res == null ? null : res.getAsDict(PdfName.XOBJECT);
        if (xo == null) {
            return 0;
        }
        // Nur echte Bilder zählen, nicht die Formulare (zum Beispiel die Seitenzahl)
        int n = 0;
        for (PdfName name : xo.getKeys()) {
            var objekt = xo.getAsStream(name);
            if (objekt != null && PdfName.IMAGE.equals(objekt.getAsName(PdfName.SUBTYPE))) {
                n++;
            }
        }
        return n;
    }

    private static String inhalt(PdfReader r) throws Exception {
        return new String(r.getPageContent(1), java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    @Test
    void ohneLogoUndBriefpapierBleibtAllesWieBisher() throws Exception {
        PdfReader r = erzeuge();

        assertEquals(0, bilder(r));
        assertTrue(new PdfTextExtractor(r).getTextFromPage(1).contains("Muster Metallbau GmbH"));
    }

    @org.junit.jupiter.api.Disabled("TODO: Logo/Briefpapier wird jetzt über LayoutDto.logoPath und RechnungDto.backgroundImagePage1 (Base64) übergeben – loadLogoImage()/loadBriefpapier() werden nicht mehr aufgerufen; Tests neu schreiben")
    @Test
    void dasFirmenlogoWirdAlsBildGezeichnet() throws Exception {
        when(firmenService.loadLogoImage()).thenReturn(Image.getInstance(bildBytes(400, 200)));

        PdfReader r = erzeuge();

        assertEquals(1, bilder(r), "genau das Logo");
        assertFalse(Pattern.compile("595(\\.0+)? 0 0 842").matcher(inhalt(r)).find(), "und kein Vollseitenbild");
    }

    @org.junit.jupiter.api.Disabled("TODO: Logo/Briefpapier wird jetzt über LayoutDto.logoPath und RechnungDto.backgroundImagePage1 (Base64) übergeben; Tests neu schreiben")
    @Test
    void dasLogoWirdNichtVerzerrtUndPasstInDenBlock() throws Exception {
        when(firmenService.loadLogoImage()).thenReturn(Image.getInstance(bildBytes(1000, 100)));   // sehr breit

        PdfReader r = erzeuge();

        // Der Logo-Block ist 170 x 70: das breite Logo wird 170 x 17 (Seitenverhältnis 10:1), nicht 170 x 70
        var m = Pattern.compile("q\\s+([0-9.]+) 0 0 ([0-9.]+) [0-9.]+ [0-9.]+ cm").matcher(inhalt(r));
        boolean gefunden = false;
        while (m.find()) {
            double b = Double.parseDouble(m.group(1));
            double h = Double.parseDouble(m.group(2));
            if (Math.abs(b / h - 10.0) < 0.2) {
                gefunden = true;
                assertTrue(b <= 170.01 && h <= 70.01, b + " x " + h);
            }
        }
        assertTrue(gefunden, "Logo mit Seitenverhältnis 10:1 nicht gefunden");
    }

    @org.junit.jupiter.api.Disabled("TODO: Logo/Briefpapier wird jetzt über LayoutDto.logoPath und RechnungDto.backgroundImagePage1 (Base64) übergeben; Tests neu schreiben")
    @Test
    void dasBriefpapierBildFuelltDieGanzeSeiteEgalWieGrossOderWelchesFormat() throws Exception {
        for (int[] groesse : new int[][] { { 600, 800 }, { 2480, 3508 }, { 1000, 1000 }, { 3000, 1000 } }) {
            when(firmenService.loadBriefpapier()).thenReturn(new FirmeninformationService.Briefpapier(bildBytes(groesse[0], groesse[1]), false, "image/png"));

            PdfReader r = erzeuge();

            assertTrue(Pattern.compile("595(\\.0+)? 0 0 842(\\.0+)? 0 0 cm").matcher(inhalt(r)).find(),
                    "Briefpapier " + groesse[0] + "x" + groesse[1] + " muss auf 595 x 842 gezogen sein");
        }
    }

    @org.junit.jupiter.api.Disabled("TODO: Logo/Briefpapier wird jetzt über LayoutDto.logoPath und RechnungDto.backgroundImagePage1 (Base64) übergeben; Tests neu schreiben")
    @Test
    void dasBriefpapierErscheintAufJederSeite() throws Exception {
        when(firmenService.loadBriefpapier()).thenReturn(new FirmeninformationService.Briefpapier(bildBytes(600, 800), false, "image/png"));
        List<ContentBlockDto> viele = new java.util.ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            viele.add(new ContentBlockDto("SERVICE", null, false, i, "" + i, "Position " + i, "Beschreibung", BigDecimal.ONE, "Stk",
                    BigDecimal.TEN, BigDecimal.TEN, false, null, null, null));
        }
        KopfdatenDto kopf = new KopfdatenDto("RE-1", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), "Beispiel GmbH", "Beispiel GmbH", "x", "KD-1",
                "Rechnung", null, "PRJ-1", "Anbau");
        var layout = RechnungPdfService.createLayoutFromFormBlocks(bloecke, 595f, 842f);

        PdfReader r = new PdfReader(pdf.generatePdfBytes(new RechnungDto(layout, kopf, viele, bloecke, null, null, null)));

        assertTrue(r.getNumberOfPages() >= 2);
        for (int seite = 1; seite <= r.getNumberOfPages(); seite++) {
            String inhalt = new String(r.getPageContent(seite), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(Pattern.compile("595(\\.0+)? 0 0 842").matcher(inhalt).find(), "Briefpapier auf Seite " + seite);
        }
    }

    @org.junit.jupiter.api.Disabled("TODO: Logo/Briefpapier wird jetzt über LayoutDto.logoPath und RechnungDto.backgroundImagePage1 (Base64) übergeben; Tests neu schreiben")
    @Test
    void einPdfBriefpapierWirdAufA4SkaliertUndGezeichnet() throws Exception {
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        Document d = new Document(new org.openpdf.text.Rectangle(300, 400));   // kleines Format, anderes Seitenverhältnis
        PdfWriter.getInstance(d, aus);
        d.open();
        d.add(new Paragraph("BRIEFKOPF AUS PDF"));
        d.close();
        when(firmenService.loadBriefpapier()).thenReturn(new FirmeninformationService.Briefpapier(aus.toByteArray(), true, "application/pdf"));

        PdfReader r = erzeuge();

        assertTrue(new PdfTextExtractor(r).getTextFromPage(1).contains("BRIEFKOPF AUS PDF"), "der Text des PDF-Briefpapiers steht im Formular");
        assertTrue(Pattern.compile("1\\.98\\d* 0 0 2\\.1\\d* 0 0 cm").matcher(inhalt(r)).find(), "300x400 wird auf 595x842 skaliert: " + inhalt(r).substring(0, 200));
    }

    @Test
    void einEigenerHintergrundDerVorlageHatVorrang() throws Exception {
        when(firmenService.loadBriefpapier()).thenReturn(new FirmeninformationService.Briefpapier(bildBytes(100, 100), false, "image/png"));
        String eigener = java.util.Base64.getEncoder().encodeToString(bildBytes(50, 50));
        KopfdatenDto kopf = new KopfdatenDto("RE-1", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2), "Beispiel GmbH", "Beispiel GmbH", "x", "KD-1",
                "Rechnung", null, "PRJ-1", "Anbau");
        var layout = RechnungPdfService.createLayoutFromFormBlocks(bloecke, 595f, 842f);

        PdfReader r = new PdfReader(pdf.generatePdfBytes(new RechnungDto(layout, kopf, List.of(), bloecke, null, "data:image/png;base64," + eigener, null)));

        assertEquals(1, bilder(r), "nur der eigene Hintergrund, nicht zusätzlich das Firmenbriefpapier");
    }

    @org.junit.jupiter.api.Disabled("TODO: Logo/Briefpapier wird jetzt über LayoutDto.logoPath und RechnungDto.backgroundImagePage1 (Base64) übergeben; Tests neu schreiben")
    @Test
    void enthaeltDasBriefpapierDenKopfEntfallenFirmenangabenUndLogoAberNichtDerRest() throws Exception {
        when(firmenService.loadBriefpapier()).thenReturn(new FirmeninformationService.Briefpapier(bildBytes(600, 800), false, "image/png"));
        when(firmenService.loadLogoImage()).thenReturn(Image.getInstance(bildBytes(400, 200)));
        firma.setBriefpapierEnthaeltKopf(true);

        PdfReader r = erzeuge();

        String text = new PdfTextExtractor(r).getTextFromPage(1);
        assertFalse(text.contains("Muster Metallbau GmbH"), "Firmenname steht schon im Briefpapier: " + text);
        assertFalse(text.contains("DE00 0000"), "Bank steht schon in der Fußzeile des Briefpapiers");
        assertFalse(text.contains("Werkstraße"), "Adresse auch");
        assertEquals(1, bilder(r), "nur das Briefpapier, das Logo entfällt");
        assertTrue(text.contains("Musterstadt"), "die Kundenadresse bleibt");
        assertTrue(text.contains("RE-1"), "die Dokumentnummer bleibt");
        assertTrue(text.contains("Beispiel GmbH"));
    }

    @Test
    void dasKopfFlagOhneBriefpapierBewirktNichts() throws Exception {
        firma.setBriefpapierEnthaeltKopf(true);   // aber keine Datei hinterlegt

        PdfReader r = erzeuge();

        assertTrue(new PdfTextExtractor(r).getTextFromPage(1).contains("Muster Metallbau GmbH"), "ohne Briefpapier bleibt der eigene Briefkopf");
    }
}
