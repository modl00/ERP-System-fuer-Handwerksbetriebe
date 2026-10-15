package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.BuchungsTyp;
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Zeitbuchung;
import org.example.kalkulationsprogramm.domain.ZeitkontoVersion;
import org.example.kalkulationsprogramm.repository.AbwesenheitRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.ZeitbuchungRepository;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.Aenderung;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.Eingabe;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.HinweisNoetigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Nur Dummy-Daten (Max Mustermann). */
@ExtendWith(MockitoExtension.class)
class ZeiterfassungNachtragServiceTest {

    @Mock private FunktionsRechteService rechte;
    @Mock private MitarbeiterRepository mitarbeiterRepository;
    @Mock private ZeitbuchungRepository zeitbuchungRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private ArbeitsgangRepository arbeitsgangRepository;
    @Mock private ArbeitsgangStundensatzRepository stundensatzRepository;
    @Mock private AbwesenheitRepository abwesenheitRepository;
    @Mock private MonatsSaldoService monatsSaldoService;
    @Mock private ZeitkontoService zeitkontoService;
    @Mock private ZeitbuchungAuditService auditService;

    private ZeiterfassungNachtragService service;
    private Mitarbeiter max;
    private Projekt halle;
    private Arbeitsgang montage;
    private ArbeitsgangStundensatz satz;
    private final LocalDate tag = LocalDate.now().minusDays(2);

    @BeforeEach
    void setUp() {
        service = new ZeiterfassungNachtragService(rechte, mitarbeiterRepository, zeitbuchungRepository, projektRepository, arbeitsgangRepository,
                stundensatzRepository, abwesenheitRepository, monatsSaldoService, zeitkontoService, auditService);
        max = new Mitarbeiter();
        max.setId(5L);
        max.setVorname("Max");
        max.setNachname("Mustermann");
        max.setFuehrtZeitkonto(true);
        max.setIstGeschaeftsfuehrer(false);
        halle = new Projekt();
        halle.setId(7L);
        halle.setBauvorhaben("Anbau Halle");
        montage = new Arbeitsgang();
        montage.setId(3L);
        montage.setBeschreibung("Montage");
        satz = new ArbeitsgangStundensatz();
        var l = org.mockito.Mockito.lenient();
        l.when(mitarbeiterRepository.findByLoginTokenAndAktivTrueForUpdate("tok")).thenReturn(Optional.of(max));
        l.when(rechte.verlangeFuerToken(eq("tok"), eq(Funktion.ZEITERFASSUNG), any())).thenReturn(max);
        l.when(rechte.darf(any(), eq(Funktion.ZEITERFASSUNG), any())).thenReturn(true);
        l.when(zeitkontoService.versionAm(anyLong(), any())).thenReturn(Optional.of(new ZeitkontoVersion()));
        l.when(monatsSaldoService.isMonatFestgeschrieben(anyLong(), anyInt(), anyInt())).thenReturn(false);
        l.when(projektRepository.findById(7L)).thenReturn(Optional.of(halle));
        l.when(arbeitsgangRepository.findById(3L)).thenReturn(Optional.of(montage));
        l.when(stundensatzRepository.findTopByArbeitsgangIdAndJahrOrderByIdDesc(3L, tag.getYear())).thenReturn(Optional.of(satz));
        l.when(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any())).thenReturn(List.of());
        l.when(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(anyLong(), any(), any())).thenReturn(List.of());
        l.when(zeitbuchungRepository.save(any(Zeitbuchung.class))).thenAnswer(a -> {
            Zeitbuchung z = a.getArgument(0);
            if (z.getId() == null) z.setId(100L);
            return z;
        });
    }

    private Eingabe arbeit(LocalTime start, LocalTime ende, Integer dauer) {
        return new Eingabe("tok", "ARBEIT", tag, start, ende, dauer, 7L, 3L, null, "  Geländer montiert  ", false, null);
    }

    private Zeitbuchung vorhanden(long id, LocalTime von, LocalTime bis, BuchungsTyp typ) {
        Zeitbuchung z = new Zeitbuchung();
        z.setId(id);
        z.setMitarbeiter(max);
        z.setTyp(typ);
        z.setProjekt(typ == BuchungsTyp.PAUSE ? null : halle);
        z.setStartZeit(tag.atTime(von));
        z.setEndeZeit(bis == null ? null : tag.atTime(bis));
        return z;
    }

    private void tagHat(Zeitbuchung... bs) {
        given(zeitbuchungRepository.findByMitarbeiterIdAndStartZeitBetween(anyLong(), any(), any())).willReturn(List.of(bs));
    }

    private static ResponseStatusException status(Throwable t) {
        return (ResponseStatusException) t;
    }

    // ============================================================ Erfassen

    @Test
    @DisplayName("Arbeit mit Start und Ende: gespeichert mit Stunden, Projekt, Arbeitsgang, Stundensatz, Quelle App und Protokoll „nachträglich erfasst“")
    void erfasseMitStartUndEnde() {
        var e = service.erfasse(arbeit(LocalTime.of(8, 0), LocalTime.of(10, 30), null));

        ArgumentCaptor<Zeitbuchung> z = ArgumentCaptor.forClass(Zeitbuchung.class);
        verify(zeitbuchungRepository).save(z.capture());
        Zeitbuchung b = z.getValue();
        assertThat(b.getStartZeit()).isEqualTo(tag.atTime(8, 0));
        assertThat(b.getEndeZeit()).isEqualTo(tag.atTime(10, 30));
        assertThat(b.getAnzahlInStunden()).isEqualByComparingTo(new BigDecimal("2.50"));
        assertThat(b.getProjekt()).isSameAs(halle);
        assertThat(b.getArbeitsgang()).isSameAs(montage);
        assertThat(b.getArbeitsgangStundensatz()).isSameAs(satz);
        assertThat(b.getNotiz()).isEqualTo("Geländer montiert");
        assertThat(b.getErfasstVia()).isEqualTo(ErfassungsQuelle.MOBILE_APP);
        assertThat(b.getErfasstVon()).isSameAs(max);
        verify(rechte).verlangeFuerToken("tok", Funktion.ZEITERFASSUNG, FunktionsAktion.ANLEGEN);
        ArgumentCaptor<String> grund = ArgumentCaptor.forClass(String.class);
        verify(auditService).protokolliereErstellung(eq(b), eq(max), eq(ErfassungsQuelle.MOBILE_APP), grund.capture());
        assertThat(grund.getValue()).contains("Nachträglich erfasst").contains("für den " + tag.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        verify(monatsSaldoService).invalidiereFuerDateTime(5L, b.getStartZeit());
        assertThat(e.buchung()).containsEntry("startMinuten", 480).containsEntry("endeMinuten", 630).containsEntry("dauerMinuten", 150).containsEntry("typ", "ARBEIT");
        assertThat(e.hinweise()).isEmpty();
    }

    @Test
    @DisplayName("Mit Dauer statt Ende wird das Ende berechnet")
    void erfasseMitDauer() {
        service.erfasse(arbeit(LocalTime.of(7, 30), null, 90));

        ArgumentCaptor<Zeitbuchung> z = ArgumentCaptor.forClass(Zeitbuchung.class);
        verify(zeitbuchungRepository).save(z.capture());
        assertThat(z.getValue().getEndeZeit()).isEqualTo(tag.atTime(9, 0));
        assertThat(z.getValue().getAnzahlInStunden()).isEqualByComparingTo("1.50");
    }

    @Test
    @DisplayName("Pause braucht kein Projekt und keinen Arbeitsgang; Arbeit ohne beides wird abgelehnt")
    void pauseUndArbeitOhneProjekt() {
        service.erfasse(new Eingabe("tok", "pause", tag, LocalTime.of(12, 0), null, 30, null, null, null, null, false, null));
        ArgumentCaptor<Zeitbuchung> z = ArgumentCaptor.forClass(Zeitbuchung.class);
        verify(zeitbuchungRepository).save(z.capture());
        assertThat(z.getValue().getTyp()).isEqualTo(BuchungsTyp.PAUSE);
        assertThat(z.getValue().getProjekt()).isNull();

        assertThatThrownBy(() -> service.erfasse(new Eingabe("tok", "ARBEIT", tag, LocalTime.of(8, 0), LocalTime.of(9, 0), null, null, 3L, null, null, false, null)))
                .hasMessageContaining("Projekt");
        assertThatThrownBy(() -> service.erfasse(new Eingabe("tok", "ARBEIT", tag, LocalTime.of(8, 0), LocalTime.of(9, 0), null, 7L, null, null, null, false, null)))
                .hasMessageContaining("Arbeitsgang");
        assertThatThrownBy(() -> service.erfasse(new Eingabe("tok", "FEIERABEND", tag, LocalTime.of(8, 0), LocalTime.of(9, 0), null, 7L, 3L, null, null, false, null)))
                .hasMessageContaining("unbekannt");
    }

    @Test
    @DisplayName("Zeitangaben: keine Zukunft, Ende nach Start, kein Tageswechsel, Ende oder Dauer nötig")
    void zeitangabenWerdenGeprueft() {
        assertThatThrownBy(() -> service.erfasse(new Eingabe("tok", "ARBEIT", LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0), null, 7L, 3L, null, null, false, null)))
                .hasMessageContaining("Zukunft");
        assertThatThrownBy(() -> service.erfasse(new Eingabe("tok", "ARBEIT", LocalDate.now(), LocalTime.of(0, 0), LocalTime.of(23, 59), null, 7L, 3L, null, null, false, null)))
                .hasMessageContaining("Zukunft");
        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(10, 0), LocalTime.of(9, 0), null))).hasMessageContaining("nach dem Start");
        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(20, 0), null, 300))).hasMessageContaining("Mitternacht");
        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(8, 0), null, 0))).hasMessageContaining("Dauer");
        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(8, 0), null, null))).hasMessageContaining("Ende oder die Dauer");
        assertThatThrownBy(() -> service.erfasse(arbeit(null, LocalTime.of(9, 0), null))).hasMessageContaining("Startzeit");
        verify(zeitbuchungRepository, never()).save(any());
    }

    @Test
    @DisplayName("Gesperrt: abgeschlossener Monat (mit Monatsname), ausgeschaltete Zeiterfassung, fehlende Arbeitszeit; Geschäftsführer brauchen keine hinterlegte Arbeitszeit")
    void sperren() {
        given(monatsSaldoService.isMonatFestgeschrieben(5L, tag.getYear(), tag.getMonthValue())).willReturn(true);
        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(8, 0), LocalTime.of(9, 0), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, t -> assertThat(t.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("abgeschlossen").hasMessageContaining("" + tag.getYear());
        given(monatsSaldoService.isMonatFestgeschrieben(5L, tag.getYear(), tag.getMonthValue())).willReturn(false);

        given(zeitkontoService.versionAm(5L, tag)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(8, 0), LocalTime.of(9, 0), null))).hasMessageContaining("keine Arbeitszeit hinterlegt");
        max.setIstGeschaeftsfuehrer(true);
        assertThat(service.erfasse(arbeit(LocalTime.of(8, 0), LocalTime.of(9, 0), null)).buchung()).isNotEmpty();

        max.setFuehrtZeitkonto(false);
        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(11, 0), LocalTime.of(12, 0), null))).hasMessageContaining("ausgeschaltet");
    }

    @Test
    @DisplayName("Überschneidung mit einer anderen Buchung wird abgelehnt, auch mit einer laufenden; Anschluss ohne Lücke ist erlaubt")
    void ueberschneidung() {
        tagHat(vorhanden(1L, LocalTime.of(8, 0), LocalTime.of(10, 0), BuchungsTyp.ARBEIT));

        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(9, 0), LocalTime.of(11, 0), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, t -> assertThat(t.getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("überschneidet").hasMessageContaining("08:00 bis 10:00").hasMessageContaining("Anbau Halle");
        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(7, 0), LocalTime.of(8, 30), null))).hasMessageContaining("überschneidet");
        assertThat(service.erfasse(arbeit(LocalTime.of(10, 0), LocalTime.of(12, 0), null)).buchung()).isNotEmpty();
        assertThat(service.erfasse(arbeit(LocalTime.of(6, 0), LocalTime.of(8, 0), null)).buchung()).isNotEmpty();

        tagHat(vorhanden(2L, LocalTime.of(12, 0), LocalTime.of(12, 30), BuchungsTyp.PAUSE));
        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(12, 15), LocalTime.of(13, 0), null))).hasMessageContaining("Pause");
    }

    @Test
    @DisplayName("Urlaub am selben Tag: erst Hinweis, mit Bestätigung wird gespeichert und der Hinweis kommt zurück")
    void abwesenheitBrauchtBestaetigung() {
        Abwesenheit urlaub = new Abwesenheit();
        urlaub.setTyp(AbwesenheitsTyp.URLAUB);
        given(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(5L, tag, tag)).willReturn(List.of(urlaub));

        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(8, 0), LocalTime.of(9, 0), null)))
                .isInstanceOfSatisfying(HinweisNoetigException.class, h -> assertThat(h.getHinweise()).singleElement().asString().contains("Urlaub"));
        verify(zeitbuchungRepository, never()).save(any());

        var e = service.erfasse(new Eingabe("tok", "ARBEIT", tag, LocalTime.of(8, 0), LocalTime.of(9, 0), null, 7L, 3L, null, null, true, null));

        assertThat(e.hinweise()).singleElement().asString().contains("Urlaub");
        verify(zeitbuchungRepository).save(any());
    }

    @Test
    @DisplayName("Sehr langer Tag (mehr als 10 Stunden Arbeit) braucht Bestätigung; Pausen zählen nicht mit")
    void langerTag() {
        tagHat(vorhanden(1L, LocalTime.of(5, 0), LocalTime.of(13, 0), BuchungsTyp.ARBEIT), vorhanden(2L, LocalTime.of(13, 0), LocalTime.of(14, 0), BuchungsTyp.PAUSE));

        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(14, 0), LocalTime.of(17, 0), null)))
                .isInstanceOfSatisfying(HinweisNoetigException.class, h -> assertThat(h.getMessage()).contains("11:00 Stunden").contains("mehr als 10"));
        assertThat(service.erfasse(arbeit(LocalTime.of(14, 0), LocalTime.of(15, 0), null)).hinweise()).isEmpty();   // 9 Stunden: in Ordnung
    }

    @Test
    @DisplayName("Derselbe Schlüssel zweimal (Wiederholung nach Verbindungsabbruch) legt nichts doppelt an; fremde Buchung wird nicht herausgegeben")
    void idempotenz() {
        Zeitbuchung schon = vorhanden(9L, LocalTime.of(8, 0), LocalTime.of(9, 0), BuchungsTyp.ARBEIT);
        given(zeitbuchungRepository.findByIdempotencyKey("key-1")).willReturn(Optional.of(schon));

        var e = service.erfasse(new Eingabe("tok", "ARBEIT", tag, LocalTime.of(8, 0), LocalTime.of(9, 0), null, 7L, 3L, null, null, false, "key-1"));

        assertThat(e.buchung()).containsEntry("id", 9L);
        verify(zeitbuchungRepository, never()).save(any());

        Mitarbeiter anderer = new Mitarbeiter();
        anderer.setId(99L);
        schon.setMitarbeiter(anderer);
        assertThatThrownBy(() -> service.erfasse(new Eingabe("tok", "ARBEIT", tag, LocalTime.of(8, 0), LocalTime.of(9, 0), null, 7L, 3L, null, null, false, "key-1")))
                .isInstanceOfSatisfying(ResponseStatusException.class, t -> assertThat(t.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("Ohne Recht „Zeit nachtragen, anlegen“ passiert nichts: weder Sperre noch Speichern")
    void ohneRecht() {
        given(rechte.verlangeFuerToken("tok", Funktion.ZEITERFASSUNG, FunktionsAktion.ANLEGEN))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung: Zeit nachtragen, Anlegen."));

        assertThatThrownBy(() -> service.erfasse(arbeit(LocalTime.of(8, 0), LocalTime.of(9, 0), null))).hasMessageContaining("Berechtigung");
        verifyNoInteractions(zeitbuchungRepository, mitarbeiterRepository);
    }

    // ============================================================ Ändern und Löschen

    private Aenderung aenderung(LocalTime start, LocalTime ende, String grund) {
        return new Aenderung("tok", null, start, ende, null, null, null, null, null, grund, false);
    }

    @Test
    @DisplayName("Ändern: neue Zeiten gelten, Version steigt, Protokoll mit Grund; die Buchung zählt bei der Überschneidungsprüfung nicht gegen sich selbst")
    void aendern() {
        Zeitbuchung b = vorhanden(1L, LocalTime.of(8, 0), LocalTime.of(10, 0), BuchungsTyp.ARBEIT);
        given(zeitbuchungRepository.findById(1L)).willReturn(Optional.of(b));
        tagHat(b);

        var e = service.aendere(1L, aenderung(LocalTime.of(8, 0), LocalTime.of(11, 0), "Zeit falsch eingetragen"));

        assertThat(b.getEndeZeit()).isEqualTo(tag.atTime(11, 0));
        assertThat(b.getAnzahlInStunden()).isEqualByComparingTo("3.00");
        assertThat(b.getVersion()).isEqualTo(2);
        verify(auditService).protokolliereAenderung(b, max, ErfassungsQuelle.MOBILE_APP, "Zeit falsch eingetragen");
        verify(rechte).verlangeFuerToken("tok", Funktion.ZEITERFASSUNG, FunktionsAktion.AENDERN);
        assertThat(e.buchung()).containsEntry("dauerMinuten", 180);
    }

    @Test
    @DisplayName("Ändern: nur Startzeit angegeben verschiebt die Buchung mit gleicher Dauer; Projekt und Arbeitsgang lassen sich wechseln")
    void aendernVerschiebtMitGleicherDauer() {
        Zeitbuchung b = vorhanden(1L, LocalTime.of(8, 0), LocalTime.of(10, 0), BuchungsTyp.ARBEIT);
        given(zeitbuchungRepository.findById(1L)).willReturn(Optional.of(b));
        tagHat(b);
        Projekt neu = new Projekt();
        neu.setId(8L);
        neu.setBauvorhaben("Carport");
        given(projektRepository.findById(8L)).willReturn(Optional.of(neu));

        service.aendere(1L, new Aenderung("tok", null, LocalTime.of(9, 0), null, null, 8L, null, null, null, "Falsches Projekt", false));

        assertThat(b.getStartZeit()).isEqualTo(tag.atTime(9, 0));
        assertThat(b.getEndeZeit()).isEqualTo(tag.atTime(11, 0));
        assertThat(b.getProjekt()).isSameAs(neu);
    }

    @Test
    @DisplayName("Ändern: Grund ist Pflicht, fremde Buchungen und laufende Buchungen sind tabu, Überschneidung mit anderen wird abgelehnt")
    void aendernAblehnungen() {
        Zeitbuchung b = vorhanden(1L, LocalTime.of(8, 0), LocalTime.of(10, 0), BuchungsTyp.ARBEIT);
        Zeitbuchung andere = vorhanden(2L, LocalTime.of(10, 0), LocalTime.of(12, 0), BuchungsTyp.ARBEIT);
        given(zeitbuchungRepository.findById(1L)).willReturn(Optional.of(b));
        tagHat(b, andere);

        assertThatThrownBy(() -> service.aendere(1L, aenderung(LocalTime.of(8, 0), LocalTime.of(9, 0), " "))).hasMessageContaining("warum");
        assertThatThrownBy(() -> service.aendere(1L, aenderung(LocalTime.of(8, 0), LocalTime.of(11, 0), "Korrektur"))).hasMessageContaining("überschneidet");

        b.setEndeZeit(null);
        assertThatThrownBy(() -> service.aendere(1L, aenderung(LocalTime.of(8, 0), LocalTime.of(9, 0), "Korrektur"))).hasMessageContaining("läuft noch");

        Mitarbeiter anderer = new Mitarbeiter();
        anderer.setId(99L);
        b.setMitarbeiter(anderer);
        assertThatThrownBy(() -> service.aendere(1L, aenderung(LocalTime.of(8, 0), LocalTime.of(9, 0), "Korrektur"))).hasMessageContaining("nicht zu Ihrem Zugang");

        given(zeitbuchungRepository.findById(404L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.aendere(404L, aenderung(LocalTime.of(8, 0), LocalTime.of(9, 0), "Korrektur"))).hasMessageContaining("gibt es nicht");
        verify(auditService, never()).protokolliereAenderung(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Ändern in einem abgeschlossenen Monat ist gesperrt")
    void aendernImAbgeschlossenenMonat() {
        Zeitbuchung b = vorhanden(1L, LocalTime.of(8, 0), LocalTime.of(10, 0), BuchungsTyp.ARBEIT);
        given(zeitbuchungRepository.findById(1L)).willReturn(Optional.of(b));
        given(monatsSaldoService.isMonatFestgeschrieben(5L, tag.getYear(), tag.getMonthValue())).willReturn(true);

        assertThatThrownBy(() -> service.aendere(1L, aenderung(LocalTime.of(8, 0), LocalTime.of(9, 0), "Korrektur"))).hasMessageContaining("abgeschlossen");
    }

    @Test
    @DisplayName("Ändern an einem schon langen Tag fragt nur nach, wenn die Buchung länger wird; eine kleine Korrektur bleibt ohne Rückfrage")
    void aendernAmLangenTag() {
        Zeitbuchung b = vorhanden(1L, LocalTime.of(5, 0), LocalTime.of(14, 0), BuchungsTyp.ARBEIT);
        Zeitbuchung andere = vorhanden(2L, LocalTime.of(14, 0), LocalTime.of(16, 0), BuchungsTyp.ARBEIT);
        given(zeitbuchungRepository.findById(1L)).willReturn(Optional.of(b));
        tagHat(b, andere);

        assertThatThrownBy(() -> service.aendere(1L, aenderung(LocalTime.of(4, 0), LocalTime.of(14, 0), "Früher angefangen")))
                .isInstanceOfSatisfying(HinweisNoetigException.class, h -> assertThat(h.getMessage()).contains("mehr als 10"));
        var ohneRueckfrage = service.aendere(1L, new Aenderung("tok", null, LocalTime.of(5, 0), LocalTime.of(13, 30), null, null, null, null, "Kommentar", "Ende korrigiert", false));
        assertThat(ohneRueckfrage.hinweise()).isEmpty();
        assertThat(b.getEndeZeit()).isEqualTo(tag.atTime(13, 30));
    }

    @Test
    @DisplayName("Ändern fragt nach Urlaub nur, wenn die Buchung auf einen anderen Tag wandert")
    void aendernMitAbwesenheit() {
        Abwesenheit urlaub = new Abwesenheit();
        urlaub.setTyp(AbwesenheitsTyp.URLAUB);
        org.mockito.Mockito.lenient().when(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(5L, tag, tag)).thenReturn(List.of(urlaub));
        Zeitbuchung b = vorhanden(1L, LocalTime.of(8, 0), LocalTime.of(10, 0), BuchungsTyp.ARBEIT);
        given(zeitbuchungRepository.findById(1L)).willReturn(Optional.of(b));
        tagHat(b);

        assertThat(service.aendere(1L, aenderung(LocalTime.of(8, 0), LocalTime.of(9, 30), "Kürzer")).hinweise()).isEmpty();
        verify(abwesenheitRepository, never()).findByMitarbeiterIdAndDatumBetween(5L, tag, tag);

        LocalDate anderer = tag.plusDays(1);
        given(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(5L, anderer, anderer)).willReturn(List.of(urlaub));
        given(zeitkontoService.versionAm(5L, anderer)).willReturn(Optional.of(new ZeitkontoVersion()));
        assertThatThrownBy(() -> service.aendere(1L, new Aenderung("tok", anderer, LocalTime.of(8, 0), LocalTime.of(9, 30), null, null, null, null, null, "Anderer Tag", false)))
                .isInstanceOf(HinweisNoetigException.class);
    }

    @Test
    @DisplayName("Löschen ist ein Storno: Grund Pflicht, Protokoll, dann entfernt; Recht „löschen“ wird geprüft")
    void stornieren() {
        Zeitbuchung b = vorhanden(1L, LocalTime.of(8, 0), LocalTime.of(10, 0), BuchungsTyp.ARBEIT);
        given(zeitbuchungRepository.findById(1L)).willReturn(Optional.of(b));

        assertThatThrownBy(() -> service.storniere("tok", 1L, "")).hasMessageContaining("warum");
        verify(zeitbuchungRepository, never()).deleteById(anyLong());

        service.storniere("tok", 1L, "Doppelt eingetragen");

        verify(rechte, org.mockito.Mockito.times(2)).verlangeFuerToken("tok", Funktion.ZEITERFASSUNG, FunktionsAktion.LOESCHEN);
        verify(auditService).protokolliereStorno(b, max, ErfassungsQuelle.MOBILE_APP, "Doppelt eingetragen");
        verify(zeitbuchungRepository).deleteById(1L);
        verify(monatsSaldoService).invalidiereFuerDateTime(5L, tag.atTime(8, 0));
    }

    @Test
    @DisplayName("Löschen: laufende, fremde und abgeschlossene Buchungen bleiben")
    void stornierenAblehnungen() {
        Zeitbuchung b = vorhanden(1L, LocalTime.of(8, 0), null, BuchungsTyp.ARBEIT);
        given(zeitbuchungRepository.findById(1L)).willReturn(Optional.of(b));
        assertThatThrownBy(() -> service.storniere("tok", 1L, "Irrtum")).hasMessageContaining("läuft noch");

        b.setEndeZeit(tag.atTime(9, 0));
        given(monatsSaldoService.isMonatFestgeschrieben(5L, tag.getYear(), tag.getMonthValue())).willReturn(true);
        assertThatThrownBy(() -> service.storniere("tok", 1L, "Irrtum")).hasMessageContaining("abgeschlossen");
        verify(zeitbuchungRepository, never()).deleteById(anyLong());
    }

    // ============================================================ Status

    @Test
    @DisplayName("Status eines Tages: Sperre mit Grund, Abwesenheiten, Arbeitsminuten und die Rechte des Mitarbeiters")
    void status() {
        Abwesenheit krank = new Abwesenheit();
        krank.setTyp(AbwesenheitsTyp.KRANKHEIT);
        given(abwesenheitRepository.findByMitarbeiterIdAndDatumBetween(5L, tag, tag)).willReturn(List.of(krank));
        tagHat(vorhanden(1L, LocalTime.of(8, 0), LocalTime.of(10, 0), BuchungsTyp.ARBEIT), vorhanden(2L, LocalTime.of(10, 0), LocalTime.of(10, 30), BuchungsTyp.PAUSE));
        given(rechte.darf(max, Funktion.ZEITERFASSUNG, FunktionsAktion.LOESCHEN)).willReturn(false);

        Map<String, Object> s = service.status("tok", tag);

        assertThat(s).containsEntry("gesperrt", false).containsEntry("zukunft", false).containsEntry("arbeitMinuten", 120L)
                .containsEntry("darfAnlegen", true).containsEntry("darfLoeschen", false);
        assertThat(s.get("abwesenheiten")).isEqualTo(List.of("Krankheit"));

        given(monatsSaldoService.isMonatFestgeschrieben(5L, tag.getYear(), tag.getMonthValue())).willReturn(true);
        Map<String, Object> zu = service.status("tok", tag);
        assertThat(zu).containsEntry("gesperrt", true);
        assertThat((String) zu.get("sperrgrund")).contains("abgeschlossen");
        assertThat(service.status("tok", LocalDate.now().plusDays(3))).containsEntry("zukunft", true);
        assertThatThrownBy(() -> service.status("tok", null)).hasMessageContaining("Datum");
    }
}
