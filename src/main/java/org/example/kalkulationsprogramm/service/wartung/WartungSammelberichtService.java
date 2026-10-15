package org.example.kalkulationsprogramm.service.wartung;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungSammelberichtAnfrageDto;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.AMBER;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.GRAU;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.GRUEN;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.GRUNDFARBE;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.KOPF_HINTERGRUND;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.LINIE;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.ROT;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.leereZelle;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.notBlank;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.nvl;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.pdfText;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.schrift;
import static org.example.kalkulationsprogramm.service.wartung.WartungProtokollPdfService.zelle;

/**
 * Sammelbericht: ein PDF aus mehreren Wartungsprotokollen. Optional vorne ein Deckblatt mit Zeitraum,
 * Übersicht, Mängelliste und Kennzahlen, danach je Protokoll das vollständige Einzelprotokoll
 * (das gleiche PDF wie beim einzelnen Abruf, inklusive Unterschriften).
 */
@Slf4j
@Service
@AllArgsConstructor
public class WartungSammelberichtService {

    private static final DateTimeFormatter DATUM = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);

    private final WartungProtokollRepository protokollRepository;
    private final WartungProtokollPruefpunktRepository pruefpunktRepository;
    private final WartungProtokollPdfService einzelPdfService;
    private final FirmeninformationService firmeninformationService;

    /** Auswertung eines Protokolls für das Deckblatt. */
    record Zeile(WartungProtokoll protokoll, String ausfuehrung, long maengel, long offen, List<WartungProtokollPruefpunkt> maengelPunkte) { }

    @Transactional(readOnly = true)
    public WartungProtokollPdfService.PdfDatei erstelle(WartungSammelberichtAnfrageDto anfrage) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(anfrage.protokollIds()));
        List<WartungProtokoll> protokolle = new ArrayList<>(protokollRepository.findAllById(ids));
        if (protokolle.size() != ids.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mindestens ein gewähltes Protokoll wurde nicht gefunden.");
        }
        protokolle.sort(Comparator.comparing(WartungProtokoll::getDatum).thenComparing(WartungProtokoll::getNummer));

        List<Zeile> zeilen = protokolle.stream().map(this::auswerten).toList();
        boolean deckblatt = anfrage.mitDeckblatt() == null || anfrage.mitDeckblatt();
        String titel = notBlank(anfrage.titel()) ? anfrage.titel().trim() : null;

        List<RandomAccessReadBuffer> puffer = new ArrayList<>();
        try {
            PDFMergerUtility zusammenfuehrer = new PDFMergerUtility();
            if (deckblatt) {
                RandomAccessReadBuffer quelle = new RandomAccessReadBuffer(deckblattPdf(titel, zeilen));
                puffer.add(quelle);
                zusammenfuehrer.addSource(quelle);
            }
            for (WartungProtokoll protokoll : protokolle) {
                RandomAccessReadBuffer quelle = new RandomAccessReadBuffer(einzelPdf(protokoll));
                puffer.add(quelle);
                zusammenfuehrer.addSource(quelle);
            }
            ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
            zusammenfuehrer.setDestinationStream(ausgabe);
            zusammenfuehrer.mergeDocuments(null);
            return new WartungProtokollPdfService.PdfDatei(dateiname(zeilen), ausgabe.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Der Sammelbericht konnte nicht erstellt werden.", e);
        } finally {
            for (RandomAccessReadBuffer quelle : puffer) {
                try {
                    quelle.close();
                } catch (IOException ignoriert) {
                    // Schließen eines Speicherpuffers kann nicht sinnvoll scheitern
                }
            }
        }
    }

    /** Das Einzelprotokoll als PDF. Scheitert eines, nennt die Meldung seine Nummer: Bei 200 Protokollen sucht sonst niemand den Fehler. */
    private byte[] einzelPdf(WartungProtokoll protokoll) {
        try {
            return einzelPdfService.erstelle(protokoll.getId()).inhalt();
        } catch (RuntimeException e) {
            log.warn("Einzelprotokoll {} konnte nicht als PDF erstellt werden", protokoll.getNummer(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Das Protokoll " + protokoll.getNummer() + " konnte nicht als PDF erstellt werden. Der Sammelbericht wurde nicht erstellt.");
        }
    }

    private Zeile auswerten(WartungProtokoll protokoll) {
        List<WartungProtokollPruefpunkt> punkte = pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(protokoll.getId());
        List<WartungProtokollPruefpunkt> maengel = punkte.stream().filter(p -> p.getErgebnis() == WartungErgebnis.NIO).toList();
        long offen = punkte.stream().filter(p -> p.getErgebnis() == null).count();
        return new Zeile(protokoll, ausfuehrung(protokoll), maengel.size(), offen, maengel);
    }

    /** "Brandschutztür (2-flügelig), Feststellanlage (Freilauf)". */
    static String ausfuehrung(WartungProtokoll protokoll) {
        return protokoll.getModule().stream()
                .map(m -> notBlank(m.getUntertypLabel()) ? m.getModulName() + " (" + m.getUntertypLabel() + ")" : m.getModulName())
                .collect(Collectors.joining(", "));
    }

    /** Typ, Kunde und Zeitraum wie im Original; mehrere Kunden werden als "Mehrere Kunden" benannt. */
    static String dateiname(List<Zeile> zeilen) {
        Set<String> typen = zeilen.stream().map(z -> nvl(z.protokoll().getProtokollTyp())).collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> kunden = zeilen.stream().map(z -> z.protokoll().getKunde().getName()).collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDate von = zeilen.stream().map(z -> z.protokoll().getDatum()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate bis = zeilen.stream().map(z -> z.protokoll().getDatum()).max(Comparator.naturalOrder()).orElseThrow();
        String typ = typen.size() == 1 ? typen.iterator().next() : "Sammelbericht";
        String kunde = kunden.size() == 1 ? kunden.iterator().next() : "Mehrere Kunden";
        String datum = von.equals(bis) ? von.toString() : von + "_bis_" + bis;
        String roh = typ + "_" + kunde + "_" + datum + "_(" + zeilen.size() + "_Protokolle)";
        return roh.replaceAll("[^a-zA-Z0-9äöüÄÖÜß._()-]", "_") + ".pdf";
    }

    // ── Deckblatt ───────────────────────────────────────────────────────────

    private byte[] deckblattPdf(String titel, List<Zeile> zeilen) {
        ByteArrayOutputStream ausgabe = new ByteArrayOutputStream();
        Document dokument = new Document(PageSize.A4, 40, 40, 40, 50);
        try {
            PdfWriter.getInstance(dokument, ausgabe);
            dokument.open();
            dokument.addTitle(titel != null ? titel : "Sammelbericht");
            dokument.addCreator("Handwerkerprogramm");

            kopf(dokument);
            ueberschrift(dokument, titel, zeilen);
            kennzahlen(dokument, zeilen);
            ausfuehrungen(dokument, zeilen);
            uebersicht(dokument, zeilen);
            maengelListe(dokument, zeilen);
            dokument.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Das Deckblatt konnte nicht erstellt werden.", e);
        }
        return ausgabe.toByteArray();
    }

    private void kopf(Document dokument) throws DocumentException {
        FirmeninformationDto firma = null;
        try {
            firma = firmeninformationService.getFirmeninformation();
        } catch (RuntimeException e) {
            log.warn("Firmendaten für das Deckblatt nicht verfügbar", e);
        }
        Image logo = firmeninformationService.loadLogoImage();
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
        if (firma != null) {
            Paragraph absatz = new Paragraph(pdfText(nvl(firma.getFirmenname()) + "\n" + nvl(firma.getStrasse()) + "\n"
                    + (nvl(firma.getPlz()) + " " + nvl(firma.getOrt())).trim()), schrift(8, Font.NORMAL, GRAU));
            absatz.setAlignment(Element.ALIGN_RIGHT);
            rechts.addElement(absatz);
        }
        tabelle.addCell(rechts);
        tabelle.setSpacingAfter(18);
        dokument.add(tabelle);
    }

    private void ueberschrift(Document dokument, String titel, List<Zeile> zeilen) throws DocumentException {
        dokument.add(new Paragraph(pdfText(titel != null ? titel : "Sammelbericht"), schrift(20, Font.BOLD, GRUNDFARBE)));
        LocalDate von = zeilen.stream().map(z -> z.protokoll().getDatum()).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate bis = zeilen.stream().map(z -> z.protokoll().getDatum()).max(Comparator.naturalOrder()).orElseThrow();
        String kunden = zeilen.stream().map(z -> z.protokoll().getKunde().getName()).distinct().collect(Collectors.joining(", "));
        String techniker = zeilen.stream().map(z -> z.protokoll().getTechniker()).filter(java.util.Objects::nonNull)
                .map(WartungSammelberichtService::name).distinct().collect(Collectors.joining(", "));

        PdfPTable tabelle = new PdfPTable(new float[] {1f, 3f});
        tabelle.setWidthPercentage(100);
        tabelle.setSpacingBefore(10);
        tabelle.setSpacingAfter(12);
        angabe(tabelle, "Zeitraum", von.equals(bis) ? DATUM.format(von) : DATUM.format(von) + " bis " + DATUM.format(bis));
        angabe(tabelle, zeilen.stream().map(z -> z.protokoll().getKunde().getId()).distinct().count() > 1 ? "Kunden" : "Kunde", kunden);
        if (notBlank(techniker)) {
            angabe(tabelle, "Techniker", techniker);
        }
        angabe(tabelle, "Protokolle", String.valueOf(zeilen.size()));
        dokument.add(tabelle);
    }

    private static String name(Mitarbeiter m) {
        return (nvl(m.getVorname()) + " " + nvl(m.getNachname())).trim();
    }

    private static void angabe(PdfPTable tabelle, String label, String wert) {
        PdfPCell l = zelle(label, schrift(8, Font.BOLD, GRAU), Element.ALIGN_LEFT, LINIE);
        l.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        PdfPCell w = zelle(wert, schrift(9, Font.NORMAL, GRUNDFARBE), Element.ALIGN_LEFT, LINIE);
        w.setBorder(com.lowagie.text.Rectangle.BOTTOM);
        tabelle.addCell(l);
        tabelle.addCell(w);
    }

    private void kennzahlen(Document dokument, List<Zeile> zeilen) throws DocumentException {
        long ohne = zeilen.stream().filter(z -> z.maengel() == 0 && z.offen() == 0).count();
        long mit = zeilen.stream().filter(z -> z.maengel() > 0).count();
        long insgesamt = zeilen.stream().mapToLong(Zeile::maengel).sum();
        long unbewertet = zeilen.stream().filter(z -> z.offen() > 0).count();

        PdfPTable tabelle = new PdfPTable(4);
        tabelle.setWidthPercentage(100);
        tabelle.setSpacingAfter(12);
        // Zwei Protokolle derselben Tür (z. B. Nachprüfung) sind ein geprüftes Bauteil
        long bauteile = zeilen.stream().map(z -> z.protokoll().getTuer() != null && z.protokoll().getTuer().getId() != null
                ? "t" + z.protokoll().getTuer().getId() : "p" + z.protokoll().getId()).distinct().count();
        kachel(tabelle, "Bauteile geprüft", String.valueOf(bauteile), GRUNDFARBE);
        kachel(tabelle, "ohne Mängel", String.valueOf(ohne), GRUEN);
        kachel(tabelle, "mit Mängeln", mit == 0 ? "0" : mit + " (" + insgesamt + (insgesamt == 1 ? " Mangel" : " Mängel") + ")", mit > 0 ? ROT : GRUEN);
        kachel(tabelle, "mit offenen Punkten", String.valueOf(unbewertet), unbewertet > 0 ? AMBER : GRUEN);
        dokument.add(tabelle);
    }

    private static void kachel(PdfPTable tabelle, String label, String wert, Color farbe) {
        PdfPCell zelle = new PdfPCell();
        zelle.setBorderColor(LINIE);
        zelle.setPadding(7);
        zelle.setBackgroundColor(KOPF_HINTERGRUND);
        zelle.addElement(new Paragraph(pdfText(wert), schrift(13, Font.BOLD, farbe)));
        zelle.addElement(new Paragraph(pdfText(label), schrift(8, Font.NORMAL, GRAU)));
        tabelle.addCell(zelle);
    }

    private void ausfuehrungen(Document dokument, List<Zeile> zeilen) throws DocumentException {
        Map<String, Long> gruppen = zeilen.stream().collect(Collectors.groupingBy(
                z -> notBlank(z.ausfuehrung()) ? z.ausfuehrung() : "ohne Angabe", LinkedHashMap::new, Collectors.counting()));
        WartungProtokollPdfService.abschnittsUeberschrift(dokument, "Aufstellung nach Ausführung");
        PdfPTable tabelle = new PdfPTable(new float[] {5f, 1f});
        tabelle.setWidthPercentage(100);
        tabelle.setSpacingAfter(12);
        gruppen.forEach((ausfuehrung, anzahl) -> {
            tabelle.addCell(zelle(ausfuehrung, schrift(9, Font.NORMAL, GRUNDFARBE), Element.ALIGN_LEFT, LINIE));
            tabelle.addCell(zelle(String.valueOf(anzahl), schrift(9, Font.BOLD, GRUNDFARBE), Element.ALIGN_RIGHT, LINIE));
        });
        dokument.add(tabelle);
    }

    private void uebersicht(Document dokument, List<Zeile> zeilen) throws DocumentException {
        WartungProtokollPdfService.abschnittsUeberschrift(dokument, "Übersicht");
        PdfPTable tabelle = new PdfPTable(new float[] {2f, 2.2f, 3f, 1.4f, 2f});
        tabelle.setWidthPercentage(100);
        tabelle.setHeaderRows(1);
        tabelle.setSpacingAfter(12);
        for (String ueber : new String[] {"Objekt", "Tür / Tor", "Ausführung", "Datum", "Ergebnis"}) {
            PdfPCell kopf = zelle(ueber, schrift(8, Font.BOLD, GRAU), Element.ALIGN_LEFT, LINIE);
            kopf.setBackgroundColor(KOPF_HINTERGRUND);
            tabelle.addCell(kopf);
        }
        for (Zeile z : zeilen) {
            WartungProtokoll p = z.protokoll();
            Font normal = schrift(8, Font.NORMAL, GRUNDFARBE);
            tabelle.addCell(zelle(nvl(p.getObjekt() == null ? null : p.getObjekt().getBezeichnung()), normal, Element.ALIGN_LEFT, LINIE));
            tabelle.addCell(zelle(nvl(p.getTuer() == null ? null : p.getTuer().getBezeichnung()), normal, Element.ALIGN_LEFT, LINIE));
            tabelle.addCell(zelle(z.ausfuehrung(), normal, Element.ALIGN_LEFT, LINIE));
            tabelle.addCell(zelle(DATUM.format(p.getDatum()), normal, Element.ALIGN_LEFT, LINIE));
            String ergebnis;
            Color farbe;
            if (z.maengel() > 0) {
                ergebnis = z.maengel() + (z.maengel() == 1 ? " Mangel" : " Mängel");
                farbe = ROT;
            } else if (z.offen() > 0) {
                ergebnis = z.offen() + " offen";
                farbe = AMBER;
            } else {
                ergebnis = "ohne Mängel";
                farbe = GRUEN;
            }
            tabelle.addCell(zelle(ergebnis, schrift(8, Font.BOLD, farbe), Element.ALIGN_LEFT, LINIE));
        }
        dokument.add(tabelle);
    }

    private void maengelListe(Document dokument, List<Zeile> zeilen) throws DocumentException {
        if (zeilen.stream().noneMatch(z -> z.maengel() > 0)) {
            return;
        }
        WartungProtokollPdfService.abschnittsUeberschrift(dokument, "Festgestellte Mängel");
        PdfPTable tabelle = new PdfPTable(new float[] {2f, 2.2f, 3f, 3f});
        tabelle.setWidthPercentage(100);
        tabelle.setHeaderRows(1);
        for (String ueber : new String[] {"Protokoll", "Tür / Tor", "Prüfpunkt", "Bemerkung"}) {
            PdfPCell kopf = zelle(ueber, schrift(8, Font.BOLD, GRAU), Element.ALIGN_LEFT, LINIE);
            kopf.setBackgroundColor(KOPF_HINTERGRUND);
            tabelle.addCell(kopf);
        }
        for (Zeile z : zeilen) {
            for (WartungProtokollPruefpunkt punkt : z.maengelPunkte()) {
                Font normal = schrift(8, Font.NORMAL, GRUNDFARBE);
                tabelle.addCell(zelle(z.protokoll().getNummer(), normal, Element.ALIGN_LEFT, LINIE));
                tabelle.addCell(zelle(nvl(z.protokoll().getTuer() == null ? null : z.protokoll().getTuer().getBezeichnung()), normal, Element.ALIGN_LEFT, LINIE));
                tabelle.addCell(zelle(punkt.getLabel(), normal, Element.ALIGN_LEFT, LINIE));
                tabelle.addCell(zelle(nvl(punkt.getBemerkung()), normal, Element.ALIGN_LEFT, LINIE));
            }
        }
        dokument.add(tabelle);
    }
}
