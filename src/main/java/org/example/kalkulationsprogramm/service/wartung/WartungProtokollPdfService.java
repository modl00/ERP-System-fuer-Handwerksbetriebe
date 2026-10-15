package org.example.kalkulationsprogramm.service.wartung;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.mapper.wartung.WartungMapper;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollAbschnittRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Erzeugt das Wartungsprotokoll als PDF (A4): Kopf mit Firma, Angaben zur Wartung, Datenaufnahme,
 * Checkliste je Abschnitt, Mängel, Bemerkungen und Unterschriften.
 *
 * <p>Ein noch nicht abgeschlossenes Protokoll wird deutlich als Entwurf gekennzeichnet. Fehlende
 * Unterschriften erscheinen als leere Linie, damit sich ein ausgedrucktes Protokoll von Hand
 * unterschreiben lässt. Verwendet werden die eingebauten Schriften (wie bei den Rechnungen); Zeichen,
 * die sie nicht darstellen können, werden durch {@link #pdfText(String)} ersetzt.</p>
 */
@Slf4j
@Service
@AllArgsConstructor
public class WartungProtokollPdfService {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);
    private static final Charset ANSI = Charset.forName("windows-1252");
    static final Color GRUNDFARBE = new Color(0x1E, 0x29, 0x3B);
    static final Color GRAU = new Color(0x64, 0x74, 0x8B);
    static final Color LINIE = new Color(0xCB, 0xD5, 0xE1);
    static final Color KOPF_HINTERGRUND = new Color(0xF1, 0xF5, 0xF9);
    static final Color GRUEN = new Color(0x04, 0x78, 0x57);
    static final Color ROT = new Color(0xBE, 0x12, 0x3C);
    static final Color AMBER = new Color(0xB4, 0x53, 0x09);

    private final WartungProtokollRepository protokollRepository;
    private final WartungProtokollAbschnittRepository abschnittRepository;
    private final WartungProtokollPruefpunktRepository pruefpunktRepository;
    private final WartungProtokollDatenfeldRepository datenfeldRepository;
    private final org.example.kalkulationsprogramm.service.FirmeninformationService firmeninformationService;

    /** Das fertige PDF samt Dateiname. */
    public record PdfDatei(String dateiname, byte[] inhalt) { }

    @Transactional(readOnly = true)
    public PdfDatei erstelle(Long protokollId) {
        WartungProtokoll protokoll = protokollRepository.findById(protokollId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Protokoll wurde nicht gefunden."));
        List<WartungProtokollAbschnitt> abschnitte = abschnittRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId);
        List<WartungProtokollPruefpunkt> pruefpunkte = pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId);
        List<WartungProtokollDatenfeld> datenfelder = datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokollId);
        FirmeninformationDto firma = ladeFirma();
        Image logo = firmeninformationService.loadLogoImage();

        ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
        Document dokument = new Document(PageSize.A4, 40, 40, 40, 60);
        try {
            PdfWriter writer = PdfWriter.getInstance(dokument, ausgabe);
            writer.setPageEvent(new Fusszeile(protokoll.getNummer(), firma == null ? null : firma.getFusszeileText()));
            dokument.open();
            dokument.addTitle("Wartungsprotokoll " + protokoll.getNummer());
            dokument.addCreator("Handwerkerprogramm");

            kopf(dokument, firma, logo);
            titel(dokument, protokoll);
            // Entwurf, solange die Prüfung nicht abgeschlossen ist (auch nach dem Wiederöffnen).
            if (protokoll.getAbgeschlossenAm() == null) {
                entwurfHinweis(dokument);
            }
            angaben(dokument, protokoll);
            datenaufnahme(dokument, datenfelder, protokoll);
            checkliste(dokument, abschnitte, pruefpunkte);
            maengel(dokument, pruefpunkte);
            bemerkungen(dokument, protokoll);
            unterschriften(dokument, protokoll);
            dokument.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Das PDF konnte nicht erstellt werden.", e);
        }
        return new PdfDatei(dateiname(protokoll), ausgabe.toByteArray());
    }

    // ── Aufbau ──────────────────────────────────────────────────────────────

    private void kopf(Document dokument, FirmeninformationDto firma, Image logo) throws DocumentException {
        PdfPTable tabelle = new PdfPTable(new float[] {1.2f, 1f});
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
        rechts.setHorizontalAlignment(Element.ALIGN_RIGHT);
        if (firma != null) {
            Font klein = schrift(8, Font.NORMAL, GRAU);
            absatz(rechts, firma.getFirmenname(), schrift(9, Font.BOLD, GRUNDFARBE), Element.ALIGN_RIGHT);
            absatz(rechts, firma.getStrasse(), klein, Element.ALIGN_RIGHT);
            absatz(rechts, (nvl(firma.getPlz()) + " " + nvl(firma.getOrt())).trim(), klein, Element.ALIGN_RIGHT);
            absatz(rechts, notBlank(firma.getTelefon()) ? "Telefon " + firma.getTelefon() : null, klein, Element.ALIGN_RIGHT);
            absatz(rechts, firma.getEmail(), klein, Element.ALIGN_RIGHT);
        }
        tabelle.addCell(rechts);
        tabelle.setSpacingAfter(14);
        dokument.add(tabelle);
    }

    private void titel(Document dokument, WartungProtokoll protokoll) throws DocumentException {
        PdfPTable tabelle = new PdfPTable(new float[] {2.4f, 1f});
        tabelle.setWidthPercentage(100);

        PdfPCell links = leereZelle();
        links.addElement(new Paragraph(pdfText(protokoll.getProtokollTyp()), schrift(20, Font.BOLD, GRUNDFARBE)));
        if (notBlank(protokoll.getTitel())) {
            links.addElement(new Paragraph(pdfText(protokoll.getTitel()), schrift(11, Font.NORMAL, GRAU)));
        }
        tabelle.addCell(links);

        PdfPCell rechts = leereZelle();
        rechts.setHorizontalAlignment(Element.ALIGN_RIGHT);
        absatz(rechts, protokoll.getNummer(), schrift(13, Font.BOLD, GRUNDFARBE), Element.ALIGN_RIGHT);
        absatz(rechts, "vom " + DATUM.format(protokoll.getDatum()), schrift(10, Font.NORMAL, GRAU), Element.ALIGN_RIGHT);
        tabelle.addCell(rechts);
        tabelle.setSpacingAfter(10);
        dokument.add(tabelle);
    }

    private void entwurfHinweis(Document dokument) throws DocumentException {
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        PdfPCell zelle = new PdfPCell(new Phrase("ENTWURF – Die Prüfung ist noch nicht abgeschlossen.", schrift(10, Font.BOLD, AMBER)));
        zelle.setBorderColor(AMBER);
        zelle.setBorderWidth(1f);
        zelle.setPadding(6);
        zelle.setHorizontalAlignment(Element.ALIGN_CENTER);
        box.addCell(zelle);
        box.setSpacingAfter(10);
        dokument.add(box);
    }

    private void angaben(Document dokument, WartungProtokoll protokoll) throws DocumentException {
        PdfPTable tabelle = new PdfPTable(new float[] {1.1f, 2.4f, 1.1f, 2.4f});
        tabelle.setWidthPercentage(100);
        String module = protokoll.getModule().stream()
                .sorted(Comparator.comparing(WartungProtokollModul::getModulId))
                .map(m -> m.getUntertypLabel() == null ? m.getModulName() : m.getModulName() + " (" + m.getUntertypLabel() + ")")
                .collect(Collectors.joining(", "));
        Mitarbeiter techniker = protokoll.getTechniker();

        angabenZeile(tabelle, "Kunde", protokoll.getKunde().getName(), "Datum der Prüfung", DATUM.format(protokoll.getDatum()));
        angabenZeile(tabelle, "Objekt", protokoll.getObjekt() == null ? null : protokoll.getObjekt().getBezeichnung(),
                "Nächste Prüfung", protokoll.getNaechsteWartung() == null ? null : DATUM.format(protokoll.getNaechsteWartung()));
        angabenZeile(tabelle, "Tür / Tor", protokoll.getTuer() == null ? null : protokoll.getTuer().getBezeichnung(),
                "Techniker", techniker == null ? null : (nvl(techniker.getVorname()) + " " + nvl(techniker.getNachname())).trim());
        angabenZeile(tabelle, "Geprüft wurde", module, "Ansprechpartner", protokoll.getAnsprechpartnerName());
        angabenZeile(tabelle, "Projekt", protokoll.getProjekt() == null ? null : WartungMapper.projektName(protokoll.getProjekt()),
                "Wartungsnummer", protokoll.getNummer());
        tabelle.setSpacingAfter(12);
        dokument.add(tabelle);
    }

    private void datenaufnahme(Document dokument, List<WartungProtokollDatenfeld> felder, WartungProtokoll protokoll) throws DocumentException {
        if (felder.isEmpty()) {
            return;
        }
        abschnittsUeberschrift(dokument, "Datenaufnahme");
        PdfPTable tabelle = new PdfPTable(new float[] {1.4f, 2f});
        tabelle.setWidthPercentage(100);
        tabelle.setHeaderRows(0);
        Map<String, String> modulNamen = protokoll.getModule().stream()
                .collect(Collectors.toMap(WartungProtokollModul::getModulKey, WartungProtokollModul::getModulName, (a, b) -> a, LinkedHashMap::new));
        String letzteGruppe = null;
        // "keine Angabe" bedeutet: unbekannt oder nicht vorhanden. Das steht nicht im Protokoll, die Zeile entfällt.
        for (WartungProtokollDatenfeld feld : felder.stream().filter(f -> !WartungFeldtyp.istKeineAngabe(f.getWert()))
                .sorted(Comparator.comparingInt(WartungProtokollDatenfeld::getSortierung)).toList()) {
            String gruppe = feld.getModulKey() == null ? "Allgemein" : modulNamen.getOrDefault(feld.getModulKey(), "Weitere Angaben");
            if (!gruppe.equals(letzteGruppe)) {
                PdfPCell kopf = new PdfPCell(new Phrase(pdfText(gruppe), schrift(9, Font.BOLD, GRUNDFARBE)));
                kopf.setColspan(2);
                kopf.setBackgroundColor(KOPF_HINTERGRUND);
                kopf.setBorderColor(LINIE);
                kopf.setPadding(4);
                tabelle.addCell(kopf);
                letzteGruppe = gruppe;
            }
            tabelle.addCell(zelle(feld.getLabel(), schrift(9, Font.NORMAL, GRAU), Element.ALIGN_LEFT, LINIE));
            tabelle.addCell(zelle(notBlank(feld.getWert()) ? feld.getWert() : "–", schrift(9, Font.NORMAL, GRUNDFARBE), Element.ALIGN_LEFT, LINIE));
        }
        tabelle.setSpacingAfter(12);
        dokument.add(tabelle);
    }

    private void checkliste(Document dokument, List<WartungProtokollAbschnitt> abschnitte, List<WartungProtokollPruefpunkt> punkte) throws DocumentException {
        if (punkte.isEmpty()) {
            return;
        }
        abschnittsUeberschrift(dokument, "Prüfung");
        Map<Long, WartungProtokollAbschnitt> nachId = abschnitte.stream()
                .filter(a -> a.getBereich() == WartungBereich.PRUEFPUNKTE)
                .collect(Collectors.toMap(WartungProtokollAbschnitt::getId, a -> a));
        int nummer = 0;

        // Abschnitte in ihrer Reihenfolge, Punkte ohne Abschnitt zuletzt.
        for (WartungProtokollAbschnitt abschnitt : nachId.values().stream().sorted(Comparator.comparingInt(WartungProtokollAbschnitt::getSortierung)).toList()) {
            List<WartungProtokollPruefpunkt> inAbschnitt = punkte.stream()
                    .filter(p -> Objects.equals(p.getAbschnittId(), abschnitt.getId()))
                    .sorted(Comparator.comparingInt(WartungProtokollPruefpunkt::getSortierung)).toList();
            if (!inAbschnitt.isEmpty()) {
                nummer = pruefTabelle(dokument, abschnitt.getLabel(), inAbschnitt, nummer);
            }
        }
        List<WartungProtokollPruefpunkt> ohne = punkte.stream()
                .filter(p -> p.getAbschnittId() == null || !nachId.containsKey(p.getAbschnittId()))
                .sorted(Comparator.comparingInt(WartungProtokollPruefpunkt::getSortierung)).toList();
        if (!ohne.isEmpty()) {
            pruefTabelle(dokument, "Weitere Prüfpunkte", ohne, nummer);
        }
    }

    private int pruefTabelle(Document dokument, String titel, List<WartungProtokollPruefpunkt> punkte, int startNummer) throws DocumentException {
        PdfPTable tabelle = new PdfPTable(new float[] {0.45f, 3.3f, 0.9f, 2.2f});
        tabelle.setWidthPercentage(100);
        tabelle.setHeaderRows(2);
        tabelle.setSplitLate(false);

        PdfPCell ueberschrift = new PdfPCell(new Phrase(pdfText(titel), schrift(10, Font.BOLD, GRUNDFARBE)));
        ueberschrift.setColspan(4);
        ueberschrift.setBorder(Rectangle.NO_BORDER);
        ueberschrift.setPaddingTop(6);
        ueberschrift.setPaddingBottom(4);
        tabelle.addCell(ueberschrift);
        for (String kopf : new String[] {"Nr.", "Prüfpunkt", "Ergebnis", "Bemerkung"}) {
            PdfPCell k = new PdfPCell(new Phrase(kopf, schrift(8, Font.BOLD, GRAU)));
            k.setBackgroundColor(KOPF_HINTERGRUND);
            k.setBorderColor(LINIE);
            k.setPadding(4);
            tabelle.addCell(k);
        }

        int nummer = startNummer;
        for (WartungProtokollPruefpunkt p : punkte) {
            nummer++;
            tabelle.addCell(zelle(String.valueOf(nummer), schrift(9, Font.NORMAL, GRAU), Element.ALIGN_CENTER, LINIE));
            tabelle.addCell(zelle(p.getLabel(), schrift(9, Font.NORMAL, GRUNDFARBE), Element.ALIGN_LEFT, LINIE));
            tabelle.addCell(zelle(ergebnisText(p.getErgebnis()), schrift(9, Font.BOLD, ergebnisFarbe(p.getErgebnis())), Element.ALIGN_CENTER, LINIE));
            tabelle.addCell(zelle(p.getBemerkung() == null ? "" : p.getBemerkung(), schrift(8, Font.NORMAL, GRAU), Element.ALIGN_LEFT, LINIE));
        }
        tabelle.setSpacingAfter(8);
        dokument.add(tabelle);
        return nummer;
    }

    private void maengel(Document dokument, List<WartungProtokollPruefpunkt> punkte) throws DocumentException {
        List<WartungProtokollPruefpunkt> liste = punkte.stream().filter(p -> p.getErgebnis() == WartungErgebnis.NIO).toList();
        long bewertet = punkte.stream().filter(p -> p.getErgebnis() != null).count();
        long inOrdnung = punkte.stream().filter(p -> p.getErgebnis() == WartungErgebnis.IO).count();

        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setKeepTogether(true);
        PdfPCell zelle = new PdfPCell();
        zelle.setPadding(8);
        zelle.setBorderWidth(1f);
        zelle.setBorderColor(liste.isEmpty() ? GRUEN : ROT);
        if (liste.isEmpty()) {
            zelle.addElement(new Paragraph(pdfText("Keine Mängel festgestellt."), schrift(10, Font.BOLD, GRUEN)));
        } else {
            zelle.addElement(new Paragraph(pdfText(liste.size() == 1 ? "Festgestellter Mangel" : "Festgestellte Mängel (" + liste.size() + ")"), schrift(10, Font.BOLD, ROT)));
            for (WartungProtokollPruefpunkt p : liste) {
                Paragraph absatz = new Paragraph();
                absatz.setSpacingBefore(3);
                absatz.add(new Phrase(pdfText("• " + p.getLabel()), schrift(9, Font.BOLD, GRUNDFARBE)));
                if (notBlank(p.getBemerkung())) {
                    absatz.add(new Phrase(pdfText(": " + p.getBemerkung()), schrift(9, Font.NORMAL, GRUNDFARBE)));
                }
                zelle.addElement(absatz);
            }
        }
        Paragraph zusammenfassung = new Paragraph(pdfText(bewertet + " von " + punkte.size() + " Prüfpunkten bewertet, davon " + inOrdnung + " in Ordnung."),
                schrift(8, Font.NORMAL, GRAU));
        zusammenfassung.setSpacingBefore(5);
        zelle.addElement(zusammenfassung);
        box.addCell(zelle);
        box.setSpacingBefore(4);
        box.setSpacingAfter(10);
        dokument.add(box);
    }

    private void bemerkungen(Document dokument, WartungProtokoll protokoll) throws DocumentException {
        if (!notBlank(protokoll.getBemerkungen())) {
            return;
        }
        abschnittsUeberschrift(dokument, "Bemerkungen");
        Paragraph text = new Paragraph(pdfText(protokoll.getBemerkungen()), schrift(9, Font.NORMAL, GRUNDFARBE));
        text.setSpacingAfter(10);
        dokument.add(text);
    }

    private void unterschriften(Document dokument, WartungProtokoll protokoll) throws DocumentException {
        PdfPTable tabelle = new PdfPTable(new float[] {1f, 0.12f, 1f});
        tabelle.setWidthPercentage(100);
        tabelle.setKeepTogether(true);
        tabelle.addCell(unterschriftZelle("Techniker", protokoll.getUnterschriftTechniker(), protokoll.getUnterschriftTechnikerName(),
                protokoll.getAbgeschlossenAm() == null ? null : protokoll.getAbgeschlossenAm().toLocalDate()));
        tabelle.addCell(leereZelle());
        tabelle.addCell(unterschriftZelle("Kunde", protokoll.getUnterschriftKunde(), protokoll.getUnterschriftKundeName(),
                protokoll.getAbgeschlossenAm() == null ? null : protokoll.getAbgeschlossenAm().toLocalDate()));
        tabelle.setSpacingBefore(16);
        dokument.add(tabelle);
    }

    private PdfPCell unterschriftZelle(String rolle, String bild, String name, LocalDate datum) {
        PdfPCell zelle = leereZelle();
        Image bildObjekt = lesePng(bild);
        if (bildObjekt != null) {
            bildObjekt.scaleToFit(170, 55);
            PdfPCell bildZelle = new PdfPCell(bildObjekt, false);
            bildZelle.setBorder(Rectangle.BOTTOM);
            bildZelle.setBorderColor(GRUNDFARBE);
            bildZelle.setFixedHeight(60);
            bildZelle.setVerticalAlignment(Element.ALIGN_BOTTOM);
            bildZelle.setPaddingBottom(2);
            zelle.addElement(tabelleAusZelle(bildZelle));
        } else {
            // Leere Linie, damit sich das Protokoll nach dem Ausdrucken von Hand unterschreiben lässt.
            PdfPCell linie = new PdfPCell(new Phrase(" "));
            linie.setBorder(Rectangle.BOTTOM);
            linie.setBorderColor(GRUNDFARBE);
            linie.setFixedHeight(60);
            zelle.addElement(tabelleAusZelle(linie));
        }
        String unterzeile = rolle + (notBlank(name) ? ": " + name : "");
        zelle.addElement(new Paragraph(pdfText(unterzeile), schrift(9, Font.NORMAL, GRUNDFARBE)));
        if (bildObjekt != null && datum != null) {
            zelle.addElement(new Paragraph(DATUM.format(datum), schrift(8, Font.NORMAL, GRAU)));
        }
        return zelle;
    }

    // ── Hilfen ──────────────────────────────────────────────────────────────

    /** Ersetzt Zeichen, die die eingebauten PDF-Schriften nicht darstellen können, damit keine Lücken entstehen. */
    static String pdfText(String text) {
        if (text == null) {
            return "";
        }
        String ersetzt = text
                .replace("→", "->").replace("←", "<-").replace("≤", "<=").replace("≥", ">=")
                .replace("✓", "ok").replace("×", "x").replace(" ", " ");
        CharsetEncoder encoder = ANSI.newEncoder();
        StringBuilder ergebnis = new StringBuilder(ersetzt.length());
        ersetzt.codePoints().forEach(cp -> {
            String zeichen = new String(Character.toChars(cp));
            if (cp == '\n' || cp == '\r' || cp == '\t' || encoder.canEncode(zeichen)) {
                ergebnis.append(zeichen);
            } else {
                ergebnis.append('?');
            }
        });
        return ergebnis.toString();
    }

    static String ergebnisText(WartungErgebnis ergebnis) {
        if (ergebnis == null) {
            return "offen";
        }
        return switch (ergebnis) {
            case IO -> "i.O.";
            case NIO -> "n.i.O.";
            case NA -> "n.a.";
        };
    }

    private static Color ergebnisFarbe(WartungErgebnis ergebnis) {
        if (ergebnis == null) {
            return AMBER;
        }
        return switch (ergebnis) {
            case IO -> GRUEN;
            case NIO -> ROT;
            case NA -> GRAU;
        };
    }

    /** Dateiname ohne Zeichen, die in einem Header oder Dateisystem Ärger machen. */
    static String dateiname(WartungProtokoll protokoll) {
        return "Wartungsprotokoll_" + protokoll.getNummer().replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
    }

    /** Liest eine PNG-Data-URL. Alles andere oder Kaputtes ergibt {@code null}, die Unterschrift bleibt dann leer. */
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
            log.warn("Firmendaten konnten für das Wartungsprotokoll nicht geladen werden", e);
            return null;
        }
    }

    static Font schrift(float groesse, int stil, Color farbe) {
        return FontFactory.getFont(FontFactory.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED, groesse, stil, farbe);
    }

    static PdfPCell leereZelle() {
        PdfPCell zelle = new PdfPCell();
        zelle.setBorder(Rectangle.NO_BORDER);
        zelle.setPadding(0);
        return zelle;
    }

    static PdfPCell zelle(String text, Font schrift, int ausrichtung, Color randfarbe) {
        PdfPCell zelle = new PdfPCell(new Phrase(pdfText(text), schrift));
        zelle.setHorizontalAlignment(ausrichtung);
        zelle.setBorderColor(randfarbe);
        zelle.setPadding(4);
        return zelle;
    }

    private static PdfPTable tabelleAusZelle(PdfPCell zelle) {
        PdfPTable tabelle = new PdfPTable(1);
        tabelle.setWidthPercentage(100);
        tabelle.addCell(zelle);
        return tabelle;
    }

    private static void absatz(PdfPCell zelle, String text, Font schrift, int ausrichtung) {
        if (!notBlank(text)) {
            return;
        }
        Paragraph absatz = new Paragraph(pdfText(text), schrift);
        absatz.setAlignment(ausrichtung);
        zelle.addElement(absatz);
    }

    static void abschnittsUeberschrift(Document dokument, String text) throws DocumentException {
        Paragraph absatz = new Paragraph(pdfText(text.toUpperCase(Locale.GERMAN)), schrift(9, Font.BOLD, GRAU));
        absatz.setSpacingBefore(4);
        absatz.setSpacingAfter(4);
        dokument.add(absatz);
    }

    private static void angabenZeile(PdfPTable tabelle, String l1, String w1, String l2, String w2) {
        Font label = schrift(8, Font.BOLD, GRAU);
        Font wert = schrift(9, Font.NORMAL, GRUNDFARBE);
        for (String[] paar : new String[][] {{l1, w1}, {l2, w2}}) {
            PdfPCell k = new PdfPCell(new Phrase(pdfText(paar[0]), label));
            k.setBorder(Rectangle.BOTTOM);
            k.setBorderColor(LINIE);
            k.setPadding(4);
            tabelle.addCell(k);
            PdfPCell v = new PdfPCell(new Phrase(pdfText(notBlank(paar[1]) ? paar[1] : "–"), wert));
            v.setBorder(Rectangle.BOTTOM);
            v.setBorderColor(LINIE);
            v.setPadding(4);
            tabelle.addCell(v);
        }
    }

    static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }

    static String nvl(String text) {
        return text == null ? "" : text;
    }

    /** Fußzeile auf jeder Seite: Protokollnummer, "Seite x von y" und der Firmen-Fußzeilentext. */
    private static final class Fusszeile extends PdfPageEventHelper {
        private final String nummer;
        private final String firmenText;
        private PdfTemplate gesamt;
        private BaseFont schrift;

        Fusszeile(String nummer, String firmenText) {
            this.nummer = nummer;
            this.firmenText = firmenText;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                schrift = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            gesamt = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte inhalt = writer.getDirectContent();
            float unten = document.bottomMargin() - 22;
            inhalt.setColorStroke(LINIE);
            inhalt.moveTo(document.leftMargin(), unten + 12);
            inhalt.lineTo(document.getPageSize().getWidth() - document.rightMargin(), unten + 12);
            inhalt.stroke();

            inhalt.beginText();
            inhalt.setFontAndSize(schrift, 8);
            inhalt.setColorFill(GRAU);
            inhalt.setTextMatrix(document.leftMargin(), unten);
            inhalt.showText(pdfText("Wartungsprotokoll " + nummer + (notBlank(firmenText) ? "  ·  " + firmenText.replaceAll("\\s+", " ") : "")));
            inhalt.endText();

            String seite = "Seite " + writer.getPageNumber() + " von ";
            float breite = schrift.getWidthPoint(seite, 8);
            float x = document.getPageSize().getWidth() - document.rightMargin() - breite - 14;
            inhalt.beginText();
            inhalt.setFontAndSize(schrift, 8);
            inhalt.setColorFill(GRAU);
            inhalt.setTextMatrix(x, unten);
            inhalt.showText(seite);
            inhalt.endText();
            inhalt.addTemplate(gesamt, x + breite, unten);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            gesamt.beginText();
            gesamt.setFontAndSize(schrift, 8);
            gesamt.setColorFill(GRAU);
            gesamt.setTextMatrix(0, 0);
            gesamt.showText(String.valueOf(writer.getPageNumber() - 1));
            gesamt.endText();
        }
    }
}
