package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokoll;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungStatus;
import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldDefinitionDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldNeuDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungDatenfeldWertDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungProtokollDto;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Datenfelder im laufenden Feldeinsatz hinzufügen, umbenennen und löschen. Es ändert immer nur dieses Protokoll,
 * ein abgeschlossenes bleibt unverändert, und die Reihenfolge der Gruppen bleibt erhalten.
 */
@ExtendWith(MockitoExtension.class)
class WartungDatenfeldBearbeitenTest {

    private static final long ID = 42L;

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

    private WartungProtokoll protokoll;
    private final List<WartungProtokollDatenfeld> felder = new ArrayList<>();
    private final List<WartungProtokollAbschnitt> abschnitte = new ArrayList<>();
    private final List<WartungProtokollPruefpunkt> punkte = new ArrayList<>();
    private long naechsteId = 800;

    @BeforeEach
    void setUp() {
        Kunde kunde = new Kunde();
        kunde.setId(1L);
        kunde.setName("Max Mustermann GmbH");
        WartungObjekt objekt = new WartungObjekt();
        objekt.setId(5L);
        objekt.setKunde(kunde);
        objekt.setBezeichnung("Werk Nord");

        protokoll = new WartungProtokoll();
        protokoll.setId(ID);
        protokoll.setNummer("WA-2026-2001");
        protokoll.setKunde(kunde);
        protokoll.setObjekt(objekt);
        protokoll.setDatum(LocalDate.of(2026, 10, 1));
        protokoll.setStatus(WartungStatus.OFFEN);
        protokoll.getModule().add(new WartungProtokollModul(1L, "m_bst", "Brandschutztür", null, null));
        protokoll.getModule().add(new WartungProtokollModul(3L, "m_fsa", "Feststellanlage", null, null));

        // Allgemein (3 Felder), Brandschutztür (2 Felder in Abschnitt 11), Feststellanlage noch ohne Felder
        feld(1L, null, null, "Standort / Lage", WartungFeldtyp.TEXT, null, "Treppenhaus", 0);
        feld(2L, null, null, "Hersteller", WartungFeldtyp.TEXT, null, null, 1);
        feld(3L, null, null, "Zulassungsnummer", WartungFeldtyp.TEXT, null, null, 2);
        feld(4L, 11L, "m_bst", "Feuerwiderstand / Rauchschutzklasse", WartungFeldtyp.TEXT, null, null, 3);
        feld(5L, 11L, "m_bst", "Gebrauchs- u. Wartungsanweisung liegt vor", WartungFeldtyp.JA_NEIN, null, "Ja", 4);
        WartungProtokollAbschnitt a = new WartungProtokollAbschnitt();
        a.setId(11L);
        a.setProtokollId(ID);
        a.setBereich(WartungBereich.DATENFELDER);
        a.setLabel("Datenaufnahme Brandschutztür");
        abschnitte.add(a);

        lenient().when(protokollRepository.findMitSperre(ID)).thenReturn(Optional.of(protokoll));
        lenient().when(protokollRepository.findById(ID)).thenReturn(Optional.of(protokoll));
        lenient().when(protokollRepository.save(any(WartungProtokoll.class))).thenAnswer(x -> x.getArgument(0));
        lenient().when(datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenAnswer(x ->
                felder.stream().sorted(java.util.Comparator.comparingInt(WartungProtokollDatenfeld::getSortierung)).toList());
        lenient().when(datenfeldRepository.findByIdAndProtokollId(anyLong(), anyLong())).thenAnswer(x ->
                felder.stream().filter(f -> f.getId().equals(x.getArgument(0)) && f.getProtokollId().equals(x.getArgument(1))).findFirst());
        lenient().when(datenfeldRepository.save(any(WartungProtokollDatenfeld.class))).thenAnswer(x -> {
            WartungProtokollDatenfeld f = x.getArgument(0);
            if (f.getId() == null) {
                f.setId(naechsteId++);
                felder.add(f);
            }
            return f;
        });
        lenient().when(datenfeldRepository.saveAll(any())).thenAnswer(x -> x.getArgument(0));
        lenient().doAnswer(x -> { felder.remove(x.getArgument(0)); return null; }).when(datenfeldRepository).delete(any(WartungProtokollDatenfeld.class));
        lenient().when(abschnittRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenAnswer(x -> List.copyOf(abschnitte));
        lenient().when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(ID)).thenAnswer(x -> List.copyOf(punkte));
        lenient().doAnswer(x -> { abschnitte.removeIf(ab -> ab.getId().equals(x.getArgument(0))); return null; }).when(abschnittRepository).deleteById(anyLong());
    }

    private void feld(Long id, Long abschnittId, String modulKey, String label, WartungFeldtyp typ, String optionen, String wert, int sortierung) {
        WartungProtokollDatenfeld f = new WartungProtokollDatenfeld();
        f.setId(id);
        f.setProtokollId(ID);
        f.setAbschnittId(abschnittId);
        f.setModulKey(modulKey);
        f.setLabel(label);
        f.setFeldtyp(typ);
        f.setOptionen(optionen);
        f.setWert(wert);
        f.setSortierung(sortierung);
        felder.add(f);
    }

    private List<String> reihenfolge() {
        return felder.stream().sorted(java.util.Comparator.comparingInt(WartungProtokollDatenfeld::getSortierung))
                .map(WartungProtokollDatenfeld::getLabel).toList();
    }

    private static ResponseStatusException fehler(Runnable r) {
        try {
            r.run();
        } catch (ResponseStatusException e) {
            return e;
        }
        throw new AssertionError("Es wurde keine Ablehnung erwartet, aber keine kam");
    }

    // ── Hinzufügen ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Ein neues Textfeld ohne Gruppe steht am Ende von \"Allgemein\", mit getrimmter Bezeichnung; das Protokoll wird \"in Arbeit\"")
    void textfeldInAllgemein() {
        WartungProtokollDto dto = service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "  Baujahr  ", null, null));

        assertThat(reihenfolge()).containsExactly("Standort / Lage", "Hersteller", "Zulassungsnummer", "Baujahr",
                "Feuerwiderstand / Rauchschutzklasse", "Gebrauchs- u. Wartungsanweisung liegt vor");
        WartungProtokollDatenfeld neu = felder.stream().filter(f -> "Baujahr".equals(f.getLabel())).findFirst().orElseThrow();
        assertThat(neu.getFeldtyp()).isEqualTo(WartungFeldtyp.TEXT);
        assertThat(neu.getModulKey()).isNull();
        assertThat(neu.getWert()).isNull();
        assertThat(neu.getAbschnittId()).isNull();
        assertThat(protokoll.getStatus()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
        assertThat(dto.datenfelder()).extracting(WartungProtokollDto.Datenfeld::label).contains("Baujahr");
    }

    @Test
    @DisplayName("Die Sortierung wird lückenlos neu durchgezählt: Oberfläche und PDF sortieren nur nach dieser Nummer")
    void sortierungLueckenlos() {
        service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Baujahr", null, null));

        assertThat(felder.stream().map(WartungProtokollDatenfeld::getSortierung).sorted().toList()).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    @DisplayName("Ein neues Feld in einer Modul-Gruppe steht hinter dem letzten Feld dieser Gruppe und im selben Abschnitt")
    void feldInModulGruppe() {
        service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto("m_bst", "Brandschutzklasse geprüft", WartungFeldtyp.JA_NEIN, null));

        assertThat(reihenfolge()).containsExactly("Standort / Lage", "Hersteller", "Zulassungsnummer", "Feuerwiderstand / Rauchschutzklasse",
                "Gebrauchs- u. Wartungsanweisung liegt vor", "Brandschutzklasse geprüft");
        WartungProtokollDatenfeld neu = felder.stream().filter(f -> "Brandschutzklasse geprüft".equals(f.getLabel())).findFirst().orElseThrow();
        assertThat(neu.getModulKey()).isEqualTo("m_bst");
        assertThat(neu.getFeldtyp()).isEqualTo(WartungFeldtyp.JA_NEIN);
        assertThat(neu.getAbschnittId()).isEqualTo(11L);
    }

    @Test
    @DisplayName("Ein Feld für ein Modul ohne bisherige Felder steht am Ende und braucht keinen Abschnitt")
    void erstesFeldEinerGruppe() {
        service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto("m_fsa", "Rauchmelder Hersteller/Typ", null, null));

        assertThat(reihenfolge().get(5)).isEqualTo("Rauchmelder Hersteller/Typ");
        WartungProtokollDatenfeld neu = felder.stream().filter(f -> "Rauchmelder Hersteller/Typ".equals(f.getLabel())).findFirst().orElseThrow();
        assertThat(neu.getModulKey()).isEqualTo("m_fsa");
        assertThat(neu.getAbschnittId()).isNull();
    }

    @Test
    @DisplayName("Auswahl: Möglichkeiten werden getrimmt, Leere und Doppelte entfallen, gespeichert wird zeilenweise")
    void auswahl() {
        service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Zustand", WartungFeldtyp.AUSWAHL, List.of(" gut ", "mittel", "", "gut", "schlecht")));

        WartungProtokollDatenfeld neu = felder.stream().filter(f -> "Zustand".equals(f.getLabel())).findFirst().orElseThrow();
        assertThat(neu.getFeldtyp()).isEqualTo(WartungFeldtyp.AUSWAHL);
        assertThat(neu.getOptionen()).isEqualTo("gut\nmittel\nschlecht");
    }

    @Test
    @DisplayName("Eine Auswahl braucht mindestens zwei Möglichkeiten; bei Text und Ja/Nein sind Möglichkeiten nicht erlaubt")
    void optionenRegeln() {
        assertThat(fehler(() -> service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Zustand", WartungFeldtyp.AUSWAHL, List.of("gut")))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fehler(() -> service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Zustand", WartungFeldtyp.AUSWAHL, null))).getReason())
                .contains("mindestens zwei");
        assertThat(fehler(() -> service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Baujahr", WartungFeldtyp.TEXT, List.of("a", "b")))).getReason())
                .contains("nur bei einer Auswahl");
        assertThat(fehler(() -> service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Ja oder nein", WartungFeldtyp.JA_NEIN, List.of("a")))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(felder).hasSize(5);
    }

    @Test
    @DisplayName("Gibt es noch kein allgemeines Feld, steht das erste unter \"Allgemein\" ganz vorn, wie die Oberfläche es zeigt (nicht hinter den Modulen)")
    void ersteAllgemeinesFeldStehtVorn() {
        felder.removeIf(f -> f.getModulKey() == null);

        service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Baujahr", null, null));

        assertThat(reihenfolge().get(0)).isEqualTo("Baujahr");
        assertThat(felder.stream().map(WartungProtokollDatenfeld::getSortierung).sorted().toList()).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("Möglichkeiten mit eingebettetem Zeilenumbruch werden aufgeteilt: Die Prüfung auf zwei Möglichkeiten und Doppelte gilt für die echten Zeilen")
    void optionenMitZeilenumbruch() {
        service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Zustand", WartungFeldtyp.AUSWAHL, List.of("gut\nmittel", "mittel\nschlecht")));

        assertThat(felder.stream().filter(f -> "Zustand".equals(f.getLabel())).findFirst().orElseThrow().getOptionen()).isEqualTo("gut\nmittel\nschlecht");
        assertThat(fehler(() -> service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Nur eine", WartungFeldtyp.AUSWAHL, List.of("x\nx")))).getReason())
                .contains("mindestens zwei");
    }

    @Test
    @DisplayName("Ein Modul, das nicht zu diesem Feldeinsatz gehört, wird abgelehnt")
    void fremdesModul() {
        ResponseStatusException e = fehler(() -> service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto("m_gibt_es_nicht", "Baujahr", null, null)));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("Modul");
        assertThat(felder).hasSize(5);
    }

    @Test
    @DisplayName("Ein leerer Modul-Schlüssel zählt als \"Allgemein\"")
    void leererModulSchluessel() {
        service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto("   ", "Baujahr", null, null));

        assertThat(felder.stream().filter(f -> "Baujahr".equals(f.getLabel())).findFirst().orElseThrow().getModulKey()).isNull();
    }

    @Test
    @DisplayName("Höchstens 200 Datenfelder je Feldeinsatz: das 201. wird mit Hinweis abgelehnt")
    void obergrenze() {
        for (int i = felder.size(); i < WartungProtokollService.MAX_DATENFELDER_JE_PROTOKOLL; i++) {
            feld((long) (100 + i), null, null, "Feld " + i, WartungFeldtyp.TEXT, null, null, i);
        }

        ResponseStatusException e = fehler(() -> service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Eins zu viel", null, null)));

        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(e.getReason()).contains("200");
        assertThat(felder).hasSize(WartungProtokollService.MAX_DATENFELDER_JE_PROTOKOLL);
    }

    @Test
    @DisplayName("Sonderzeichen in der Bezeichnung (SQL, Skript) werden unverändert als Text gespeichert")
    void sonderzeichen() {
        service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "'; DROP TABLE wartung_protokoll_datenfeld; --", null, null));
        service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "<script>alert(1)</script>", null, null));

        assertThat(reihenfolge()).contains("'; DROP TABLE wartung_protokoll_datenfeld; --", "<script>alert(1)</script>");
    }

    @Test
    @DisplayName("Abgeschlossen oder archiviert: nichts lässt sich hinzufügen, ändern oder löschen (409), und es wird nichts geschrieben")
    void gesperrt() {
        for (WartungStatus s : new WartungStatus[] {WartungStatus.ABGESCHLOSSEN, WartungStatus.ARCHIVIERT}) {
            protokoll.setStatus(s);

            assertThat(fehler(() -> service.datenfeldHinzufuegen(ID, new WartungDatenfeldNeuDto(null, "Baujahr", null, null))).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(fehler(() -> service.datenfeldDefinition(ID, 1L, new WartungDatenfeldDefinitionDto("Neu", null))).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(fehler(() -> service.datenfeldLoeschen(ID, 1L)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
        assertThat(felder).hasSize(5);
        assertThat(felder.get(0).getLabel()).isEqualTo("Standort / Lage");
        verify(datenfeldRepository, never()).delete(any(WartungProtokollDatenfeld.class));
    }

    // ── Umbenennen ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Umbenennen ändert nur die Bezeichnung: Wert, Art und Platz bleiben")
    void umbenennen() {
        service.datenfeldDefinition(ID, 1L, new WartungDatenfeldDefinitionDto("  Standort / Raum  ", null));

        WartungProtokollDatenfeld f = felder.get(0);
        assertThat(f.getLabel()).isEqualTo("Standort / Raum");
        assertThat(f.getWert()).isEqualTo("Treppenhaus");
        assertThat(f.getFeldtyp()).isEqualTo(WartungFeldtyp.TEXT);
        assertThat(f.getSortierung()).isZero();
        assertThat(protokoll.getStatus()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
    }

    @Test
    @DisplayName("Auswahl ändern: Ein Wert, der weiter angeboten wird, bleibt; ein nicht mehr angebotener wird geleert")
    void auswahlAendern() {
        feld(6L, null, null, "Zustand", WartungFeldtyp.AUSWAHL, "gut\nmittel\nschlecht", "mittel", 5);
        feld(7L, null, null, "Zustand 2", WartungFeldtyp.AUSWAHL, "gut\nmittel\nschlecht", "schlecht", 6);

        service.datenfeldDefinition(ID, 6L, new WartungDatenfeldDefinitionDto("Zustand", List.of("gut", "mittel", "defekt")));
        service.datenfeldDefinition(ID, 7L, new WartungDatenfeldDefinitionDto("Zustand 2", List.of("gut", "defekt")));

        assertThat(felder.stream().filter(f -> f.getId() == 6L).findFirst().orElseThrow().getWert()).isEqualTo("mittel");
        assertThat(felder.stream().filter(f -> f.getId() == 7L).findFirst().orElseThrow().getWert()).isNull();
        assertThat(felder.stream().filter(f -> f.getId() == 7L).findFirst().orElseThrow().getOptionen()).isEqualTo("gut\ndefekt");
    }

    @Test
    @DisplayName("Bei einer Auswahl ohne Angabe von Möglichkeiten bleiben sie, wie sie sind (nur umbenennen)")
    void auswahlNurUmbenennen() {
        feld(6L, null, null, "Zustand", WartungFeldtyp.AUSWAHL, "gut\nmittel", "gut", 5);

        service.datenfeldDefinition(ID, 6L, new WartungDatenfeldDefinitionDto("Allgemeinzustand", null));

        WartungProtokollDatenfeld f = felder.stream().filter(x -> x.getId() == 6L).findFirst().orElseThrow();
        assertThat(f.getLabel()).isEqualTo("Allgemeinzustand");
        assertThat(f.getOptionen()).isEqualTo("gut\nmittel");
        assertThat(f.getWert()).isEqualTo("gut");
    }

    @Test
    @DisplayName("Möglichkeiten bei einem Text- oder Ja/Nein-Feld: abgelehnt; unbekanntes Feld: 404")
    void definitionFehler() {
        assertThat(fehler(() -> service.datenfeldDefinition(ID, 1L, new WartungDatenfeldDefinitionDto("Neu", List.of("a", "b")))).getReason())
                .contains("nur bei einer Auswahl");
        assertThat(fehler(() -> service.datenfeldDefinition(ID, 5L, new WartungDatenfeldDefinitionDto("Neu", List.of("a")))).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fehler(() -> service.datenfeldDefinition(ID, 999L, new WartungDatenfeldDefinitionDto("Neu", null))).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(felder.get(0).getLabel()).isEqualTo("Standort / Lage");
    }

    // ── Löschen ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Löschen entfernt das Feld samt Wert aus diesem Feldeinsatz; der Rest bleibt in Reihenfolge")
    void loeschen() {
        service.datenfeldLoeschen(ID, 1L);

        assertThat(reihenfolge()).containsExactly("Hersteller", "Zulassungsnummer", "Feuerwiderstand / Rauchschutzklasse", "Gebrauchs- u. Wartungsanweisung liegt vor");
        assertThat(protokoll.getStatus()).isEqualTo(WartungStatus.IN_BEARBEITUNG);
    }

    @Test
    @DisplayName("Das letzte Feld eines Abschnitts entfernen: Der leere Abschnitt entfällt mit; solange noch etwas darin ist, bleibt er")
    void leererAbschnittEntfaellt() {
        service.datenfeldLoeschen(ID, 4L);
        assertThat(abschnitte).hasSize(1);

        service.datenfeldLoeschen(ID, 5L);
        assertThat(abschnitte).isEmpty();
    }

    @Test
    @DisplayName("Ein Abschnitt, in dem noch ein Prüfpunkt liegt, bleibt beim Löschen des letzten Datenfelds bestehen")
    void abschnittMitPruefpunktBleibt() {
        WartungProtokollPruefpunkt p = new WartungProtokollPruefpunkt();
        p.setId(900L);
        p.setProtokollId(ID);
        p.setAbschnittId(11L);
        p.setLabel("Dichtung");
        punkte.add(p);

        service.datenfeldLoeschen(ID, 4L);
        service.datenfeldLoeschen(ID, 5L);

        assertThat(abschnitte).hasSize(1);
    }

    @Test
    @DisplayName("Ein Datenfeld eines anderen Protokolls oder eine unbekannte Nummer ist \"nicht gefunden\" (auch 0, negativ und Long.MAX_VALUE)")
    void fremdeNummern() {
        WartungProtokollDatenfeld fremd = new WartungProtokollDatenfeld();
        fremd.setId(77L);
        fremd.setProtokollId(99L);
        fremd.setLabel("Fremd");
        felder.add(fremd);

        for (long id : new long[] {77L, 0L, -1L, Long.MAX_VALUE}) {
            assertThat(fehler(() -> service.datenfeldLoeschen(ID, id)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
            assertThat(fehler(() -> service.datenfeldDefinition(ID, id, new WartungDatenfeldDefinitionDto("Neu", null))).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(felder).hasSize(6);
    }

    @Test
    @DisplayName("Jahreszahl: vier Ziffern zwischen 1900 und nächstem Jahr, leer erlaubt; alles andere wird mit Klartext abgelehnt")
    void jahreszahl() {
        feld(20L, null, null, "Baujahr", WartungFeldtyp.JAHR, null, null, 20);

        assertThat(service.datenfeldSetzen(ID, 20L, new WartungDatenfeldWertDto(" 2014 ")).datenfeld().wert()).isEqualTo("2014");
        assertThat(service.datenfeldSetzen(ID, 20L, new WartungDatenfeldWertDto("")).datenfeld().wert()).isNull();
        for (String falsch : new String[] { "14", "20145", "1899", String.valueOf(java.time.Year.now().getValue() + 2), "abcd", "20.14", "-2014", "keine Angabe" }) {
            assertThat(fehler(() -> service.datenfeldSetzen(ID, 20L, new WartungDatenfeldWertDto(falsch))).getReason()).as(falsch).contains("vierstelliges Jahr");
        }
    }

    @Test
    @DisplayName("Auswahl mit „keine Angabe“: der Wert ist erlaubt, wenn er in der Liste steht, und zählt als Wert")
    void keineAngabeInDerAuswahl() {
        feld(21L, null, null, "Anschlag", WartungFeldtyp.AUSWAHL, "keine Angabe\nDIN links\nDIN rechts", null, 21);

        assertThat(service.datenfeldSetzen(ID, 21L, new WartungDatenfeldWertDto("keine Angabe")).datenfeld().wert()).isEqualTo("keine Angabe");
        assertThat(service.datenfeldSetzen(ID, 21L, new WartungDatenfeldWertDto("DIN links")).datenfeld().wert()).isEqualTo("DIN links");
        assertThat(fehler(() -> service.datenfeldSetzen(ID, 21L, new WartungDatenfeldWertDto("DINL"))).getReason()).contains("angebotenen");
        assertThat(WartungFeldtyp.istKeineAngabe(" Keine Angabe ")).isTrue();
        assertThat(WartungFeldtyp.istKeineAngabe("")).isFalse();
        assertThat(WartungFeldtyp.istKeineAngabe(null)).isFalse();
    }
}
