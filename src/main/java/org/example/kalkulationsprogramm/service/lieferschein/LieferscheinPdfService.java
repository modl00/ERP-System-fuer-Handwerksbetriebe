package org.example.kalkulationsprogramm.service.lieferschein;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.lieferschein.Lieferschein;
import org.example.kalkulationsprogramm.domain.lieferschein.LieferscheinPosition;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.example.kalkulationsprogramm.service.wartung.WartungUnterschriftPruefer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Erzeugt den Lieferschein als PDF (A4): Firmenkopf, Empfänger, Nummer und Datum, Positionen mit Menge und Einheit, Bemerkung und die Unterschrift des
 * Empfängers. Ohne Unterschrift steht eine leere Linie (zum Ausdrucken und von Hand Unterschreiben) und der Hinweis „Entwurf“.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LieferscheinPdfService {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);
    private static final Charset ANSI = Charset.forName("windows-1252");
    private static final Color GRUNDFARBE = new Color(0x1E, 0x29, 0x3B);
    private static final Color GRAU = new Color(0x64, 0x74, 0x8B);
    private static final Color LINIE = new Color(0xCB, 0xD5, 0xE1);
    private static final Color KOPF_HINTERGRUND = new Color(0xF1, 0xF5, 0xF9);
    private static final Color ROT = new Color(0xBE, 0x12, 0x3C);

    private final FirmeninformationService firmeninformationService;

    public record PdfDatei(String dateiname, byte[] inhalt) { }

    @Transactional(readOnly = true)
    public PdfDatei erstelle(Lieferschein l) {
        FirmeninformationDto firma = ladeFirma();
        Image logo = firmeninformationService.loadLogoImage();
        ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
        Document dokument = new Document(PageSize.A4, 40, 40, 40, 60);
        try {
            PdfWriterFabrik.writer(dokument, ausgabe);
            dokument.open();
            dokument.addTitle("Lieferschein " + l.getNummer());
            dokument.addCreator("Handwerkerprogramm");
            kopf(dokument, firma, logo);
            titel(dokument, l);
            empfaenger(dokument, l);
            positionen(dokument, l);
            if (l.getBemerkung() != null) {
                dokument.add(new Paragraph(pdfText("Bemerkung"), schrift(9, Font.BOLD, GRUNDFARBE)));
                dokument.add(new Paragraph(pdfText(l.getBemerkung()), schrift(9, Font.NORMAL, GRUNDFARBE)));
            }
            unterschrift(dokument, l);
            dokument.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Das PDF konnte nicht erstellt werden.", e);
        }
        return new PdfDatei("Lieferschein_" + l.getNummer().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf", ausgabe.toByteArray());
    }

    private void kopf(Document dokument, FirmeninformationDto firma, Image logo) throws DocumentException {
        PdfPTable tabelle = new PdfPTable(new float[] { 1.2f, 1f });
        tabelle.setWidthPercentage(100);
        PdfPCell links = leereZelle();
        if (logo != null) {
            logo.scaleToFit(150, 48);
            links.addElement(logo);
        } else if (firma != null && notBlank(firma.getFirmenname())) {
            links.addElement(new Paragraph(pdfText(firma.getFirmenname()), schrift(15, Font.BOLD, GRUNDFARBE)));
        }
        tabelle.addCell(links);
        PdfPCell rechts = leereZelle();
        if (firma != null) {
            Font klein = schrift(8, Font.NORMAL, GRAU);
            zeile(rechts, firma.getFirmenname(), schrift(9, Font.BOLD, GRUNDFARBE));
            zeile(rechts, firma.getStrasse(), klein);
            zeile(rechts, ((firma.getPlz() == null ? "" : firma.getPlz()) + " " + (firma.getOrt() == null ? "" : firma.getOrt())).trim(), klein);
            zeile(rechts, notBlank(firma.getTelefon()) ? "Telefon " + firma.getTelefon() : null, klein);
            zeile(rechts, firma.getEmail(), klein);
        }
        tabelle.addCell(rechts);
        tabelle.setSpacingAfter(16);
        dokument.add(tabelle);
    }

    private void titel(Document dokument, Lieferschein l) throws DocumentException {
        Paragraph ueberschrift = new Paragraph("Lieferschein " + pdfText(l.getNummer()), schrift(16, Font.BOLD, GRUNDFARBE));
        dokument.add(ueberschrift);
        dokument.add(new Paragraph("Datum " + DATUM.format(l.getDatum()), schrift(9, Font.NORMAL, GRAU)));
        if (!l.istUnterschrieben()) {
            Paragraph entwurf = new Paragraph("Entwurf: noch nicht vom Empfänger unterschrieben", schrift(9, Font.BOLD, ROT));
            entwurf.setSpacingBefore(4);
            dokument.add(entwurf);
        }
    }

    private void empfaenger(Document dokument, Lieferschein l) throws DocumentException {
        PdfPTable tabelle = new PdfPTable(1);
        tabelle.setWidthPercentage(100);
        PdfPCell zelle = new PdfPCell();
        zelle.setBorderColor(LINIE);
        zelle.setBackgroundColor(KOPF_HINTERGRUND);
        zelle.setPadding(8);
        Kunde k = l.getKunde();
        zeile(zelle, "Lieferung an", schrift(8, Font.NORMAL, GRAU));
        if (k != null) {
            zeile(zelle, k.getName(), schrift(10, Font.BOLD, GRUNDFARBE));
            zeile(zelle, k.getStrasse(), schrift(9, Font.NORMAL, GRUNDFARBE));
            zeile(zelle, ((k.getPlz() == null ? "" : k.getPlz()) + " " + (k.getOrt() == null ? "" : k.getOrt())).trim(), schrift(9, Font.NORMAL, GRUNDFARBE));
        }
        if (l.getProjekt() != null && notBlank(l.getProjekt().getBauvorhaben())) {
            zeile(zelle, "Bauvorhaben: " + l.getProjekt().getBauvorhaben(), schrift(9, Font.NORMAL, GRUNDFARBE));
        }
        zeile(zelle, "Entgegengenommen von: " + l.getEmpfaengerName(), schrift(9, Font.NORMAL, GRUNDFARBE));
        tabelle.addCell(zelle);
        tabelle.setSpacingBefore(12);
        tabelle.setSpacingAfter(12);
        dokument.add(tabelle);
    }

    private void positionen(Document dokument, Lieferschein l) throws DocumentException {
        PdfPTable tabelle = new PdfPTable(new float[] { 0.5f, 5f, 1.2f, 1f });
        tabelle.setWidthPercentage(100);
        tabelle.setHeaderRows(1);
        for (String kopf : new String[] { "Pos.", "Bezeichnung", "Menge", "Einheit" }) {
            PdfPCell z = new PdfPCell(new Phrase(kopf, schrift(8, Font.BOLD, GRUNDFARBE)));
            z.setBackgroundColor(KOPF_HINTERGRUND);
            z.setBorderColor(LINIE);
            z.setPadding(5);
            z.setHorizontalAlignment("Menge".equals(kopf) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            tabelle.addCell(z);
        }
        int nr = 1;
        for (LieferscheinPosition p : l.getPositionen()) {
            tabelle.addCell(zelle(String.valueOf(nr++), Element.ALIGN_LEFT));
            tabelle.addCell(zelle(p.getBezeichnung(), Element.ALIGN_LEFT));
            tabelle.addCell(zelle(menge(p.getMenge()), Element.ALIGN_RIGHT));
            tabelle.addCell(zelle(p.getEinheit() == null ? "" : p.getEinheit(), Element.ALIGN_LEFT));
        }
        tabelle.setSpacingAfter(12);
        dokument.add(tabelle);
    }

    private void unterschrift(Document dokument, Lieferschein l) throws DocumentException {
        PdfPTable tabelle = new PdfPTable(new float[] { 1f, 1f });
        tabelle.setWidthPercentage(100);
        tabelle.setKeepTogether(true);
        PdfPCell zelle = leereZelle();
        Image bild = lesePng(l.getUnterschriftBild());
        PdfPCell linie;
        if (bild != null) {
            bild.scaleToFit(170, 55);
            linie = new PdfPCell(bild, false);
            linie.setVerticalAlignment(Element.ALIGN_BOTTOM);
            linie.setPaddingBottom(2);
        } else {
            linie = new PdfPCell(new Phrase(" "));
        }
        linie.setBorder(Rectangle.BOTTOM);
        linie.setBorderColor(GRUNDFARBE);
        linie.setFixedHeight(60);
        PdfPTable innen = new PdfPTable(1);
        innen.addCell(linie);
        zelle.addElement(innen);
        String name = l.getUnterschriftName() != null ? l.getUnterschriftName() : l.getEmpfaengerName();
        zelle.addElement(new Paragraph(pdfText("Empfänger: " + name), schrift(9, Font.NORMAL, GRUNDFARBE)));
        if (l.getUnterschriebenAm() != null) {
            zelle.addElement(new Paragraph(DATUM.format(l.getUnterschriebenAm()), schrift(8, Font.NORMAL, GRAU)));
        }
        zelle.addElement(new Paragraph(pdfText("Die Ware wurde vollständig und ohne sichtbare Mängel übernommen."), schrift(7, Font.NORMAL, GRAU)));
        tabelle.addCell(zelle);
        tabelle.addCell(leereZelle());
        tabelle.setSpacingBefore(18);
        dokument.add(tabelle);
    }

    private static String menge(BigDecimal menge) {
        DecimalFormat format = new DecimalFormat("#,##0.###", DecimalFormatSymbols.getInstance(Locale.GERMANY));
        return format.format(menge);
    }

    private Image lesePng(String bild) {
        if (bild == null || !bild.startsWith(WartungUnterschriftPruefer.PRAEFIX)) {
            return null;
        }
        try {
            return Image.getInstance(Base64.getDecoder().decode(bild.substring(WartungUnterschriftPruefer.PRAEFIX.length())));
        } catch (Exception e) {
            log.warn("Unterschrift konnte nicht als Bild gelesen werden", e);
            return null;
        }
    }

    private FirmeninformationDto ladeFirma() {
        try {
            return firmeninformationService.getFirmeninformation();
        } catch (RuntimeException e) {
            log.warn("Firmendaten konnten für den Lieferschein nicht geladen werden", e);
            return null;
        }
    }

    private static Font schrift(float groesse, int stil, Color farbe) {
        return FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED, groesse, stil, farbe);
    }

    private static PdfPCell leereZelle() {
        PdfPCell zelle = new PdfPCell();
        zelle.setBorder(Rectangle.NO_BORDER);
        zelle.setPadding(0);
        return zelle;
    }

    private static PdfPCell zelle(String text, int ausrichtung) {
        PdfPCell z = new PdfPCell(new Phrase(pdfText(text), schrift(9, Font.NORMAL, GRUNDFARBE)));
        z.setBorderColor(LINIE);
        z.setPadding(5);
        z.setHorizontalAlignment(ausrichtung);
        return z;
    }

    private static void zeile(PdfPCell zelle, String text, Font schrift) {
        if (notBlank(text)) {
            zelle.addElement(new Paragraph(pdfText(text), schrift));
        }
    }

    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }

    /** Ersetzt Zeichen, die die eingebauten PDF-Schriften nicht darstellen können, damit keine Lücken entstehen. */
    static String pdfText(String text) {
        if (text == null) {
            return "";
        }
        CharsetEncoder encoder = ANSI.newEncoder();
        StringBuilder ergebnis = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            String zeichen = new String(Character.toChars(cp));
            if (cp == '\n' || cp == '\r' || cp == '\t' || encoder.canEncode(zeichen)) {
                ergebnis.append(zeichen);
            } else {
                ergebnis.append('?');
            }
        });
        return ergebnis.toString();
    }
}
