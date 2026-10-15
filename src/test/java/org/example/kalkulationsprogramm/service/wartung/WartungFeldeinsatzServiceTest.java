package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungMitarbeiterUnterschrift;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulAuswahlDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollAnlegenDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktNeuDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungPruefpunktTextDto;
import org.example.kalkulationsprogramm.mapper.wartung.WartungMapper;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektDokumentRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungMitarbeiterUnterschriftRepository;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Feldeinsatz: neue Prüfung (auch als Folgeprüfung aus einer früheren), Projekt der Wartung, Unterschrift des
 * Mitarbeiters und Prüfpunkte im laufenden Feldeinsatz. Wichtig ist überall: Eine abgeschlossene Prüfung wird nie verändert.
 */
@ExtendWith(MockitoExtension.class)
class WartungFeldeinsatzServiceTest {

    private static final long ID = 42L;
    private static final byte[] PNG = java.nio.ByteBuffer.allocate(33)
            .put(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})
            .putInt(13).put(new byte[] {'I', 'H', 'D', 'R'}).putInt(360).putInt(120).put(new byte[] {8, 6, 0, 0, 0}).array();
    private static final String PNG_URL = WartungUnterschriftPruefer.PRAEFIX + java.util.Base64.getEncoder().encodeToString(PNG);

    @Mock private WartungProtokollRepository protokollRepository;
    @Mock private WartungProtokollAbschnittRepository abschnittRepository;
    @Mock private WartungProtokollPruefpunktRepository pruefpunktRepository;
    @Mock private WartungProtokollDatenfeldRepository datenfeldRepository;
    @Mock private WartungObjektRepository objektRepository;
    @Mock private WartungTuerRepository tuerRepository;
    @Mock private KundeRepository kundeRepository;
    @Mock private MitarbeiterRepository mitarbeiterRepository;
    @Mock private ProjektRepository projektRepository;
    @Mock private WartungModulAuswahlPruefer auswahlPruefer;
    @Mock private WartungInhaltKopierer inhaltKopierer;
    @Mock private WartungNummernService nummernService;
    @Mock private WartungMitarbeiterUnterschriftRepository unterschriftRepository;
    @Mock private ProjektDokumentRepository projektDokumentRepository;
    @Spy private WartungMapper mapper = new WartungMapper();

    @InjectMocks private WartungProtokollService service;

    private Kunde kunde;
    private WartungObjekt objekt;
    private WartungTuer tuer;
    private WartungProtokoll vorlage;
    private Mitarbeiter techniker;

    @BeforeEach
    void setUp() {
        kunde = new Kunde();
        kunde.setId(1L);
        kunde.setName("Max Mustermann GmbH");
        objekt = new WartungObjekt();
        objekt.setId(5L);
        objekt.setKunde(kunde);
        objekt.setBezeichnung("Werk Nord");
        tuer = new WartungTuer();
        tuer.setId(9L);
        tuer.setObjekt(objekt);
        tuer.setBezeichnung("Heizungsanlage");
        techniker = new Mitarbeiter();
        techniker.setId(3L);
        techniker.setVorname("Erika");
        techniker.setNachname("Beispiel");
        techniker.setAktiv(true);

        vorlage = new WartungProtokoll();
        vorlage.setId(5L);
        vorlage.setNummer("WA-2026-1001");
        vorlage.setKunde(kunde);
        vorlage.setObjekt(objekt);
        vorlage.setTuer(tuer);
        vorlage.setDatum(LocalDate.of(2026, 4, 1));
        vorlage.setIntervallMonate(6);
        vorlage.setStatus(WartungStatus.ABGESCHLOSSEN);
        vorlage.setAbgeschlossenAm(LocalDateTime.of(2026, 4, 1, 12, 0));
        vorlage.setAnsprechpartnerName("Hausmeister Beispiel");
        vorlage.getModule().add(new WartungProtokollModul(1L, "m_heizung", "Heizungsanlage", null, null));

        lenient().when(kundeRepository.findById(1L)).thenReturn(Optional.of(kunde));
        lenient().when(tuerRepository.findMitSperre(9L)).thenReturn(Optional.of(tuer));
        lenient().when(protokollRepository.findById(5L)).thenReturn(Optional.of(vorlage));
        lenient().when(mitarbeiterRepository.findById(3L)).thenReturn(Optional.of(techniker));
        lenient().when(nummernService.naechsteNummer(any())).thenReturn("WA-2026-2001");
        lenient().when(protokollRepository.saveAndFlush(any(WartungProtokoll.class))).thenAnswer(a -> {
            WartungProtokoll p = a.getArgument(0);
            if (p.getId() == null) {
                p.setId(77L);
            }
            return p;
        });
        lenient().when(protokollRepository.save(any(WartungProtokoll.class))).thenAnswer(a -> a.getArgument(0));
    }

    private WartungProtokollAnlegenDto anlegen(Long projektId, Long technikerId, Long vorlageId, Boolean werte,
                                               List<WartungModulAuswahlDto> module) {
        return new WartungProtokollAnlegenDto(1L, null, 9L, projektId, technikerId, null, LocalDate.of(2026, 10, 1), null,
                module, null, null, vorlageId, werte);
    }

    private WartungProtokoll gespeichert() {
        ArgumentCaptor<WartungProtokoll> captor = ArgumentCaptor.forClass(WartungProtokoll.class);
        verify(protokollRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private static Projekt projekt(Long id, Kunde kunde) {
        Projekt p = new Projekt();
        p.setId(id);
        p.setBauvorhaben("Neubau Halle");
        p.setKundenId(kunde);
        return p;
    }

    // ── Folgeprüfung aus einer Vorlage ──────────────────────────────────────

    @Test
    @DisplayName("Folgeprüfung: Inhalt und Werte kommen aus der früheren Prüfung, die Vorlage wird nicht verändert")
    void folgepruefungKopiertDieVorlage() {
        WartungProtokollDto ergebnis = service.anlegen(anlegen(null, null, 5L, null, null));

        WartungProtokoll neu = gespeichert();
        assertThat(neu.getVorlageProtokollId()).isEqualTo(5L);
        assertThat(neu.getIntervallMonate()).isEqualTo(6);
        assertThat(neu.getAnsprechpartnerName()).isEqualTo("Hausmeister Beispiel");
        assertThat(neu.getModule()).extracting(WartungProtokollModul::getModulKey).containsExactly("m_heizung");
        assertThat(neu.getStatus()).isEqualTo(WartungStatus.OFFEN);
        assertThat(neu.getAbgeschlossenAm()).isNull();
        verify(inhaltKopierer).kopiereAusProtokoll(5L, 77L, true);
        verify(inhaltKopierer, never()).kopiere(anyLong(), any(), any());
        // Die Vorlage bleibt, wie sie war
        assertThat(vorlage.getStatus()).isEqualTo(WartungStatus.ABGESCHLOSSEN);
        assertThat(vorlage.getAbgeschlossenAm()).isEqualTo(LocalDateTime.of(2026, 4, 1, 12, 0));
        verify(protokollRepository, never()).save(vorlage);
        assertThat(ergebnis.vorlageProtokollId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("Folgeprüfung ohne Werte: nur die Struktur wird übernommen, wenn der Nutzer es so wählt")
    void folgepruefungOhneWerte() {
        service.anlegen(anlegen(null, null, 5L, false, null));

        verify(inhaltKopierer).kopiereAusProtokoll(5L, 77L, false);
    }

    @Test
    @DisplayName("Eine Vorlage einer anderen Wartung, eine nicht abgeschlossene oder eine unbekannte wird abgelehnt")
    void vorlagePruefungen() {
        WartungTuer andere = new WartungTuer();
        andere.setId(99L);
        vorlage.setTuer(andere);
        assertThatThrownBy(() -> service.anlegen(anlegen(null, null, 5L, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("anderen Wartung");
                });

        vorlage.setTuer(tuer);
        vorlage.setAbgeschlossenAm(null);
        vorlage.setStatus(WartungStatus.IN_BEARBEITUNG);
        assertThatThrownBy(() -> service.anlegen(anlegen(null, null, 5L, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getReason()).contains("abgeschlossene Prüfung"));

        assertThatThrownBy(() -> service.anlegen(anlegen(null, null, 404L, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(protokollRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Ohne Vorlage braucht ein Feldeinsatz mindestens ein Modul")
    void ohneVorlageBrauchtModule() {
        assertThatThrownBy(() -> service.anlegen(anlegen(null, null, null, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).isEqualTo("Bitte mindestens ein Modul wählen.");
                });
        assertThatThrownBy(() -> service.anlegen(anlegen(null, null, null, null, List.of())))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ── Projekt ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Das Projekt der Wartung wird in den neuen Feldeinsatz übernommen")
    void projektWirdVonDerWartungGeerbt() {
        tuer.setProjekt(projekt(7L, kunde));

        service.anlegen(anlegen(null, null, 5L, null, null));

        assertThat(gespeichert().getProjekt().getId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Ein ausdrücklich gewähltes Projekt hat Vorrang und ändert die Wartung nicht")
    void gewaehltesProjektHatVorrang() {
        tuer.setProjekt(projekt(7L, kunde));
        when(projektRepository.findById(8L)).thenReturn(Optional.of(projekt(8L, kunde)));

        service.anlegen(anlegen(8L, null, 5L, null, null));

        assertThat(gespeichert().getProjekt().getId()).isEqualTo(8L);
        assertThat(tuer.getProjekt().getId()).isEqualTo(7L);
        verify(tuerRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ein Projekt eines anderen Kunden wird abgelehnt, ohne Projekt bleibt es leer")
    void projektDesFalschenKunden() {
        Kunde anderer = new Kunde();
        anderer.setId(2L);
        when(projektRepository.findById(8L)).thenReturn(Optional.of(projekt(8L, anderer)));
        assertThatThrownBy(() -> service.anlegen(anlegen(8L, null, 5L, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getReason()).contains("anderen Kunden"));

        service.anlegen(anlegen(null, null, 5L, null, null));
        assertThat(gespeichert().getProjekt()).isNull();
    }

    // ── Kein Doppelstart ────────────────────────────────────────────────────

    @Test
    @DisplayName("Läuft für die Wartung schon ein Feldeinsatz, gibt es 409 mit seiner Nummer; die Wartung wird dafür gesperrt")
    void keinVersehentlicherZweiterFeldeinsatz() {
        WartungProtokoll offen = new WartungProtokoll();
        offen.setId(60L);
        offen.setNummer("WA-2026-1999");
        offen.setStatus(WartungStatus.IN_BEARBEITUNG);
        when(protokollRepository.findFirstByTuerIdAndStatusInOrderByIdDesc(eq(9L), any())).thenReturn(Optional.of(offen));

        assertThatThrownBy(() -> service.anlegen(anlegen(null, null, 5L, null, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("WA-2026-1999");
                });
        verify(protokollRepository, never()).saveAndFlush(any());
        verify(tuerRepository).findMitSperre(9L);
    }

    // ── Unterschrift des Mitarbeiters ───────────────────────────────────────

    private WartungMitarbeiterUnterschrift gespeicherteUnterschrift() {
        WartungMitarbeiterUnterschrift u = new WartungMitarbeiterUnterschrift();
        u.setMitarbeiterId(3L);
        u.setBild(PNG_URL);
        u.setName("Erika Beispiel");
        return u;
    }

    @Test
    @DisplayName("Hat der Techniker eine Unterschrift gespeichert, kommt sie als Kopie in den neuen Feldeinsatz")
    void gespeicherteUnterschriftWirdKopiert() {
        when(unterschriftRepository.findById(3L)).thenReturn(Optional.of(gespeicherteUnterschrift()));

        service.anlegen(anlegen(null, 3L, 5L, null, null), 3L);

        WartungProtokoll neu = gespeichert();
        assertThat(neu.getUnterschriftTechniker()).isEqualTo(PNG_URL);
        assertThat(neu.getUnterschriftTechnikerName()).isEqualTo("Erika Beispiel");
        assertThat(neu.getUnterschriftKunde()).isNull();
    }

    @Test
    @DisplayName("Wählt das Büro einen Kollegen als Techniker, kommt dessen gemerkte Unterschrift nicht von allein ins Protokoll")
    void fremdeUnterschriftWirdNichtUebernommen() {
        lenient().when(unterschriftRepository.findById(3L)).thenReturn(Optional.of(gespeicherteUnterschrift()));

        service.anlegen(anlegen(null, 3L, 5L, null, null), 8L);

        assertThat(gespeichert().getUnterschriftTechniker()).isNull();
        verify(unterschriftRepository, never()).findById(3L);
    }

    @Test
    @DisplayName("Ohne Mitarbeiter-Zuordnung des Aufrufers (reines Admin-Konto) wird nichts übernommen")
    void ohneAufruferKeineUebernahme() {
        lenient().when(unterschriftRepository.findById(3L)).thenReturn(Optional.of(gespeicherteUnterschrift()));

        service.anlegen(anlegen(null, 3L, 5L, null, null));

        assertThat(gespeichert().getUnterschriftTechniker()).isNull();
    }

    @Test
    @DisplayName("Ohne gespeicherte Unterschrift oder ohne Techniker bleibt das Feld leer")
    void ohneGespeicherteUnterschrift() {
        service.anlegen(anlegen(null, 3L, 5L, null, null));

        assertThat(gespeichert().getUnterschriftTechniker()).isNull();
    }

    @Test
    @DisplayName("Die gespeicherte Unterschrift lässt sich in einen offenen Feldeinsatz einsetzen")
    void gespeicherteUnterschriftVerwenden() {
        WartungProtokoll offen = offenesProtokoll();
        when(unterschriftRepository.findById(3L)).thenReturn(Optional.of(gespeicherteUnterschrift()));

        WartungProtokollDto dto = service.gespeicherteUnterschriftVerwenden(ID, 3L);

        assertThat(offen.getUnterschriftTechniker()).isEqualTo(PNG_URL);
        assertThat(offen.getUnterschriftTechnikerName()).isEqualTo("Erika Beispiel");
        assertThat(offen.getStatus()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
        assertThat(dto.unterschriftTechniker().name()).isEqualTo("Erika Beispiel");
    }

    @Test
    @DisplayName("Die gespeicherte Unterschrift lässt sich nicht in den Feldeinsatz eines anderen Mitarbeiters einsetzen")
    void fremdenFeldeinsatzNichtUnterschreiben() {
        WartungProtokoll offen = offenesProtokoll();
        Mitarbeiter anderer = new Mitarbeiter();
        anderer.setId(4L);
        anderer.setVorname("Erika");
        anderer.setNachname("Beispiel");
        offen.setTechniker(anderer);

        assertThatThrownBy(() -> service.gespeicherteUnterschriftVerwenden(ID, 3L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("Erika Beispiel");
                });
        assertThat(offen.getUnterschriftTechniker()).isNull();
        verify(unterschriftRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Gespeicherte Unterschrift einsetzen: ohne Mitarbeiter 409, ohne gespeicherte 404, im abgeschlossenen Protokoll 409")
    void gespeicherteUnterschriftFehler() {
        WartungProtokoll offen = offenesProtokoll();
        assertThatThrownBy(() -> service.gespeicherteUnterschriftVerwenden(ID, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.gespeicherteUnterschriftVerwenden(ID, 3L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        offen.setStatus(WartungStatus.ABGESCHLOSSEN);
        assertThatThrownBy(() -> service.gespeicherteUnterschriftVerwenden(ID, 3L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(offen.getUnterschriftTechniker()).isNull();
    }

    // ── Prüfpunkte im laufenden Feldeinsatz ─────────────────────────────────

    private WartungProtokoll offenesProtokoll() {
        WartungProtokoll p = new WartungProtokoll();
        p.setId(ID);
        p.setNummer("WA-2026-2001");
        p.setKunde(kunde);
        p.setObjekt(objekt);
        p.setTuer(tuer);
        p.setDatum(LocalDate.of(2026, 10, 1));
        p.setStatus(WartungStatus.OFFEN);
        lenient().when(protokollRepository.findMitSperre(ID)).thenReturn(Optional.of(p));
        lenient().when(protokollRepository.findById(ID)).thenReturn(Optional.of(p));
        return p;
    }

    private final List<WartungProtokollAbschnitt> abschnitte = new ArrayList<>();
    private final List<WartungProtokollPruefpunkt> punkte = new ArrayList<>();

    private void bereiteAbschnitteVor() {
        abschnitte.clear();
        punkte.clear();
        WartungProtokollAbschnitt a = abschnitt(10L, WartungBereich.PRUEFPUNKTE, "Prüfpunkte Heizung", 1);
        abschnitte.add(a);
        punkte.add(punkt(100L, 10L, "Druck prüfen", 1, WartungErgebnis.IO));
        punkte.add(punkt(101L, 10L, "Sicherheitsventil", 2, null));
        lenient().when(abschnittRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenAnswer(x -> List.copyOf(abschnitte));
        lenient().when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenAnswer(x -> List.copyOf(punkte));
        lenient().when(pruefpunktRepository.findByIdAndProtokollId(anyLong(), anyLong())).thenAnswer(x ->
                punkte.stream().filter(p -> p.getId().equals(x.getArgument(0))).findFirst().filter(p -> p.getProtokollId().equals(x.getArgument(1))));
        lenient().when(abschnittRepository.save(any(WartungProtokollAbschnitt.class))).thenAnswer(x -> {
            WartungProtokollAbschnitt neu = x.getArgument(0);
            if (neu.getId() == null) {
                neu.setId(500L);
                abschnitte.add(neu);
            }
            return neu;
        });
        lenient().when(pruefpunktRepository.save(any(WartungProtokollPruefpunkt.class))).thenAnswer(x -> {
            WartungProtokollPruefpunkt neu = x.getArgument(0);
            if (neu.getId() == null) {
                neu.setId(900L);
                punkte.add(neu);
            }
            return neu;
        });
        lenient().doAnswer(x -> { punkte.remove(x.getArgument(0)); return null; }).when(pruefpunktRepository).delete(any(WartungProtokollPruefpunkt.class));
        lenient().doAnswer(x -> { abschnitte.removeIf(ab -> ab.getId().equals(x.getArgument(0))); return null; }).when(abschnittRepository).deleteById(anyLong());
    }

    private static WartungProtokollAbschnitt abschnitt(Long id, WartungBereich bereich, String label, int sortierung) {
        WartungProtokollAbschnitt a = new WartungProtokollAbschnitt();
        a.setId(id);
        a.setProtokollId(ID);
        a.setBereich(bereich);
        a.setLabel(label);
        a.setSortierung(sortierung);
        return a;
    }

    private static WartungProtokollPruefpunkt punkt(Long id, Long abschnittId, String label, int sortierung, WartungErgebnis ergebnis) {
        WartungProtokollPruefpunkt p = new WartungProtokollPruefpunkt();
        p.setId(id);
        p.setProtokollId(ID);
        p.setAbschnittId(abschnittId);
        p.setLabel(label);
        p.setSortierung(sortierung);
        p.setErgebnis(ergebnis);
        return p;
    }

    @Test
    @DisplayName("Ein ergänzter Prüfpunkt landet im gewählten Abschnitt, am Ende, und ändert nur dieses Protokoll")
    void pruefpunktHinzufuegenInAbschnitt() {
        WartungProtokoll offen = offenesProtokoll();
        bereiteAbschnitteVor();

        service.pruefpunktHinzufuegen(ID, new WartungPruefpunktNeuDto(10L, "  Abgasmessung  "));

        WartungProtokollPruefpunkt neu = punkte.get(punkte.size() - 1);
        assertThat(neu.getLabel()).isEqualTo("Abgasmessung");
        assertThat(neu.getAbschnittId()).isEqualTo(10L);
        assertThat(neu.getSortierung()).isEqualTo(3);
        assertThat(neu.getErgebnis()).isNull();
        assertThat(offen.getStatus()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
        assertThat(abschnitte).hasSize(1);
    }

    @Test
    @DisplayName("Ohne Abschnitt entsteht einmal 'Zusätzliche Prüfpunkte' und wird danach wiederverwendet")
    void pruefpunktHinzufuegenOhneAbschnitt() {
        offenesProtokoll();
        bereiteAbschnitteVor();

        service.pruefpunktHinzufuegen(ID, new WartungPruefpunktNeuDto(null, "Erster"));
        service.pruefpunktHinzufuegen(ID, new WartungPruefpunktNeuDto(null, "Zweiter"));

        assertThat(abschnitte).extracting(WartungProtokollAbschnitt::getLabel).containsExactly("Prüfpunkte Heizung", "Zusätzliche Prüfpunkte");
        assertThat(abschnitte.get(1).getSortierung()).isEqualTo(2);
        assertThat(punkte.stream().filter(p -> p.getAbschnittId().equals(500L))).hasSize(2);
    }

    @Test
    @DisplayName("Ein Abschnitt, der nicht zu diesem Feldeinsatz gehört oder keine Prüfpunkte hat, wird abgelehnt")
    void pruefpunktHinzufuegenFalscherAbschnitt() {
        offenesProtokoll();
        bereiteAbschnitteVor();
        abschnitte.add(abschnitt(11L, WartungBereich.DATENFELDER, "Daten", 2));

        assertThatThrownBy(() -> service.pruefpunktHinzufuegen(ID, new WartungPruefpunktNeuDto(999L, "X")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.pruefpunktHinzufuegen(ID, new WartungPruefpunktNeuDto(11L, "X")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(punkte).hasSize(2);
    }

    @Test
    @DisplayName("Prüfpunkte lassen sich nur im offenen Feldeinsatz ergänzen, ändern und löschen, nicht im abgeschlossenen")
    void abgeschlossenIstGesperrt() {
        WartungProtokoll p = offenesProtokoll();
        bereiteAbschnitteVor();
        p.setStatus(WartungStatus.ABGESCHLOSSEN);

        assertThatThrownBy(() -> service.pruefpunktHinzufuegen(ID, new WartungPruefpunktNeuDto(10L, "X")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.pruefpunktText(ID, 100L, new WartungPruefpunktTextDto("Neu")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.pruefpunktLoeschen(ID, 100L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(punkte).hasSize(2);
        assertThat(punkte.get(0).getLabel()).isEqualTo("Druck prüfen");
    }

    @Test
    @DisplayName("Ein Prüfpunkt lässt sich umformulieren, ohne dass sein Ergebnis verloren geht")
    void pruefpunktText() {
        offenesProtokoll();
        bereiteAbschnitteVor();

        service.pruefpunktText(ID, 100L, new WartungPruefpunktTextDto("  Wasserdruck prüfen "));

        assertThat(punkte.get(0).getLabel()).isEqualTo("Wasserdruck prüfen");
        assertThat(punkte.get(0).getErgebnis()).isEqualTo(WartungErgebnis.IO);
        assertThatThrownBy(() -> service.pruefpunktText(ID, 12345L, new WartungPruefpunktTextDto("X")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    @DisplayName("Löschen entfernt den Prüfpunkt; ein dadurch leerer Abschnitt entfällt, ein Abschnitt mit Rest bleibt")
    void pruefpunktLoeschen() {
        offenesProtokoll();
        bereiteAbschnitteVor();

        service.pruefpunktLoeschen(ID, 100L);
        assertThat(punkte).extracting(WartungProtokollPruefpunkt::getId).containsExactly(101L);
        assertThat(abschnitte).hasSize(1);

        service.pruefpunktLoeschen(ID, 101L);
        assertThat(punkte).isEmpty();
        assertThat(abschnitte).isEmpty();
    }

    @Test
    @DisplayName("Ein Prüfpunkt eines anderen Protokolls lässt sich hier nicht löschen")
    void fremderPruefpunkt() {
        offenesProtokoll();
        bereiteAbschnitteVor();
        WartungProtokollPruefpunkt fremd = punkt(700L, 10L, "Gehört woanders hin", 9, null);
        fremd.setProtokollId(1234L);
        punkte.add(fremd);

        assertThatThrownBy(() -> service.pruefpunktLoeschen(ID, 700L))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(punkte).contains(fremd);
        verify(pruefpunktRepository, never()).delete(fremd);
    }

    @Test
    @DisplayName("Mehr als 500 Prüfpunkte je Feldeinsatz werden abgelehnt")
    void obergrenze() {
        offenesProtokoll();
        bereiteAbschnitteVor();
        for (int i = 0; i < 498; i++) {
            punkte.add(punkt(1000L + i, 10L, "P" + i, 10 + i, null));
        }

        assertThatThrownBy(() -> service.pruefpunktHinzufuegen(ID, new WartungPruefpunktNeuDto(10L, "Zu viel")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getReason()).contains("höchstens 500"));
    }
}
