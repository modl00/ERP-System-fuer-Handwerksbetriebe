package org.example.kalkulationsprogramm.service.wartung;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungSammelberichtAnfrageDto;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.service.FirmeninformationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WartungSammelberichtServiceTest {

    @Mock
    private WartungProtokollRepository protokollRepository;
    @Mock
    private WartungProtokollPruefpunktRepository pruefpunktRepository;
    @Mock
    private WartungProtokollPdfService einzelPdfService;
    @Mock
    private FirmeninformationService firmeninformationService;

    private WartungSammelberichtService service;

    private WartungProtokoll erstes;
    private WartungProtokoll zweites;

    @BeforeEach
    void setUp() {
        service = new WartungSammelberichtService(protokollRepository, pruefpunktRepository, einzelPdfService, firmeninformationService);
        Kunde kunde = new Kunde();
        kunde.setId(1L);
        kunde.setName("Max Mustermann GmbH");
        erstes = protokoll(1L, "WA-2026-1001", kunde, "Tür 1.01", LocalDate.of(2026, 5, 10), "2-flügelig");
        zweites = protokoll(2L, "WA-2026-1002", kunde, "Tor 2", LocalDate.of(2026, 5, 12), "1-flügelig");

        FirmeninformationDto firma = new FirmeninformationDto();
        firma.setFirmenname("Schlosserei Beispiel");
        lenient().when(firmeninformationService.getFirmeninformation()).thenReturn(firma);
        lenient().when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(1L))
                .thenReturn(List.of(punkt("Zulassungsschild lesbar", WartungErgebnis.IO, null),
                        punkt("Dichtung auf Beschädigungen prüfen", WartungErgebnis.NIO, "Dichtung gerissen")));
        lenient().when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(2L))
                .thenReturn(List.of(punkt("Schließfunktion prüfen", WartungErgebnis.IO, null)));
        lenient().when(einzelPdfService.erstelle(anyLong())).thenAnswer(a -> new WartungProtokollPdfService.PdfDatei("x.pdf", einSeitenPdf("Einzelblatt " + a.getArgument(0))));
    }

    private static WartungProtokoll protokoll(Long id, String nummer, Kunde kunde, String tuerName, LocalDate datum, String bauart) {
        WartungObjekt objekt = new WartungObjekt();
        objekt.setBezeichnung("Werk Nord");
        objekt.setKunde(kunde);
        WartungTuer tuer = new WartungTuer();
        tuer.setId((long) tuerName.hashCode());
        tuer.setBezeichnung(tuerName);
        tuer.setObjekt(objekt);
        Mitarbeiter techniker = new Mitarbeiter();
        techniker.setVorname("Erika");
        techniker.setNachname("Beispiel");
        WartungProtokoll p = new WartungProtokoll();
        p.setId(id);
        p.setNummer(nummer);
        p.setKunde(kunde);
        p.setObjekt(objekt);
        p.setTuer(tuer);
        p.setTechniker(techniker);
        p.setProtokollTyp("Wartungsprotokoll");
        p.setDatum(datum);
        p.setModule(new LinkedHashSet<>(List.of(new WartungProtokollModul(1L, "m_bst", "Brandschutztür", "x", bauart))));
        return p;
    }

    private static WartungProtokollPruefpunkt punkt(String label, WartungErgebnis ergebnis, String bemerkung) {
        WartungProtokollPruefpunkt p = new WartungProtokollPruefpunkt();
        p.setLabel(label);
        p.setErgebnis(ergebnis);
        p.setBemerkung(bemerkung);
        return p;
    }

    private static byte[] einSeitenPdf(String text) {
        try {
            ByteArrayOutputStream aus = new ByteArrayOutputStream();
            Document d = new Document();
            PdfWriter.getInstance(d, aus);
            d.open();
            d.add(new Paragraph(text));
            d.close();
            return aus.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String text(byte[] pdf) throws Exception {
        try (PDDocument dokument = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(dokument).replaceAll("\\s+", " ");
        }
    }

    private static int seiten(byte[] pdf) throws Exception {
        try (PDDocument dokument = Loader.loadPDF(pdf)) {
            return dokument.getNumberOfPages();
        }
    }

    @Test
    @DisplayName("Deckblatt plus je Protokoll ein Blatt, nach Datum sortiert")
    void deckblattUndEinzelblaetter() throws Exception {
        // Reihenfolge der Anfrage ist umgekehrt: Das Ergebnis richtet sich trotzdem nach dem Datum.
        when(protokollRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(zweites, erstes));

        WartungProtokollPdfService.PdfDatei datei = service.erstelle(new WartungSammelberichtAnfrageDto(List.of(2L, 1L), "Jahreswartung Werk Nord", true));

        assertThat(seiten(datei.inhalt())).isEqualTo(3);
        String alles = text(datei.inhalt());
        assertThat(alles.indexOf("Einzelblatt 1")).isLessThan(alles.indexOf("Einzelblatt 2"));
        assertThat(alles).contains("Jahreswartung Werk Nord", "Max Mustermann GmbH", "10.05.2026 bis 12.05.2026", "Erika Beispiel");
    }

    @Test
    @DisplayName("Das Deckblatt nennt Kennzahlen, Übersicht und die Mängelliste mit Bemerkung")
    void deckblattInhalt() throws Exception {
        when(protokollRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(erstes, zweites));

        byte[] pdf = service.erstelle(new WartungSammelberichtAnfrageDto(List.of(1L, 2L), null, null)).inhalt();

        String deckblatt;
        try (PDDocument d = Loader.loadPDF(pdf)) {
            PDFTextStripper s = new PDFTextStripper();
            s.setStartPage(1);
            s.setEndPage(1);
            deckblatt = s.getText(d).replaceAll("\\s+", " ");
        }
        assertThat(deckblatt).contains("Sammelbericht", "Bauteile geprüft", "ohne Mängel", "1 Mangel",
                "Brandschutztür (2-flügelig)", "Brandschutztür (1-flügelig)", "Tür 1.01", "Tor 2",
                "FESTGESTELLTE MÄNGEL", "Dichtung auf Beschädigungen prüfen", "Dichtung gerissen", "WA-2026-1001");
        assertThat(deckblatt).doesNotContain("Schließfunktion prüfen"); // i.O.-Punkte stehen nicht in der Mängelliste
    }

    @Test
    @DisplayName("Ohne Deckblatt gibt es nur die Einzelblätter")
    void ohneDeckblatt() throws Exception {
        when(protokollRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(erstes, zweites));

        byte[] pdf = service.erstelle(new WartungSammelberichtAnfrageDto(List.of(1L, 2L), "Titel", false)).inhalt();

        assertThat(seiten(pdf)).isEqualTo(2);
        assertThat(text(pdf)).doesNotContain("AUFSTELLUNG NACH AUSFÜHRUNG");
    }

    @Test
    @DisplayName("Ohne Mängel entfällt die Mängelliste")
    void ohneMaengelKeineListe() throws Exception {
        when(protokollRepository.findAllById(List.of(2L))).thenReturn(List.of(zweites));

        byte[] pdf = service.erstelle(new WartungSammelberichtAnfrageDto(List.of(2L), null, true)).inhalt();

        assertThat(text(pdf)).doesNotContain("FESTGESTELLTE MÄNGEL").contains("ohne Mängel");
    }

    @Test
    @DisplayName("Doppelte Nummern zählen nur einmal")
    void doppelteWerdenZusammengefasst() throws Exception {
        when(protokollRepository.findAllById(List.of(1L))).thenReturn(List.of(erstes));

        byte[] pdf = service.erstelle(new WartungSammelberichtAnfrageDto(List.of(1L, 1L, 1L), null, false)).inhalt();

        assertThat(seiten(pdf)).isEqualTo(1);
    }

    @Test
    @DisplayName("Ein unbekanntes Protokoll ergibt 404 und es wird nichts erzeugt")
    void unbekanntesProtokoll() {
        when(protokollRepository.findAllById(List.of(1L, 99L))).thenReturn(List.of(erstes));

        assertThatThrownBy(() -> service.erstelle(new WartungSammelberichtAnfrageDto(List.of(1L, 99L), null, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(einzelPdfService, never()).erstelle(anyLong());
    }

    @Test
    @DisplayName("Der Dateiname nennt Art, Kunde, Zeitraum und Anzahl und enthält nur sichere Zeichen")
    void dateiname() {
        when(protokollRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(erstes, zweites));

        String name = service.erstelle(new WartungSammelberichtAnfrageDto(List.of(1L, 2L), "../../etc", false)).dateiname();

        // Der Titel ist nur Anzeige im Deckblatt und fließt nicht in den Dateinamen ein
        assertThat(name).doesNotContain("etc", "..", "/");
        assertThat(name).isEqualTo("Wartungsprotokoll_Max_Mustermann_GmbH_2026-05-10_bis_2026-05-12_(2_Protokolle).pdf");
    }

    @Test
    @DisplayName("Bei mehreren Kunden heißt der Bericht \"Mehrere Kunden\"")
    void mehrereKunden() {
        Kunde anderer = new Kunde();
        anderer.setId(2L);
        anderer.setName("Erika Beispiel AG");
        zweites.setKunde(anderer);
        when(protokollRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(erstes, zweites));

        String name = service.erstelle(new WartungSammelberichtAnfrageDto(List.of(1L, 2L), null, false)).dateiname();

        assertThat(name).contains("Mehrere_Kunden");
    }

    @Test
    @DisplayName("Die Kennzahlen zählen Bauteile (nicht Protokolle), Wartungen ohne und mit Mängeln und solche mit offenen Punkten")
    void kennzahlen() throws Exception {
        WartungProtokoll nachpruefung = protokoll(3L, "WA-2026-1003", erstes.getKunde(), "Tür 1.01", LocalDate.of(2026, 6, 1), "2-flügelig");
        WartungProtokoll offen = protokoll(4L, "WA-2026-1004", erstes.getKunde(), "Tor 3", LocalDate.of(2026, 6, 2), "1-flügelig");
        lenient().when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(3L)).thenReturn(List.of(punkt("Dichtung", WartungErgebnis.IO, null)));
        lenient().when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(4L)).thenReturn(List.of(punkt("Offener Punkt", null, null), punkt("Schild", WartungErgebnis.IO, null)));
        when(protokollRepository.findAllById(List.of(1L, 2L, 3L, 4L))).thenReturn(List.of(erstes, zweites, nachpruefung, offen));

        byte[] pdf = service.erstelle(new WartungSammelberichtAnfrageDto(List.of(1L, 2L, 3L, 4L), null, true)).inhalt();

        String deckblatt;
        try (PDDocument d = Loader.loadPDF(pdf)) {
            PDFTextStripper st = new PDFTextStripper();
            st.setStartPage(1);
            st.setEndPage(1);
            deckblatt = st.getText(d).replaceAll("\\s+", " ");
        }
        assertThat(deckblatt).doesNotContain("(0 Mängel)");
        // 4 Protokolle, aber nur 3 verschiedene Türen (Tür 1.01 zweimal); 2 ohne Mängel (1002, 1003); 1 mit Mängeln (1001); 1 mit offenen Punkten (1004)
        assertThat(deckblatt).contains("3 Bauteile geprüft", "2 ohne Mängel", "1 (1 Mangel) mit Mängeln", "1 mit offenen Punkten");
    }

    @Test
    @DisplayName("Ein Protokoll ohne Techniker, Objekt und Tür bricht den Bericht nicht ab")
    void luckenhafteProtokolle() throws Exception {
        zweites.setTechniker(null);
        zweites.setObjekt(null);
        zweites.setTuer(null);
        when(protokollRepository.findAllById(List.of(2L))).thenReturn(List.of(zweites));

        byte[] pdf = service.erstelle(new WartungSammelberichtAnfrageDto(List.of(2L), null, true)).inhalt();

        assertThat(seiten(pdf)).isEqualTo(2);
        assertThat(text(pdf)).contains("Max Mustermann GmbH", "ohne Mängel");
    }

    @Test
    @DisplayName("Sonderzeichen im Titel und im Kundennamen stören das PDF nicht")
    void sonderzeichen() throws Exception {
        erstes.getKunde().setName("Müller & Söhne <GmbH> 😀");
        when(protokollRepository.findAllById(List.of(1L))).thenReturn(List.of(erstes));

        var datei = service.erstelle(new WartungSammelberichtAnfrageDto(List.of(1L), "Prüfung <b>Halle</b> \"Nord\" 😀", true));

        assertThat(text(datei.inhalt())).contains("Müller & Söhne <GmbH>", "Prüfung <b>Halle</b>");
        assertThat(datei.dateiname()).matches("[a-zA-Z0-9äöüÄÖÜß._()-]+");
    }

    @Test
    @DisplayName("Scheitert ein Einzelprotokoll, nennt die Meldung seine Nummer und es entsteht kein Sammelbericht")
    void einzelPdfScheitert() {
        when(protokollRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(erstes, zweites));
        when(einzelPdfService.erstelle(2L)).thenThrow(new IllegalStateException("kaputt"));

        assertThatThrownBy(() -> service.erstelle(new WartungSammelberichtAnfrageDto(List.of(1L, 2L), null, true)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
                    assertThat(e.getReason()).contains("WA-2026-1002");
                });
    }
}
