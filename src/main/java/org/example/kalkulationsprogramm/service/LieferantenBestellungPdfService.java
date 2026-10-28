package org.example.kalkulationsprogramm.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.ContentBlockDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.KopfdatenDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.LayoutDto;
import org.example.kalkulationsprogramm.service.RechnungPdfService.RectDto;
import org.springframework.stereotype.Service;

import org.example.kalkulationsprogramm.util.PdfWriterFabrik;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

import lombok.RequiredArgsConstructor;

/** Das Bestellformular als PDF (A4 hochkant). Wenn im Formulareditor eine Vorlage für
 * "Bestellformular" hinterlegt ist, wird diese genutzt (gleiche Variablen wie Angebot/Rechnung);
 * sonst fällt der Service auf das eingebaute Standardlayout zurück. */
@Service
@RequiredArgsConstructor
public class LieferantenBestellungPdfService {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Color AKZENT = new Color(225, 29, 72);
    private static final Color KOPF = new Color(51, 65, 85);
    private static final Color STREIFEN = new Color(241, 245, 249);

    private final LieferantenBestellungService bestellungen;
    private final FirmeninformationService firma;
    private final FormularTemplateService formularTemplateService;
    private final RechnungPdfService rechnungPdfService;

    public byte[] pdf(Long bestellungId) {
        BestellungDto b = bestellungen.lade(bestellungId);
        Optional<String> templateName = formularTemplateService
                .getPreferredTemplateForDokumenttyp("Bestellformular", null);
        if (templateName.isPresent()) {
            try {
                return erzeugePerTemplate(b, templateName.get());
            } catch (Exception e) {
                // Fehler im Template-Weg → Fallback auf Hardcode-Layout
            }
        }
        return erzeuge(b);
    }

    /** Dateiname ohne Sonderzeichen, zum Beispiel "Bestellung_B-2026-10-0001.pdf". */
    public static String dateiname(BestellungDto b) {
        String nr = b.bestellnummer() == null ? "Entwurf-" + b.id() : b.bestellnummer();
        return "Bestellung_" + nr.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }

    /** Erzeugt das PDF über RechnungPdfService mit dem hinterlegten Formulareditor-Template.
     *  Dieselben Variablen wie Angebot/Rechnung: {{DOKUMENTNUMMER}} = Bestellnummer,
     *  {{KUNDENNAME}} = Lieferantname, {{DATUM}} = Bestelldatum, {{DOKUMENTTYP}} = "Bestellung". */
    private byte[] erzeugePerTemplate(BestellungDto b, String templateName) {
        FormularTemplateService.NamedTemplateData data = formularTemplateService.loadNamedTemplate(templateName);
        AutoAuftragsbestaetigungVersandService.VorlagenDaten vorlage =
                AutoAuftragsbestaetigungVersandService.parseVorlagenHtml(data.html());

        String bestellnummer = b.bestellnummer() != null ? b.bestellnummer() : "Entwurf";
        java.time.LocalDate bestellDatum = b.bestelltAm() != null
                ? b.bestelltAm().toLocalDate()
                : b.erstelltAm().toLocalDate();
        String erstesProjekt = b.positionen().stream()
                .map(BestellungDto.Position::projektName)
                .filter(n -> n != null && !n.isBlank())
                .findFirst().orElse("");

        KopfdatenDto kopfdaten = new KopfdatenDto(
                bestellnummer,
                bestellDatum,
                null,               // kein Leistungsdatum
                b.lieferantName(),  // kundenName → Lieferantname
                b.lieferantName(),  // kundenAdresse → Lieferantname (keine separaten Adressdaten verfügbar)
                "Bestellung",       // betreff
                null,               // kundennummer
                "Bestellung",       // dokumentTyp
                null,               // bezugsdokument
                erstesProjekt,      // projektnummer
                erstesProjekt       // bauvorhaben
        );

        List<ContentBlockDto> blocks = new ArrayList<>();
        int nr = 1;
        for (BestellungDto.Position p : b.positionen()) {
            blocks.add(new ContentBlockDto(
                    "SERVICE", null, false, 10,
                    String.valueOf(nr++),
                    p.bezeichnung(),
                    null,
                    p.menge(),
                    p.einheit() != null ? p.einheit() : "",
                    p.einzelpreis(),
                    p.summe(),
                    false, null, null, null));
        }
        if (b.notiz() != null && !b.notiz().isBlank()) {
            blocks.add(new ContentBlockDto(
                    "TEXT", b.notiz(), false, 10,
                    null, null, null, null, null, null, null, false, null, null, null));
        }

        LayoutDto layout = RechnungPdfService.createLayoutFromFormBlocks(vorlage.formBlocks(), 595f, 842f);

        RechnungPdfService.RechnungDto dto = new RechnungPdfService.RechnungDto(
                layout,
                kopfdaten,
                blocks,
                vorlage.formBlocks(),
                null,
                vorlage.backgroundImagePage1(),
                vorlage.backgroundImagePage2(),
                null
        );

        return rechnungPdfService.generatePdfBytes(dto);
    }

    byte[] erzeuge(BestellungDto b) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 40, 40, 40, 50);
            PdfWriterFabrik.writer(doc, out);
            doc.open();
            Font gross = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, AKZENT);
            Font fett = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font klein = FontFactory.getFont(FontFactory.HELVETICA, 8, new Color(100, 116, 139));

            Image logo = firma.loadLogoImage();
            if (logo != null) {
                logo.scaleToFit(150, 60);
                doc.add(logo);
            }
            var f = firma.getFirmeninformation();
            if (f != null && f.getFirmenname() != null && !f.getFirmenname().isBlank()) {
                doc.add(new Paragraph(f.getFirmenname(), fett));
                String adresse = (nz(f.getStrasse()) + ", " + nz(f.getPlz()) + " " + nz(f.getOrt())).replaceAll("^[ ,]+|[ ,]+$", "");
                if (!adresse.isBlank()) {
                    doc.add(new Paragraph(adresse, klein));
                }
            }
            Paragraph titel = new Paragraph("Bestellung " + (b.bestellnummer() == null ? "(Entwurf)" : b.bestellnummer()), gross);
            titel.setSpacingBefore(18f);
            doc.add(titel);

            Paragraph an = new Paragraph("An: " + b.lieferantName(), fett);
            an.setSpacingBefore(6f);
            doc.add(an);
            String datum = b.bestelltAm() != null ? b.bestelltAm().format(DATUM) : b.erstelltAm().format(DATUM);
            doc.add(new Paragraph("Datum: " + datum + (b.status() == BestellStatus.ENTWURF ? "   (noch nicht bestellt)" : ""), normal));
            Paragraph bitte = new Paragraph("Bitte nennen Sie bei Auftragsbestätigung, Lieferschein und Rechnung die Bestellnummer und bestätigen Sie uns den Liefertermin.", normal);
            bitte.setSpacingBefore(10f);
            bitte.setSpacingAfter(12f);
            doc.add(bitte);

            boolean mitPreis = b.positionen().stream().anyMatch(p -> p.einzelpreis() != null);
            PdfPTable tabelle = mitPreis ? new PdfPTable(new float[] { 0.6f, 2f, 4.4f, 1.6f, 1.2f, 2.4f, 1.8f }) : new PdfPTable(new float[] { 0.6f, 2f, 5f, 1.6f, 1.2f, 3f });
            tabelle.setWidthPercentage(100);
            tabelle.setHeaderRows(1);
            Font kopf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
            String[] ueber = mitPreis ? new String[] { "Pos.", "Art.-Nr.", "Bezeichnung", "Menge", "Einheit", "Projekt / Hinweis", "Preis" }
                    : new String[] { "Pos.", "Art.-Nr.", "Bezeichnung", "Menge", "Einheit", "Projekt / Hinweis" };
            for (String h : ueber) {
                tabelle.addCell(zelle(h, kopf, KOPF, Element.ALIGN_LEFT));
            }
            Font zf = FontFactory.getFont(FontFactory.HELVETICA, 9);
            NumberFormat mengeFormat = NumberFormat.getNumberInstance(Locale.GERMANY);
            mengeFormat.setMaximumFractionDigits(3);
            NumberFormat geld = NumberFormat.getCurrencyInstance(Locale.GERMANY);
            int nr = 1;
            for (BestellungDto.Position p : b.positionen()) {
                Color bg = nr % 2 == 0 ? STREIFEN : Color.WHITE;
                tabelle.addCell(zelle(String.valueOf(nr++), zf, bg, Element.ALIGN_LEFT));
                tabelle.addCell(zelle(nz(p.artikelnummer()), zf, bg, Element.ALIGN_LEFT));
                tabelle.addCell(zelle(p.bezeichnung(), zf, bg, Element.ALIGN_LEFT));
                tabelle.addCell(zelle(mengeFormat.format(p.menge()), zf, bg, Element.ALIGN_RIGHT));
                tabelle.addCell(zelle(p.einheit(), zf, bg, Element.ALIGN_LEFT));
                String hinweis = (p.projektName() != null ? p.projektName() : "") + (p.kommentar() != null ? (p.projektName() != null ? " – " : "") + p.kommentar() : "");
                tabelle.addCell(zelle(hinweis, zf, bg, Element.ALIGN_LEFT));
                if (mitPreis) {
                    BigDecimal s = p.summe();
                    tabelle.addCell(zelle(s == null ? "" : geld.format(s), zf, bg, Element.ALIGN_RIGHT));
                }
            }
            doc.add(tabelle);
            if (mitPreis) {
                Paragraph summe = new Paragraph("Summe netto (laut Preisliste): " + geld.format(b.summe()), fett);
                summe.setAlignment(Element.ALIGN_RIGHT);
                summe.setSpacingBefore(8f);
                doc.add(summe);
            }
            if (b.notiz() != null && !b.notiz().isBlank()) {
                Paragraph n = new Paragraph("Hinweis: " + b.notiz(), normal);
                n.setSpacingBefore(12f);
                doc.add(n);
            }
            doc.close();
            return out.toByteArray();
        } catch (DocumentException | java.io.IOException e) {
            throw new IllegalStateException("Das Bestell-PDF konnte nicht erstellt werden.", e);
        }
    }

    private static PdfPCell zelle(String text, Font font, Color hintergrund, int ausrichtung) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, font));
        c.setBackgroundColor(hintergrund);
        c.setPadding(4f);
        c.setHorizontalAlignment(ausrichtung);
        c.setBorderColor(new Color(226, 232, 240));
        return c;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
