package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.dto.Formular.VorlagenEinstellungen;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.FormBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RechnungDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

/** Das Lieferschein-Layout im Stil des Beispiels: Absenderzeile, Infoblock, BV-Zeile, Titel, Tabelle mit Artikelnummer ohne Preise, Fußzeile. Nur Dummy-Daten. */
class StandardVorlagenLieferscheinPdfTest {

    private RechnungPdfService pdf;

    @BeforeEach
    void setUp() {
        pdf = new RechnungPdfService();
        FirmeninformationRepository repo = mock(FirmeninformationRepository.class);
        Firmeninformation f = new Firmeninformation();
        f.setFirmenname("Muster Metallbau GmbH");
        f.setStrasse("Werkstraße 5");
        f.setPlz("97070");
        f.setOrt("Würzburg");
        f.setTelefon("0931 123456");
        f.setEmail("info@muster-metallbau.example");
        f.setGeschaeftsfuehrer("Max Mustermann");
        f.setHandelsregister("Amtsgericht Würzburg");
        f.setHandelsregisterNummer("HRB 12345");
        f.setUstIdNr("DE123456789");
        f.setBankName("Mustersparkasse");
        f.setIban("DE00 0000 0000 0000 0000 00");
        f.setBic("MUSTDE00XXX");
        when(repo.findById(1L)).thenReturn(Optional.of(f));
        ReflectionTestUtils.setField(pdf, "firmeninformationRepository", repo);
    }

    private static ContentBlockDto text(String html) {
        return new ContentBlockDto("TEXT", html, false, 10, null, null, null, null, null, null, null, false, null, null, null);
    }

    private static ContentBlockDto gruppe(String pos, String name) {
        return new ContentBlockDto("SECTION_HEADER", null, false, 0, pos, null, null, null, null, null, null, false, name, null, null);
    }

    private static ContentBlockDto position(String pos, String titel, String menge, String einheit, String artikelnummer) {
        return new ContentBlockDto("SERVICE", null, false, 10, pos, titel, "<strong>" + titel + "</strong>", new BigDecimal(menge), einheit,
                BigDecimal.TEN, BigDecimal.TEN, false, null, null, null, artikelnummer);
    }

    private byte[] erzeuge(VorlagenEinstellungen e, String typLabel, List<ContentBlockDto> inhalt, KopfdatenDto kopf) {
        List<FormBlockDto> bloecke = AutoAuftragsbestaetigungVersandService.parseVorlagenHtml(StandardVorlagenInhalt.layoutHtml(e)).formBlocks();
        RechnungPdfService.LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(bloecke, 595f, 842f);
        return pdf.generatePdfBytes(new RechnungDto(layout, kopf, inhalt, bloecke, null, null, null, null, null, null, null, e));
    }

    private static KopfdatenDto kopf(String typ, String nummer) {
        return new KopfdatenDto(nummer, LocalDate.of(2026, 10, 1), null, "Landratsamt Beispiel", "Landratsamt Beispiel\nHerr Mustermann\nMusterweg 6\n95634 Musterstadt",
                "Brandschutz", "KD-1", typ, null, "317", "Musterring 1, 95643 Musterstadt", null, null, null,
                "Max Mustermann", "+49 9638 111", "+49 170 222", "max@muster-metallbau.example");
    }

    private static List<ContentBlockDto> lieferscheinInhalt() {
        return List.of(
                text("<p>Sehr geehrte Damen und Herren,</p><p>wie vereinbart, liefern wir Ihnen nachstehende Waren:</p>"),
                gruppe("1.0", "Material"),
                position("1.001", "Türabsenkdichtung", "1", "Stk", "V0000001"),
                gruppe("2.0", "Leistung"),
                position("2.001", "Arbeitszeit im Service", "4", "Std", "1002"),
                text("<p>Ware ordnungsgemäß erhalten:</p><p>________________</p><p>Datum, Unterschrift</p>"));
    }

    @Test
    @DisplayName("Lieferschein: alle Bausteine des Beispiels stehen im PDF, ohne Preise und Summen")
    void lieferscheinZeigtAlleBausteine() throws Exception {
        byte[] bytes = erzeuge(VorlagenEinstellungen.lieferschein(), "Lieferschein", lieferscheinInhalt(), kopf("Lieferschein", "LS-109"));
        Files.createDirectories(Path.of("target"));
        Files.write(Path.of("target/lieferschein-vorschau.pdf"), bytes);
        String text = new PdfTextExtractor(new PdfReader(bytes)).getTextFromPage(1);

        assertTrue(text.contains("Muster Metallbau GmbH | Werkstraße 5 | 97070 Würzburg"), "Absenderzeile: " + text);
        assertTrue(text.contains("Landratsamt Beispiel"), text);
        assertTrue(text.contains("Dokumentennummer"), text);
        assertTrue(text.contains("LS-109"), text);
        assertTrue(text.contains("Projektnummer") && text.contains("317"), text);
        assertTrue(text.contains("01.10.2026"), "Datum mit führender Null: " + text);
        assertTrue(text.contains("Ansprechpartner") && text.contains("Max Mustermann"), text);
        assertTrue(text.contains("+49 170 222"), "Mobil: " + text);
        assertTrue(text.contains("BV: Musterring 1, 95643 Musterstadt"), text);
        assertTrue(text.contains("Arbeits- & Lieferschein Nr. LS-109"), text);
        assertTrue(text.contains("Artikel-Nr.") && text.contains("V0000001") && text.contains("1002"), text);
        assertTrue(text.contains("Pos. 1") && text.contains("Material") && text.contains("Pos. 2") && text.contains("Leistung"), text);
        assertTrue(text.contains("Ware ordnungsgemäß erhalten"), text);
        assertTrue(text.contains("Lieferschein LS-109"), "Fußzeile links: " + text);
        assertTrue(text.contains("Geschäftsführer: Max Mustermann"), "Fußzeile Mitte: " + text);
        assertTrue(text.contains("Mustersparkasse | IBAN: DE00 0000 0000 0000 0000 00 | BIC: MUSTDE00XXX"), "Bankzeile: " + text);
        assertTrue(text.contains("IBAN: DE00 0000 0000 0000 0000 00"), text);
        assertTrue(text.contains("Seite 1/1"), "Seitenzahl: " + text);
        assertFalse(text.contains("Einzelpreis") || text.contains("Gesamtpreis") || text.contains("€"), "keine Preise: " + text);
        assertFalse(text.contains("{{"), "kein Platzhalter bleibt stehen: " + text);
    }

    @Test
    @DisplayName("Rechnung mit Standardeinstellungen: Preisspalten, Summen und Infoblock ohne Artikelnummer")
    void rechnungZeigtPreise() throws Exception {
        byte[] bytes = erzeuge(VorlagenEinstellungen.standard(), "Rechnung", lieferscheinInhalt(), kopf("Rechnung", "RE-2026/10/00001"));
        String text = new PdfTextExtractor(new PdfReader(bytes)).getTextFromPage(1);

        assertTrue(text.contains("Rechnung Nr. RE-2026/10/00001"), text);
        assertTrue(text.contains("Einzelpreis") && text.contains("Gesamtpreis"), text);
        assertFalse(text.contains("Artikel-Nr."), text);
        assertTrue(text.contains("Nettobetrag") || text.contains("Netto"), "Summenblock bleibt: " + text);
    }

    @Test
    @DisplayName("Ohne Bearbeiter im Infoblock gelten die Angaben der Firma, eine leere Mobilnummer lässt die Zeile weg")
    void infoblockFaelltAufFirmeZurueck() throws Exception {
        KopfdatenDto ohne = new KopfdatenDto("LS-1", LocalDate.of(2026, 10, 1), null, "Kunde", "Kunde\nWeg 1\n12345 Ort", null, null, "Lieferschein", null, "5", "BV", null, null, null);
        byte[] bytes = erzeuge(VorlagenEinstellungen.lieferschein(), "Lieferschein", lieferscheinInhalt(), ohne);
        String text = new PdfTextExtractor(new PdfReader(bytes)).getTextFromPage(1);

        assertTrue(text.contains("Max Mustermann"), "Geschäftsführer als Ansprechpartner: " + text);
        assertTrue(text.contains("0931 123456"), text);
        assertFalse(text.contains("Mobil"), "ohne Mobilnummer keine Zeile: " + text);
    }

    @Test
    @DisplayName("Briefpapier aus: weder Hintergrundbild noch Briefpapier der Firma")
    void ohneBriefpapierBleibtDerHintergrundWeg() {
        VorlagenEinstellungen e = VorlagenEinstellungen.lieferschein();
        e.setBriefpapierAnzeigen(false);
        List<FormBlockDto> bloecke = AutoAuftragsbestaetigungVersandService.parseVorlagenHtml(StandardVorlagenInhalt.layoutHtml(e)).formBlocks();
        RechnungPdfService.LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(bloecke, 595f, 842f);
        String bildPunkt = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";

        byte[] mit = pdf.generatePdfBytes(new RechnungDto(layout, kopf("Lieferschein", "LS-1"), lieferscheinInhalt(), bloecke, null, bildPunkt, null, null, null, null, null, VorlagenEinstellungen.lieferschein()));
        byte[] ohne = pdf.generatePdfBytes(new RechnungDto(layout, kopf("Lieferschein", "LS-1"), lieferscheinInhalt(), bloecke, null, bildPunkt, null, null, null, null, null, e));

        assertTrue(mit.length > ohne.length, "mit Hintergrundbild ist das PDF größer: " + mit.length + " > " + ohne.length);
    }

    @Test
    @DisplayName("Die Einstellungen stehen als Meta-Tag in der Vorlage und lassen sich daraus wieder lesen")
    void einstellungenStehenInDerVorlage() {
        VorlagenEinstellungen e = VorlagenEinstellungen.lieferschein();
        e.setSchriftart("TIMES");
        e.setBetreff1Praefix("Objekt:");
        String html = StandardVorlagenInhalt.layoutHtml(e);

        VorlagenEinstellungen gelesen = VorlagenEinstellungen.ausHtml(html).orElseThrow();
        assertEquals("TIMES", gelesen.getSchriftart());
        assertEquals("Objekt:", gelesen.getBetreff1Praefix());
        assertTrue(gelesen.isEinzelpreiseAusblenden() && gelesen.isArtikelnummerAnzeigen());
        assertTrue(VorlagenEinstellungen.ausHtml("<meta name=\"page-format\" content=\"A4\" />").isEmpty(), "ältere Vorlage ohne Einstellungen");
        assertTrue(VorlagenEinstellungen.ausHtml("<meta name=\"vorlage-einstellungen\" content=\"%7Bkaputt\" />").isEmpty(), "unlesbar ergibt leer");
    }

    @Test
    @DisplayName("Falsche Werte werden auf sinnvolle Bereiche begrenzt")
    void werteWerdenBegrenzt() {
        VorlagenEinstellungen e = new VorlagenEinstellungen();
        e.setSchriftart("COMIC");
        e.setSchriftgroesse(400);
        e.setHauptblockOben(-5);
        e.setInfoblockBreite(9999);
        e.setFusszeileTyp("IRGENDWAS");
        e.setBetreff1Praefix(null);
        e.bereinigt();

        assertEquals("HELVETICA", e.getSchriftart());
        assertEquals(14, e.getSchriftgroesse());
        assertEquals(40, e.getHauptblockOben());
        assertTrue(e.getInfoblockBreite() <= 190);
        assertEquals("DOKUMENT_FIRMA_SEITE", e.getFusszeileTyp());
        assertEquals("", e.getBetreff1Praefix());
        // und das Layout bleibt auf dem Blatt
        List<FormBlockDto> bloecke = AutoAuftragsbestaetigungVersandService.parseVorlagenHtml(StandardVorlagenInhalt.layoutHtml(e)).formBlocks();
        assertTrue(bloecke.stream().allMatch(b -> b.x() >= 0 && b.x() + b.width() <= 595.5f && b.y() >= 0 && b.y() + b.height() <= 842.5f),
                "kein Block ragt über den A4-Rand");
    }

    @Test
    @DisplayName("Tabellenschrift (#286): 10 pt ist wie bisher, eine größere Einstellung vergrößert die ganze Positionstabelle im selben Verhältnis")
    void tabellenSchriftLaesstSichEinstellen() throws Exception {
        VorlagenEinstellungen standard = VorlagenEinstellungen.standard();
        assertEquals(10, standard.getTabellenSchriftgroesse(), "Standard wie bisher");
        VorlagenEinstellungen gross = VorlagenEinstellungen.standard();
        gross.setTabellenSchriftgroesse(12);

        String normal = schriftgroessen(erzeuge(standard, "Rechnung", lieferscheinInhalt(), kopf("Rechnung", "RE-1")));
        String groesser = schriftgroessen(erzeuge(gross, "Rechnung", lieferscheinInhalt(), kopf("Rechnung", "RE-1")));

        assertFalse(normal.contains(" 10.8 Tf"), "bei 10 pt keine vergrößerten Größen: " + normal);
        assertTrue(groesser.contains(" 10.8 Tf"), "Tabellenkopf 9 pt wird 10,8 pt: " + groesser);
        assertTrue(groesser.contains(" 10.8 Tf"), "Gruppenzeile 9 pt wird 10,8 pt: " + groesser);
        assertTrue(groesser.contains(" 12 Tf") || groesser.contains(" 12.0 Tf"), "Positionstext 10 pt wird 12 pt: " + groesser);
    }

    @Test
    @DisplayName("Tabellenschrift wird auf 7 bis 14 pt begrenzt, eine Vorlage ohne den Wert behält 10 pt")
    void tabellenSchriftWirdBegrenzt() {
        VorlagenEinstellungen e = new VorlagenEinstellungen();
        e.setTabellenSchriftgroesse(40);
        assertEquals(14, e.bereinigt().getTabellenSchriftgroesse());
        e.setTabellenSchriftgroesse(2);
        assertEquals(7, e.bereinigt().getTabellenSchriftgroesse());
        assertEquals(10, new VorlagenEinstellungen().getTabellenSchriftgroesse());
    }

    private static String schriftgroessen(byte[] pdf) throws Exception {
        return new String(new PdfReader(pdf).getPageContent(1), java.nio.charset.StandardCharsets.ISO_8859_1);
    }
}
