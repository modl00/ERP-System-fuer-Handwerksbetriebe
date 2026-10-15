package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.dto.wartung.WartungBerichtDto;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WartungBerichtServiceTest {

    private static final long ID = 42L;
    private static final byte[] PDF = "%PDF-1.4 bericht".getBytes();

    @Mock private WartungProtokollRepository protokollRepository;
    @Mock private WartungProtokollPdfService pdfService;
    @Mock private DateiSpeicherService dateiSpeicherService;
    @Mock private ProjektDokumentRepository dokumentRepository;

    @InjectMocks private WartungBerichtService service;

    private WartungProtokoll protokoll;
    private Projekt projekt;

    @BeforeEach
    void setUp() {
        projekt = new Projekt();
        projekt.setId(7L);
        protokoll = new WartungProtokoll();
        protokoll.setId(ID);
        protokoll.setNummer("WA-2026-1001");
        protokoll.setStatus(WartungStatus.ABGESCHLOSSEN);
        protokoll.setAbgeschlossenAm(LocalDateTime.of(2026, 10, 1, 12, 0));
        protokoll.setProjekt(projekt);
        lenient().when(protokollRepository.findMitSperre(ID)).thenReturn(Optional.of(protokoll));
    }

    private static ProjektDokument dokument(long id, String gespeichert) {
        ProjektDokument d = new ProjektDokument();
        d.setId(id);
        d.setOriginalDateiname("Wartungsprotokoll_WA-2026-1001.pdf");
        d.setGespeicherterDateiname(gespeichert);
        return d;
    }

    private void pdfErzeugbar() {
        when(pdfService.erstelle(ID)).thenReturn(new WartungProtokollPdfService.PdfDatei("Wartungsprotokoll_WA-2026-1001.pdf", PDF));
    }

    @Test
    @DisplayName("Der Bericht einer abgeschlossenen Prüfung wird als PDF im Projekt abgelegt und im Protokoll vermerkt")
    void legtDenBerichtImProjektAb() {
        pdfErzeugbar();
        when(dateiSpeicherService.speichereErzeugteDatei(any(), any(), any(), any(), any())).thenReturn(dokument(900L, "abc 123.pdf"));

        WartungBerichtDto bericht = service.erstelleImProjekt(ID);

        verify(dateiSpeicherService).speichereErzeugteDatei(PDF, "Wartung_2026-10-01_Wartungsprotokoll_WA-2026-1001.pdf", 7L, DokumentGruppe.DIVERSE_DOKUMENTE, "application/pdf");
        assertThat(protokoll.getBerichtDokumentId()).isEqualTo(900L);
        assertThat(protokoll.getBerichtErstelltAm()).isNotNull();
        verify(protokollRepository).save(protokoll);
        assertThat(bericht.dokumentId()).isEqualTo(900L);
        assertThat(bericht.url()).isEqualTo("/api/dokumente/abc%20123.pdf");
        assertThat(bericht.dateiname()).isEqualTo("Wartungsprotokoll_WA-2026-1001.pdf");
    }

    @Test
    @DisplayName("Automatisch nach dem Abschluss: Datum der Prüfung im Dateinamen, sonst das des Abschlusses")
    void automatischMitDatumImNamen() {
        pdfErzeugbar();
        when(protokollRepository.findById(ID)).thenReturn(Optional.of(protokoll));
        when(dateiSpeicherService.speichereErzeugteDatei(any(), any(), any(), any(), any())).thenReturn(dokument(900L, "abc.pdf"));
        protokoll.setDatum(java.time.LocalDate.of(2026, 9, 30));

        service.erstelleAutomatischImProjekt(ID);

        verify(dateiSpeicherService).speichereErzeugteDatei(PDF, "Wartung_2026-09-30_Wartungsprotokoll_WA-2026-1001.pdf", 7L, DokumentGruppe.DIVERSE_DOKUMENTE, "application/pdf");
    }

    @Test
    @DisplayName("Automatisch ohne Projekt oder ohne Abschluss: nichts passiert, kein Fehler")
    void automatischOhneProjektMachtNichts() {
        when(protokollRepository.findById(ID)).thenReturn(Optional.of(protokoll));
        protokoll.setProjekt(null);
        service.erstelleAutomatischImProjekt(ID);
        protokoll.setProjekt(projekt);
        protokoll.setAbgeschlossenAm(null);
        service.erstelleAutomatischImProjekt(ID);
        org.mockito.Mockito.verifyNoInteractions(dateiSpeicherService);
    }

    @Test
    @DisplayName("Ein erneuter Bericht ersetzt den bisherigen im Projekt, damit nicht mehrere Fassungen herumliegen")
    void ersetztDenBisherigenBericht() {
        pdfErzeugbar();
        protokoll.setBerichtDokumentId(800L);
        when(dokumentRepository.findById(800L)).thenReturn(Optional.of(dokument(800L, "alt.pdf")));
        when(dateiSpeicherService.speichereErzeugteDatei(any(), any(), any(), any(), any())).thenReturn(dokument(901L, "neu.pdf"));

        service.erstelleImProjekt(ID);

        assertThat(protokoll.getBerichtDokumentId()).isEqualTo(901L);
        verify(dateiSpeicherService).loescheDatei(800L);
    }

    @Test
    @DisplayName("Lässt sich der alte Bericht nicht entfernen, bleibt der neue trotzdem gespeichert")
    void alterBerichtNichtLoeschbar() {
        pdfErzeugbar();
        protokoll.setBerichtDokumentId(800L);
        when(dokumentRepository.findById(800L)).thenReturn(Optional.of(dokument(800L, "alt.pdf")));
        when(dateiSpeicherService.speichereErzeugteDatei(any(), any(), any(), any(), any())).thenReturn(dokument(901L, "neu.pdf"));
        doThrow(new RuntimeException("Datei weg")).when(dateiSpeicherService).loescheDatei(800L);

        WartungBerichtDto bericht = service.erstelleImProjekt(ID);

        assertThat(bericht.dokumentId()).isEqualTo(901L);
        assertThat(protokoll.getBerichtDokumentId()).isEqualTo(901L);
    }

    @Test
    @DisplayName("Wurde das alte Dokument im Projekt schon gelöscht, wird ohne Umstände ein neues angelegt")
    void altesDokumentSchonWeg() {
        pdfErzeugbar();
        protokoll.setBerichtDokumentId(800L);
        when(dokumentRepository.findById(800L)).thenReturn(Optional.empty());
        when(dateiSpeicherService.speichereErzeugteDatei(any(), any(), any(), any(), any())).thenReturn(dokument(901L, "neu.pdf"));

        service.erstelleImProjekt(ID);

        verify(dateiSpeicherService, never()).loescheDatei(eq(800L));
        assertThat(protokoll.getBerichtDokumentId()).isEqualTo(901L);
    }

    @Test
    @DisplayName("Vor dem Abschluss gibt es keinen Bericht (409), und es wird nichts erzeugt oder gespeichert")
    void nurNachDemAbschluss() {
        protokoll.setAbgeschlossenAm(null);
        protokoll.setStatus(WartungStatus.IN_BEARBEITUNG);

        assertThatThrownBy(() -> service.erstelleImProjekt(ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("nach dem Abschluss");
                });
        verify(pdfService, never()).erstelle(any());
        verify(dateiSpeicherService, never()).speichereErzeugteDatei(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ohne Projekt gibt es keinen Ablageort: 409 mit dem Hinweis, ein Projekt zu wählen")
    void ohneProjekt() {
        protokoll.setProjekt(null);

        assertThatThrownBy(() -> service.erstelleImProjekt(ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("Projekt");
                });
        verify(dateiSpeicherService, never()).speichereErzeugteDatei(any(), any(), any(), any(), any());
        assertThat(protokoll.getBerichtDokumentId()).isNull();
    }

    @Test
    @DisplayName("Ein unbekanntes Protokoll ergibt 404")
    void unbekannt() {
        when(protokollRepository.findMitSperre(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.erstelleImProjekt(999L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
