package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.wartung.WartungInhaltAenderungDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktErgebnisDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungUnterschriftDto;
import org.example.kalkulationsprogramm.mapper.wartung.WartungMapper;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungObjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollAbschnittRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungTuerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WartungProtokollServiceTest {

    private static final long ID = 42L;
    /** Signatur plus IHDR-Chunk (360 x 120): mehr prüft der Unterschrift-Prüfer nicht. */
    private static final byte[] PNG = java.nio.ByteBuffer.allocate(33)
            .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
            .putInt(13).put(new byte[] {'I', 'H', 'D', 'R'}).putInt(360).putInt(120).put(new byte[] {8, 6, 0, 0, 0}).array();
    private static final String PNG_URL = WartungUnterschriftPruefer.PRAEFIX + Base64.getEncoder().encodeToString(PNG);

    @Mock
    private WartungProtokollRepository protokollRepository;
    @Mock
    private WartungProtokollAbschnittRepository abschnittRepository;
    @Mock
    private WartungProtokollPruefpunktRepository pruefpunktRepository;
    @Mock
    private WartungProtokollDatenfeldRepository datenfeldRepository;
    @Mock
    private WartungObjektRepository objektRepository;
    @Mock
    private WartungTuerRepository tuerRepository;
    @Mock
    private KundeRepository kundeRepository;
    @Mock
    private MitarbeiterRepository mitarbeiterRepository;
    @Mock
    private ProjektRepository projektRepository;
    @Mock
    private WartungModulAuswahlPruefer auswahlPruefer;
    @Mock
    private WartungInhaltKopierer inhaltKopierer;
    @Mock
    private WartungNummernService nummernService;
    @Mock
    private org.example.kalkulationsprogramm.repository.wartung.WartungMitarbeiterUnterschriftRepository unterschriftRepository;
    @Mock
    private org.example.kalkulationsprogramm.repository.ProjektDokumentRepository projektDokumentRepository;
    @Mock
    private org.example.kalkulationsprogramm.repository.wartung.WartungProtokollVerlaufRepository verlaufRepository;
    @Mock
    private org.springframework.context.ApplicationEventPublisher ereignisse;
    @Spy
    private WartungMapper mapper = new WartungMapper();

    @InjectMocks
    private WartungProtokollService service;

    private WartungProtokoll protokoll;
    private WartungTuer tuer;

    @BeforeEach
    void setUp() {
        Kunde kunde = new Kunde();
        kunde.setId(1L);
        kunde.setName("Max Mustermann GmbH");
        WartungObjekt objekt = new WartungObjekt();
        objekt.setId(5L);
        objekt.setKunde(kunde);
        objekt.setBezeichnung("Werk Nord");
        tuer = new WartungTuer();
        tuer.setId(9L);
        tuer.setObjekt(objekt);
        tuer.setBezeichnung("Tür 1.01");

        protokoll = new WartungProtokoll();
        protokoll.setId(ID);
        protokoll.setNummer("WA-2026-1001");
        protokoll.setKunde(kunde);
        protokoll.setObjekt(objekt);
        protokoll.setTuer(tuer);
        protokoll.setDatum(LocalDate.of(2026, 5, 10));
        protokoll.setIntervallMonate(12);
        protokoll.setStatus(WartungStatus.IN_BEARBEITUNG);
        protokoll.setAngelegtAm(LocalDateTime.now());
        protokoll.setGeaendertAm(LocalDateTime.now());

        lenient().when(protokollRepository.findById(ID)).thenReturn(Optional.of(protokoll));
        lenient().when(protokollRepository.findMitSperre(ID)).thenReturn(Optional.of(protokoll));
        lenient().when(protokollRepository.save(any(WartungProtokoll.class))).thenAnswer(a -> a.getArgument(0));
        lenient().when(protokollRepository.saveAndFlush(any(WartungProtokoll.class))).thenAnswer(a -> a.getArgument(0));
        lenient().when(abschnittRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenReturn(List.of());
        lenient().when(datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenReturn(List.of());
        lenient().when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenReturn(List.of());
    }

    // ── Abschließen ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Abschluss mit unbewerteten Prüfpunkten wird abgelehnt und nennt die Anzahl")
    void abschlussMitOffenenPunkten() {
        when(pruefpunktRepository.countByProtokollIdAndErgebnisIsNull(ID)).thenReturn(3L);

        assertThatThrownBy(() -> service.statusWechseln(ID, WartungStatus.ABGESCHLOSSEN))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("3 Prüfpunkte").contains("nicht bewertet");
                });
        assertThat(protokoll.getStatus()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
        assertThat(protokoll.getAbgeschlossenAm()).isNull();
    }

    @Test
    @DisplayName("Bei genau einem offenen Prüfpunkt steht die Meldung im Singular")
    void abschlussEinOffenerPunkt() {
        when(pruefpunktRepository.countByProtokollIdAndErgebnisIsNull(ID)).thenReturn(1L);

        assertThatThrownBy(() -> service.statusWechseln(ID, WartungStatus.ABGESCHLOSSEN))
                .hasMessageContaining("Ein Prüfpunkt ist noch nicht bewertet");
    }

    @Test
    @DisplayName("Abschluss ohne Unterschrift des Technikers wird abgelehnt, auch wenn alles bewertet ist")
    void abschlussOhneUnterschrift() {
        when(pruefpunktRepository.countByProtokollIdAndErgebnisIsNull(ID)).thenReturn(0L);

        assertThatThrownBy(() -> service.statusWechseln(ID, WartungStatus.ABGESCHLOSSEN))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("als Techniker unterschreiben");
                });
    }

    @Test
    @DisplayName("Eine Unterschrift ohne Namen reicht für den Abschluss nicht")
    void abschlussUnterschriftOhneName() {
        when(pruefpunktRepository.countByProtokollIdAndErgebnisIsNull(ID)).thenReturn(0L);
        protokoll.setUnterschriftTechniker(PNG_URL);
        protokoll.setUnterschriftTechnikerName("  ");

        assertThatThrownBy(() -> service.statusWechseln(ID, WartungStatus.ABGESCHLOSSEN))
                .hasMessageContaining("unterschreiben");
    }

    @Test
    @DisplayName("Erfolgreicher Abschluss setzt den Zeitpunkt und schreibt den Wartungsplan der Tür fort")
    void abschlussSchreibtWartungsplanFort() {
        when(pruefpunktRepository.countByProtokollIdAndErgebnisIsNull(ID)).thenReturn(0L);
        protokoll.setUnterschriftTechniker(PNG_URL);
        protokoll.setUnterschriftTechnikerName("Max Mustermann");
        protokoll.setIntervallMonate(6);
        // Nach dem Speichern liefert die Abfrage dieses Protokoll als neuestes abgeschlossenes.
        when(protokollRepository.findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(protokoll));

        WartungProtokollDto ergebnis = service.statusWechseln(ID, WartungStatus.ABGESCHLOSSEN);

        assertThat(ergebnis.status()).isEqualTo(WartungStatus.ABGESCHLOSSEN);
        assertThat(protokoll.getAbgeschlossenAm()).isNotNull();
        assertThat(tuer.getIntervallMonate()).isEqualTo(6);
        assertThat(tuer.getNaechsteFaelligkeit()).isEqualTo(LocalDate.of(2026, 11, 10));
        verify(tuerRepository).save(tuer);
    }

    @Test
    @DisplayName("Nach dem Abschluss wird gemeldet, dass der Bericht ins Projekt soll; beim Wiederöffnen nicht")
    void abschlussMeldetDenBerichtsAuftrag() {
        when(pruefpunktRepository.countByProtokollIdAndErgebnisIsNull(ID)).thenReturn(0L);
        protokoll.setUnterschriftTechniker(PNG_URL);
        protokoll.setUnterschriftTechnikerName("Max Mustermann");
        when(protokollRepository.findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(any(), any(Pageable.class))).thenReturn(List.of(protokoll));

        service.statusWechseln(ID, WartungStatus.ABGESCHLOSSEN);
        verify(ereignisse).publishEvent(new WartungProtokollAbgeschlossen(ID));

        org.mockito.Mockito.clearInvocations(ereignisse);
        service.statusWechseln(ID, WartungStatus.IN_BEARBEITUNG);
        org.mockito.Mockito.verifyNoInteractions(ereignisse);
    }

    @Test
    @DisplayName("Wieder öffnen nimmt den Abschlusszeitpunkt weg und rechnet den Plan der Tür neu")
    void wiederOeffnen() {
        protokoll.setStatus(WartungStatus.ABGESCHLOSSEN);
        protokoll.setAbgeschlossenAm(LocalDateTime.now());
        when(protokollRepository.findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        WartungProtokollDto ergebnis = service.statusWechseln(ID, WartungStatus.IN_BEARBEITUNG);

        assertThat(ergebnis.status()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
        assertThat(protokoll.getAbgeschlossenAm()).isNull();
        assertThat(ergebnis.bearbeitbar()).isTrue();
    }

    @Test
    @DisplayName("Wieder öffnen steht mit Benutzer, Zeit und Grund im Verlauf (#238)")
    void wiederOeffnenSchreibtVerlauf() {
        protokoll.setStatus(WartungStatus.ABGESCHLOSSEN);
        protokoll.setAbgeschlossenAm(LocalDateTime.now());
        when(protokollRepository.findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(any(), any(Pageable.class))).thenReturn(List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("anna", "x"));
        try {
            service.statusWechseln(ID, WartungStatus.IN_BEARBEITUNG, "  Messwert war falsch  ");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        org.mockito.ArgumentCaptor<org.example.kalkulationsprogramm.domain.wartung.WartungProtokollVerlauf> v =
                org.mockito.ArgumentCaptor.forClass(org.example.kalkulationsprogramm.domain.wartung.WartungProtokollVerlauf.class);
        verify(verlaufRepository).save(v.capture());
        assertThat(v.getValue().getBenutzer()).isEqualTo("anna");
        assertThat(v.getValue().getVonStatus()).isEqualTo(WartungStatus.ABGESCHLOSSEN);
        assertThat(v.getValue().getNachStatus()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
        assertThat(v.getValue().getGrund()).isEqualTo("Messwert war falsch");
        assertThat(v.getValue().getZeit()).isNotNull();
    }

    @Test
    @DisplayName("Ohne Statuswechsel oder bei verbotenem Wechsel entsteht kein Verlaufseintrag")
    void keinVerlaufOhneWechsel() {
        protokoll.setStatus(WartungStatus.OFFEN);
        service.statusWechseln(ID, WartungStatus.OFFEN);
        assertThatThrownBy(() -> service.statusWechseln(ID, WartungStatus.ABGESCHLOSSEN)).isInstanceOf(RuntimeException.class);
        verify(verlaufRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    @DisplayName("Ein zu langer Grund wird auf 500 Zeichen gekürzt")
    void langerGrundWirdGekuerzt() {
        protokoll.setStatus(WartungStatus.ABGESCHLOSSEN);
        protokoll.setAbgeschlossenAm(LocalDateTime.now());
        when(protokollRepository.findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(any(), any(Pageable.class))).thenReturn(List.of());
        service.statusWechseln(ID, WartungStatus.IN_BEARBEITUNG, "x".repeat(900));
        org.mockito.ArgumentCaptor<org.example.kalkulationsprogramm.domain.wartung.WartungProtokollVerlauf> v =
                org.mockito.ArgumentCaptor.forClass(org.example.kalkulationsprogramm.domain.wartung.WartungProtokollVerlauf.class);
        verify(verlaufRepository).save(v.capture());
        assertThat(v.getValue().getGrund()).hasSize(500);
        assertThat(v.getValue().getBenutzer()).as("ohne Anmeldung kein Name").isNull();
    }

    @Test
    @DisplayName("Der Verlauf kommt neueste zuerst; unbekanntes Protokoll gibt 404")
    void verlaufLesen() {
        when(protokollRepository.existsById(ID)).thenReturn(true);
        var e = new org.example.kalkulationsprogramm.domain.wartung.WartungProtokollVerlauf();
        e.setZeit(LocalDateTime.of(2026, 10, 9, 8, 0));
        e.setBenutzer("anna");
        e.setVonStatus(WartungStatus.ABGESCHLOSSEN);
        e.setNachStatus(WartungStatus.IN_BEARBEITUNG);
        e.setGrund("Tippfehler");
        when(verlaufRepository.findByProtokollIdOrderByZeitDescIdDesc(ID)).thenReturn(List.of(e));

        assertThat(service.verlauf(ID)).singleElement().satisfies(z -> {
            assertThat(z.benutzer()).isEqualTo("anna");
            assertThat(z.grund()).isEqualTo("Tippfehler");
            assertThat(z.nachStatus()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
        });
        when(protokollRepository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> service.verlauf(999L)).hasMessageContaining("nicht gefunden");
    }

    @Test
    @DisplayName("Wieder öffnen des einzigen abgeschlossenen Protokolls nimmt die Fälligkeit der Tür weg")
    void wiederOeffnenLoeschtFaelligkeit() {
        tuer.setNaechsteFaelligkeit(LocalDate.of(2027, 5, 10));
        protokoll.setStatus(WartungStatus.ABGESCHLOSSEN);
        protokoll.setAbgeschlossenAm(LocalDateTime.now());
        when(protokollRepository.findByTuerIdAndAbgeschlossenAmIsNotNullOrderByDatumDescIdDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        service.statusWechseln(ID, WartungStatus.IN_BEARBEITUNG);

        assertThat(tuer.getNaechsteFaelligkeit()).isNull();
        verify(tuerRepository).save(tuer);
    }

    @Test
    @DisplayName("Kopfdaten lassen sich speichern, auch wenn der eingetragene Techniker inzwischen nicht mehr aktiv ist")
    void kopfMitInaktivemTechniker() {
        org.example.kalkulationsprogramm.domain.Mitarbeiter ausgeschieden = new org.example.kalkulationsprogramm.domain.Mitarbeiter();
        ausgeschieden.setId(3L);
        ausgeschieden.setAktiv(false);
        protokoll.setTechniker(ausgeschieden);

        WartungProtokollDto ergebnis = service.kopfAendern(ID, new org.example.kalkulationsprogramm.dto.wartung.WartungProtokollKopfDto(
                "Jahreswartung", "Wartungsprotokoll", LocalDate.of(2026, 5, 10), 12, 3L, null, null, "Neue Bemerkung", null));

        assertThat(ergebnis.bemerkungen()).isEqualTo("Neue Bemerkung");
        assertThat(protokoll.getTechniker()).isSameAs(ausgeschieden);
    }

    @Test
    @DisplayName("Ein inaktiver Techniker kann neu nicht gewählt werden")
    void kopfWechselAufInaktivenTechniker() {
        org.example.kalkulationsprogramm.domain.Mitarbeiter ausgeschieden = new org.example.kalkulationsprogramm.domain.Mitarbeiter();
        ausgeschieden.setId(4L);
        ausgeschieden.setAktiv(false);
        when(mitarbeiterRepository.findById(4L)).thenReturn(Optional.of(ausgeschieden));

        assertThatThrownBy(() -> service.kopfAendern(ID, new org.example.kalkulationsprogramm.dto.wartung.WartungProtokollKopfDto(
                null, "Wartungsprotokoll", LocalDate.of(2026, 5, 10), 12, 4L, null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("Ein unerlaubter Wechsel (abgeschlossen -> offen) wird mit 409 abgelehnt")
    void unerlaubterWechsel() {
        protokoll.setStatus(WartungStatus.ABGESCHLOSSEN);

        assertThatThrownBy(() -> service.statusWechseln(ID, WartungStatus.OFFEN))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    @DisplayName("Ein Wechsel auf den eigenen Status ändert nichts")
    void gleicherStatus() {
        WartungProtokollDto ergebnis = service.statusWechseln(ID, WartungStatus.IN_BEARBEITUNG);

        assertThat(ergebnis.status()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
        verify(protokollRepository, never()).saveAndFlush(any(WartungProtokoll.class));
    }

    @Test
    @DisplayName("Aus dem Archiv: war es abgeschlossen, geht es nur als abgeschlossen zurück")
    void archivWiederherstellenAbgeschlossen() {
        protokoll.setStatus(WartungStatus.ARCHIVIERT);
        protokoll.setAbgeschlossenAm(LocalDateTime.now());

        assertThatThrownBy(() -> service.statusWechseln(ID, WartungStatus.OFFEN))
                .hasMessageContaining("als „Abgeschlossen“ wiederherstellen");
    }

    @Test
    @DisplayName("Aus dem Archiv: war es nie abgeschlossen, geht es nicht als abgeschlossen zurück")
    void archivWiederherstellenNieAbgeschlossen() {
        protokoll.setStatus(WartungStatus.ARCHIVIERT);
        protokoll.setAbgeschlossenAm(null);

        assertThatThrownBy(() -> service.statusWechseln(ID, WartungStatus.ABGESCHLOSSEN))
                .hasMessageContaining("nie abgeschlossen");
    }

    // ── Bearbeiten ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ein abgeschlossenes Protokoll lässt sich nicht mehr bewerten")
    void bewertenNachAbschluss() {
        protokoll.setStatus(WartungStatus.ABGESCHLOSSEN);

        assertThatThrownBy(() -> service.pruefpunktBewerten(ID, 1L, new WartungPruefpunktErgebnisDto(WartungErgebnis.IO, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("nicht mehr ändern").contains("wieder öffnen");
                });
        verify(pruefpunktRepository, never()).save(any());
    }

    @Test
    @DisplayName("Die erste Bewertung stellt ein offenes Protokoll automatisch auf 'In Arbeit'")
    void ersteBewertungStartetBearbeitung() {
        protokoll.setStatus(WartungStatus.OFFEN);
        WartungProtokollPruefpunkt punkt = new WartungProtokollPruefpunkt();
        punkt.setId(7L);
        punkt.setProtokollId(ID);
        punkt.setLabel("Schild lesbar");
        when(pruefpunktRepository.findByIdAndProtokollId(7L, ID)).thenReturn(Optional.of(punkt));
        when(pruefpunktRepository.save(any(WartungProtokollPruefpunkt.class))).thenAnswer(a -> a.getArgument(0));
        when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenReturn(List.of(punkt));

        WartungInhaltAenderungDto antwort = service.pruefpunktBewerten(ID, 7L,
                new WartungPruefpunktErgebnisDto(WartungErgebnis.NIO, "  Dichtung gerissen  "));

        assertThat(antwort.status()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
        assertThat(antwort.pruefpunkt().ergebnis()).isEqualTo(WartungErgebnis.NIO);
        assertThat(antwort.pruefpunkt().bemerkung()).isEqualTo("Dichtung gerissen");
        assertThat(antwort.statistik().nichtInOrdnung()).isEqualTo(1);
    }

    @Test
    @DisplayName("Ergebnis null setzt einen Prüfpunkt wieder auf 'nicht bewertet'")
    void bewertungZuruecknehmen() {
        WartungProtokollPruefpunkt punkt = new WartungProtokollPruefpunkt();
        punkt.setId(7L);
        punkt.setErgebnis(WartungErgebnis.IO);
        when(pruefpunktRepository.findByIdAndProtokollId(7L, ID)).thenReturn(Optional.of(punkt));
        when(pruefpunktRepository.save(any(WartungProtokollPruefpunkt.class))).thenAnswer(a -> a.getArgument(0));
        when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenReturn(List.of(punkt));

        WartungInhaltAenderungDto antwort = service.pruefpunktBewerten(ID, 7L, new WartungPruefpunktErgebnisDto(null, null));

        assertThat(antwort.pruefpunkt().ergebnis()).isNull();
        assertThat(antwort.statistik().bewertet()).isZero();
    }

    @Test
    @DisplayName("Ein Prüfpunkt, der nicht zu diesem Protokoll gehört, wird nicht gefunden")
    void fremderPruefpunkt() {
        when(pruefpunktRepository.findByIdAndProtokollId(99L, ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.pruefpunktBewerten(ID, 99L, new WartungPruefpunktErgebnisDto(WartungErgebnis.IO, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Unbekanntes Protokoll: 404")
    void unbekanntesProtokoll() {
        assertThatThrownBy(() -> service.laden(Long.MAX_VALUE))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── Unterschrift ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Eine Unterschrift ohne Namen wird abgelehnt")
    void unterschriftOhneName() {
        assertThatThrownBy(() -> service.unterschreiben(ID, new WartungUnterschriftDto(WartungUnterschriftDto.Rolle.TECHNIKER, PNG_URL, " ")))
                .hasMessageContaining("Namen");
        assertThat(protokoll.getUnterschriftTechniker()).isNull();
    }

    @Test
    @DisplayName("Ein kein-PNG-Bild wird abgelehnt und nichts gespeichert")
    void unterschriftKeinPng() {
        String svg = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg onload=alert(1)/>".getBytes());

        assertThatThrownBy(() -> service.unterschreiben(ID, new WartungUnterschriftDto(WartungUnterschriftDto.Rolle.KUNDE, svg, "Erika")))
                .hasMessageContaining("PNG");
        assertThat(protokoll.getUnterschriftKunde()).isNull();
    }

    @Test
    @DisplayName("Gültige Unterschrift wird für die richtige Rolle gespeichert, Bild null löscht sie samt Namen")
    void unterschriftSpeichernUndLoeschen() {
        service.unterschreiben(ID, new WartungUnterschriftDto(WartungUnterschriftDto.Rolle.KUNDE, PNG_URL, "  Erika Beispiel "));
        assertThat(protokoll.getUnterschriftKunde()).isEqualTo(PNG_URL);
        assertThat(protokoll.getUnterschriftKundeName()).isEqualTo("Erika Beispiel");
        assertThat(protokoll.getUnterschriftTechniker()).isNull();

        service.unterschreiben(ID, new WartungUnterschriftDto(WartungUnterschriftDto.Rolle.KUNDE, null, "egal"));
        assertThat(protokoll.getUnterschriftKunde()).isNull();
        assertThat(protokoll.getUnterschriftKundeName()).isNull();
    }

    // ── Suche ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Platzhalter und Escape-Zeichen im Suchtext werden maskiert")
    void likeMaskierung() {
        assertThat(WartungProtokollService.maskiereLike("100%")).isEqualTo("100!%");
        assertThat(WartungProtokollService.maskiereLike("a_b")).isEqualTo("a!_b");
        assertThat(WartungProtokollService.maskiereLike("wow!")).isEqualTo("wow!!");
        assertThat(WartungProtokollService.maskiereLike("'; DROP TABLE x; --")).isEqualTo("'; DROP TABLE x; --");
    }

    @Test
    @DisplayName("Datum von nach Datum bis wird mit einer Meldung abgelehnt")
    void datumsbereichVertauscht() {
        assertThatThrownBy(() -> service.suche(null, null, null, null, false,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1), null, Pageable.unpaged()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── Objekt des Protokolls ───────────────────────────────────────────────

    private org.example.kalkulationsprogramm.dto.wartung.WartungProtokollKopfDto kopfMitObjekt(Long objektId) {
        return new org.example.kalkulationsprogramm.dto.wartung.WartungProtokollKopfDto(
                "Jahreswartung", "Wartungsprotokoll", LocalDate.of(2026, 5, 10), 12, null, null, objektId, null, null);
    }

    private WartungObjekt objekt(Long id, String name, Kunde kunde, boolean aktiv) {
        WartungObjekt o = new WartungObjekt();
        o.setId(id);
        o.setKunde(kunde);
        o.setBezeichnung(name);
        o.setAktiv(aktiv);
        return o;
    }

    @Test
    @DisplayName("Ein Feldeinsatz ohne Wartung lässt sich einem Objekt des Kunden zuordnen, später wechseln und lösen")
    void objektZuordnenOhneWartung() {
        protokoll.setTuer(null);
        protokoll.setObjekt(null);
        WartungObjekt nord = objekt(5L, "Werk Nord", protokoll.getKunde(), true);
        WartungObjekt sued = objekt(6L, "Werk Süd", protokoll.getKunde(), true);
        when(objektRepository.findById(5L)).thenReturn(Optional.of(nord));
        when(objektRepository.findById(6L)).thenReturn(Optional.of(sued));

        service.kopfAendern(ID, kopfMitObjekt(5L));
        assertThat(protokoll.getObjekt()).isSameAs(nord);

        service.kopfAendern(ID, kopfMitObjekt(6L));
        assertThat(protokoll.getObjekt()).isSameAs(sued);

        service.kopfAendern(ID, kopfMitObjekt(null));
        assertThat(protokoll.getObjekt()).isNull();
    }

    @Test
    @DisplayName("Objekt eines anderen Kunden, deaktiviertes oder unbekanntes Objekt werden abgelehnt, das bisherige bleibt")
    void objektZuordnenFehler() {
        protokoll.setTuer(null);
        WartungObjekt bisher = protokoll.getObjekt();
        Kunde anderer = new Kunde();
        anderer.setId(2L);
        when(objektRepository.findById(7L)).thenReturn(Optional.of(objekt(7L, "Fremd", anderer, true)));
        when(objektRepository.findById(8L)).thenReturn(Optional.of(objekt(8L, "Alt", protokoll.getKunde(), false)));
        when(objektRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.kopfAendern(ID, kopfMitObjekt(7L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("nicht zu diesem Kunden");
                });
        assertThatThrownBy(() -> service.kopfAendern(ID, kopfMitObjekt(8L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getReason()).contains("nicht mehr aktiv"));
        assertThatThrownBy(() -> service.kopfAendern(ID, kopfMitObjekt(9L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(protokoll.getObjekt()).isSameAs(bisher);
    }

    @Test
    @DisplayName("Ein inzwischen deaktiviertes Objekt darf am Feldeinsatz stehen bleiben, auch beim Speichern der Angaben")
    void deaktiviertesObjektBleibtStehen() {
        protokoll.setTuer(null);
        protokoll.getObjekt().setAktiv(false);

        service.kopfAendern(ID, kopfMitObjekt(protokoll.getObjekt().getId()));

        assertThat(protokoll.getObjekt().getBezeichnung()).isEqualTo("Werk Nord");
    }

    @Test
    @DisplayName("Gehört der Feldeinsatz zu einer Wartung, ergibt sich das Objekt aus ihr: ein anderes wird mit 409 abgelehnt, die gleiche oder keine Angabe lässt es stehen")
    void objektBeiWartungFest() {
        WartungObjekt vonWartung = tuer.getObjekt();

        assertThatThrownBy(() -> service.kopfAendern(ID, kopfMitObjekt(6L)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("Tür 1.01").contains("Werk Nord");
                });
        service.kopfAendern(ID, kopfMitObjekt(5L));
        service.kopfAendern(ID, kopfMitObjekt(null));

        assertThat(protokoll.getObjekt()).isSameAs(vonWartung);
    }

    @Test
    @DisplayName("Ein abgeschlossenes Protokoll behält sein Objekt (409)")
    void objektBeiAbgeschlossenemProtokoll() {
        protokoll.setTuer(null);
        protokoll.setStatus(WartungStatus.ABGESCHLOSSEN);
        WartungObjekt bisher = protokoll.getObjekt();

        assertThatThrownBy(() -> service.kopfAendern(ID, kopfMitObjekt(null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(protokoll.getObjekt()).isSameAs(bisher);
    }

    // ── Protokollart mehrerer Wartungen auf einmal ──────────────────────────

    private WartungProtokoll mitStatus(Long id, String nummer, WartungStatus status, String typ) {
        WartungProtokoll p = new WartungProtokoll();
        p.setId(id);
        p.setNummer(nummer);
        p.setStatus(status);
        p.setProtokollTyp(typ);
        lenient().when(protokollRepository.findMitSperre(id)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    @DisplayName("Sammeländerung: offene und in Arbeit befindliche werden geändert, abgeschlossene und fehlende mit Grund übersprungen, doppelte Nummern zählen einmal")
    void protokollartSammelnAendern() {
        WartungProtokoll offen = mitStatus(1L, "WA-2026-1001", WartungStatus.OFFEN, "Wartungsprotokoll");
        WartungProtokoll inArbeit = mitStatus(2L, "WA-2026-1002", WartungStatus.IN_BEARBEITUNG, "Wartungsprotokoll");
        WartungProtokoll fertig = mitStatus(3L, "WA-2026-1003", WartungStatus.ABGESCHLOSSEN, "Wartungsprotokoll");
        WartungProtokoll schon = mitStatus(4L, "WA-2026-1004", WartungStatus.OFFEN, "Instandhaltungsprotokoll");
        lenient().when(protokollRepository.findMitSperre(99L)).thenReturn(Optional.empty());

        var e = service.protokollartSetzen(java.util.Arrays.asList(1L, 2L, 3L, 4L, 99L, 1L, null), " Instandhaltungsprotokoll ");

        assertThat(offen.getProtokollTyp()).isEqualTo("Instandhaltungsprotokoll");
        assertThat(inArbeit.getProtokollTyp()).isEqualTo("Instandhaltungsprotokoll");
        assertThat(fertig.getProtokollTyp()).isEqualTo("Wartungsprotokoll");
        assertThat(schon.getProtokollTyp()).isEqualTo("Instandhaltungsprotokoll");
        assertThat(e.geaendert()).isEqualTo(2);
        assertThat(e.uebersprungen()).hasSize(3);
        assertThat(e.uebersprungen().get(0).nummer()).isEqualTo("WA-2026-1003");
        assertThat(e.uebersprungen().get(0).grund()).contains("nicht ändern").contains("wieder öffnen");
        assertThat(e.uebersprungen().get(1).nummer()).isNull();
        assertThat(e.uebersprungen().get(1).grund()).contains("gibt es nicht");
        verify(protokollRepository).save(offen);
        verify(protokollRepository).save(inArbeit);
        verify(protokollRepository, never()).save(fertig);
        verify(protokollRepository, never()).save(schon);
    }

    @Test
    @DisplayName("Sammeländerung: unbekannte Protokollart und leere Auswahl werden abgelehnt, nichts wird geändert")
    void protokollartSammelnAblehnung() {
        WartungProtokoll offen = mitStatus(1L, "WA-2026-1001", WartungStatus.OFFEN, "Wartungsprotokoll");

        assertThatThrownBy(() -> service.protokollartSetzen(List.of(1L), "Fantasieprotokoll")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Möglich:");
        assertThatThrownBy(() -> service.protokollartSetzen(List.of(1L), null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.protokollartSetzen(List.of(), "Wartungsprotokoll")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("mindestens eine");
        assertThatThrownBy(() -> service.protokollartSetzen(null, "Wartungsprotokoll")).isInstanceOf(ResponseStatusException.class);
        assertThat(offen.getProtokollTyp()).isEqualTo("Wartungsprotokoll");
        verify(protokollRepository, never()).save(any());
    }
}
