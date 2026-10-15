package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungGlobalesDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulUntertyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollAbschnitt;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungProtokollPruefpunkt;
import org.example.kalkulationsprogramm.repository.wartung.WartungGlobalesDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulAbschnittRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollAbschnittRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollPruefpunktRepository;
import org.example.kalkulationsprogramm.service.wartung.WartungModulAuswahlPruefer.GewaehltesModul;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WartungInhaltKopiererTest {

    private static final long PROTOKOLL = 77L;

    @Mock
    private WartungModulAbschnittRepository katalogAbschnitte;
    @Mock
    private WartungModulPruefpunktRepository katalogPruefpunkte;
    @Mock
    private WartungModulDatenfeldRepository katalogDatenfelder;
    @Mock
    private WartungGlobalesDatenfeldRepository globaleDatenfelder;
    @Mock
    private WartungProtokollAbschnittRepository abschnittRepository;
    @Mock
    private WartungProtokollPruefpunktRepository pruefpunktRepository;
    @Mock
    private WartungProtokollDatenfeldRepository datenfeldRepository;

    @Captor
    private ArgumentCaptor<List<WartungProtokollPruefpunkt>> pruefpunkteCaptor;
    @Captor
    private ArgumentCaptor<List<WartungProtokollDatenfeld>> datenfelderCaptor;

    private WartungInhaltKopierer kopierer;
    private final AtomicLong abschnittIds = new AtomicLong(500);
    private final List<WartungProtokollAbschnitt> gespeicherteAbschnitte = new ArrayList<>();

    @BeforeEach
    void setUp() {
        kopierer = new WartungInhaltKopierer(katalogAbschnitte, katalogPruefpunkte, katalogDatenfelder, globaleDatenfelder,
                abschnittRepository, pruefpunktRepository, datenfeldRepository);
        lenient().when(abschnittRepository.save(any(WartungProtokollAbschnitt.class))).thenAnswer(a -> {
            WartungProtokollAbschnitt abschnitt = a.getArgument(0);
            abschnitt.setId(abschnittIds.incrementAndGet());
            gespeicherteAbschnitte.add(abschnitt);
            return abschnitt;
        });
        lenient().when(globaleDatenfelder.findAllByOrderBySortierungAscIdAsc()).thenReturn(List.of(
                global("Standort / Lage", 1), global("Hersteller", 2)));

        // Brandschutztür: Hauptabschnitt (3 Punkte, einer davon mit Sortierung 1 wie im Zusatzabschnitt)
        // und Zusatzabschnitt "2-flügelig" mit 2 Punkten, die nur bei der Bauart zweiflg gelten.
        lenient().when(katalogAbschnitte.findByModulIdInOrderBySortierungAscIdAsc(any())).thenReturn(List.of(
                abschnitt(10, 1, "Prüfpunkte Brandschutztür", 0), abschnitt(11, 1, "Prüfpunkte 2-flügeliger Abschluss", 99)));
        lenient().when(katalogPruefpunkte.findByModulIdInOrderBySortierungAscIdAsc(any())).thenReturn(List.of(
                punkt(100, 1, 10L, "Schild lesbar", 1, null),
                punkt(101, 1, 11L, "Schließfolge prüfen", 1, "zweiflg"),
                punkt(102, 1, 10L, "Bänder prüfen", 2, null),
                punkt(103, 1, 11L, "Koordinator prüfen", 2, "zweiflg"),
                punkt(104, 1, 10L, "Schließer prüfen", 3, null)));
        lenient().when(katalogDatenfelder.findByModulIdInOrderBySortierungAscIdAsc(any())).thenReturn(List.of(
                feld(200, 1, "Feuerwiderstand", 1)));
    }

    @Test
    @DisplayName("Bauart 2-flügelig: Prüfpunkte erst Abschnitt für Abschnitt, nicht nach Sortierungszahl durcheinander")
    void zweifluegeligReihenfolge() {
        kopierer.kopiere(PROTOKOLL, List.of(bst("zweiflg")), Map.of());

        verify(pruefpunktRepository).saveAll(pruefpunkteCaptor.capture());
        List<WartungProtokollPruefpunkt> punkte = pruefpunkteCaptor.getValue();
        assertThat(punkte).extracting(WartungProtokollPruefpunkt::getLabel).containsExactly(
                "Schild lesbar", "Bänder prüfen", "Schließer prüfen", "Schließfolge prüfen", "Koordinator prüfen");
        assertThat(punkte).extracting(WartungProtokollPruefpunkt::getSortierung).containsExactly(0, 1, 2, 3, 4);
        assertThat(gespeicherteAbschnitte).extracting(WartungProtokollAbschnitt::getLabel)
                .containsExactly("Prüfpunkte Brandschutztür", "Prüfpunkte 2-flügeliger Abschluss");
    }

    @Test
    @DisplayName("Bauart 1-flügelig: Punkte nur für 2-flügelig fehlen und es entsteht keine leere Überschrift")
    void einfluegeligOhneLeerenAbschnitt() {
        kopierer.kopiere(PROTOKOLL, List.of(bst("einflg")), Map.of());

        verify(pruefpunktRepository).saveAll(pruefpunkteCaptor.capture());
        assertThat(pruefpunkteCaptor.getValue()).extracting(WartungProtokollPruefpunkt::getLabel)
                .containsExactly("Schild lesbar", "Bänder prüfen", "Schließer prüfen");
        assertThat(gespeicherteAbschnitte).extracting(WartungProtokollAbschnitt::getLabel)
                .containsExactly("Prüfpunkte Brandschutztür");
    }

    @Test
    @DisplayName("Jeder Prüfpunkt hängt am neuen Abschnitt, trägt Modul und Vorlage und ist unbewertet")
    void verknuepfungenUndGrundzustand() {
        kopierer.kopiere(PROTOKOLL, List.of(bst("zweiflg")), Map.of());

        verify(pruefpunktRepository).saveAll(pruefpunkteCaptor.capture());
        WartungProtokollPruefpunkt erster = pruefpunkteCaptor.getValue().get(0);
        assertThat(erster.getProtokollId()).isEqualTo(PROTOKOLL);
        assertThat(erster.getModulKey()).isEqualTo("m_bst");
        assertThat(erster.getVorlageId()).isEqualTo(100L);
        assertThat(erster.getErgebnis()).isNull();
        assertThat(erster.getAbschnittId()).isEqualTo(gespeicherteAbschnitte.get(0).getId());
        WartungProtokollPruefpunkt zweiflg = pruefpunkteCaptor.getValue().get(3);
        assertThat(zweiflg.getAbschnittId()).isEqualTo(gespeicherteAbschnitte.get(1).getId());
    }

    @Test
    @DisplayName("Ergebnisse aus dem letzten Protokoll werden nur als Referenz übernommen, nicht als Bewertung")
    void vorherigeErgebnisseAlsReferenz() {
        Map<String, WartungErgebnis> vorherige = Map.of(
                WartungInhaltKopierer.schluessel("m_bst", "Schild lesbar"), WartungErgebnis.NIO,
                WartungInhaltKopierer.schluessel("m_bst", "Bänder prüfen"), WartungErgebnis.IO,
                // gleicher Text, anderes Modul: darf nicht greifen
                WartungInhaltKopierer.schluessel("m_rs", "Schließer prüfen"), WartungErgebnis.NA);

        kopierer.kopiere(PROTOKOLL, List.of(bst("einflg")), vorherige);

        verify(pruefpunktRepository).saveAll(pruefpunkteCaptor.capture());
        List<WartungProtokollPruefpunkt> punkte = pruefpunkteCaptor.getValue();
        assertThat(punkte.get(0).getVorherigesErgebnis()).isEqualTo(WartungErgebnis.NIO);
        assertThat(punkte.get(1).getVorherigesErgebnis()).isEqualTo(WartungErgebnis.IO);
        assertThat(punkte.get(2).getVorherigesErgebnis()).isNull();
        assertThat(punkte).allSatisfy(p -> assertThat(p.getErgebnis()).isNull());
    }

    @Test
    @DisplayName("Globale Datenfelder stehen vor den Feldern der Module")
    void globaleFelderZuerst() {
        kopierer.kopiere(PROTOKOLL, List.of(bst("einflg")), Map.of());

        verify(datenfeldRepository).saveAll(datenfelderCaptor.capture());
        List<WartungProtokollDatenfeld> felder = datenfelderCaptor.getValue();
        assertThat(felder).extracting(WartungProtokollDatenfeld::getLabel)
                .containsExactly("Standort / Lage", "Hersteller", "Feuerwiderstand");
        assertThat(felder.get(0).getModulKey()).isNull();
        assertThat(felder.get(2).getModulKey()).isEqualTo("m_bst");
        assertThat(felder).extracting(WartungProtokollDatenfeld::getSortierung).containsExactly(0, 1, 2);
    }

    private static GewaehltesModul bst(String bauart) {
        WartungModul modul = new WartungModul();
        modul.setId(1L);
        modul.setModulKey("m_bst");
        modul.setName("Brandschutztür");
        WartungModulUntertyp untertyp = new WartungModulUntertyp();
        untertyp.setModulId(1L);
        untertyp.setSchluessel(bauart);
        untertyp.setLabel(bauart);
        return new GewaehltesModul(modul, untertyp);
    }

    private static WartungGlobalesDatenfeld global(String label, int sortierung) {
        WartungGlobalesDatenfeld feld = new WartungGlobalesDatenfeld();
        feld.setLabel(label);
        feld.setFeldtyp(WartungFeldtyp.TEXT);
        feld.setSortierung(sortierung);
        return feld;
    }

    private static WartungModulAbschnitt abschnitt(long id, long modulId, String label, int sortierung) {
        WartungModulAbschnitt a = new WartungModulAbschnitt();
        a.setId(id);
        a.setModulId(modulId);
        a.setBereich(WartungBereich.PRUEFPUNKTE);
        a.setLabel(label);
        a.setSortierung(sortierung);
        return a;
    }

    private static WartungModulPruefpunkt punkt(long id, long modulId, Long abschnittId, String label, int sortierung, String nurBei) {
        WartungModulPruefpunkt p = new WartungModulPruefpunkt();
        p.setId(id);
        p.setModulId(modulId);
        p.setAbschnittId(abschnittId);
        p.setLabel(label);
        p.setSortierung(sortierung);
        p.setNurBeiUntertyp(nurBei);
        return p;
    }

    private static WartungModulDatenfeld feld(long id, long modulId, String label, int sortierung) {
        WartungModulDatenfeld f = new WartungModulDatenfeld();
        f.setId(id);
        f.setModulId(modulId);
        f.setLabel(label);
        f.setFeldtyp(WartungFeldtyp.TEXT);
        f.setSortierung(sortierung);
        return f;
    }

    // ── Folgeprüfung: Kopie einer früheren Prüfung ──────────────────────────

    private static final long VORLAGE = 55L;

    private void vorlageMitInhalt() {
        WartungProtokollAbschnitt a = new WartungProtokollAbschnitt();
        a.setId(30L);
        a.setProtokollId(VORLAGE);
        a.setBereich(WartungBereich.PRUEFPUNKTE);
        a.setLabel("Prüfpunkte Heizung");
        a.setSortierung(0);
        WartungProtokollAbschnitt zusatz = new WartungProtokollAbschnitt();
        zusatz.setId(31L);
        zusatz.setProtokollId(VORLAGE);
        zusatz.setBereich(WartungBereich.PRUEFPUNKTE);
        zusatz.setLabel("Zusätzliche Prüfpunkte");
        zusatz.setSortierung(1);
        lenient().when(abschnittRepository.findByProtokollIdOrderBySortierungAscIdAsc(VORLAGE)).thenReturn(List.of(a, zusatz));

        WartungProtokollPruefpunkt p1 = vorlagePunkt(1L, 30L, "Druck prüfen", 0, WartungErgebnis.IO, "Alles gut");
        WartungProtokollPruefpunkt p2 = vorlagePunkt(2L, 30L, "Sicherheitsventil", 1, WartungErgebnis.NIO, "Tropft");
        WartungProtokollPruefpunkt p3 = vorlagePunkt(3L, 31L, "Abgasmessung (ergänzt)", 2, WartungErgebnis.NA, "Kein Gerät vorhanden");
        lenient().when(pruefpunktRepository.findByProtokollIdOrderBySortierungAscIdAsc(VORLAGE)).thenReturn(List.of(p1, p2, p3));

        WartungProtokollDatenfeld d = new WartungProtokollDatenfeld();
        d.setId(9L);
        d.setProtokollId(VORLAGE);
        d.setLabel("Hersteller");
        d.setFeldtyp(org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp.TEXT);
        d.setWert("Vaillant");
        d.setSortierung(0);
        lenient().when(datenfeldRepository.findByProtokollIdOrderBySortierungAscIdAsc(VORLAGE)).thenReturn(List.of(d));
    }

    private static WartungProtokollPruefpunkt vorlagePunkt(long id, long abschnitt, String label, int sortierung, WartungErgebnis ergebnis, String bemerkung) {
        WartungProtokollPruefpunkt p = new WartungProtokollPruefpunkt();
        p.setId(id);
        p.setProtokollId(VORLAGE);
        p.setAbschnittId(abschnitt);
        p.setModulKey("m_heizung");
        p.setVorlageId(100 + id);
        p.setLabel(label);
        p.setSortierung(sortierung);
        p.setErgebnis(ergebnis);
        p.setBemerkung(bemerkung);
        return p;
    }

    @Test
    @DisplayName("Folgeprüfung mit Werten: Struktur samt ergänzten Prüfpunkten, Ergebnisse, Bemerkungen und Datenfeld-Werte kommen mit")
    void folgepruefungMitWerten() {
        vorlageMitInhalt();

        kopierer.kopiereAusProtokoll(VORLAGE, PROTOKOLL, true);

        assertThat(gespeicherteAbschnitte).extracting(WartungProtokollAbschnitt::getLabel).containsExactly("Prüfpunkte Heizung", "Zusätzliche Prüfpunkte");
        assertThat(gespeicherteAbschnitte).allSatisfy(a -> assertThat(a.getProtokollId()).isEqualTo(PROTOKOLL));
        verify(pruefpunktRepository).saveAll(pruefpunkteCaptor.capture());
        List<WartungProtokollPruefpunkt> punkte = pruefpunkteCaptor.getValue();
        assertThat(punkte).extracting(WartungProtokollPruefpunkt::getLabel).containsExactly("Druck prüfen", "Sicherheitsventil", "Abgasmessung (ergänzt)");
        assertThat(punkte).extracting(WartungProtokollPruefpunkt::getErgebnis).containsExactly(WartungErgebnis.IO, WartungErgebnis.NIO, WartungErgebnis.NA);
        assertThat(punkte).extracting(WartungProtokollPruefpunkt::getBemerkung).containsExactly("Alles gut", "Tropft", "Kein Gerät vorhanden");
        assertThat(punkte).extracting(WartungProtokollPruefpunkt::getVorherigesErgebnis).containsExactly(WartungErgebnis.IO, WartungErgebnis.NIO, WartungErgebnis.NA);
        assertThat(punkte).allSatisfy(p -> {
            assertThat(p.getId()).isNull();
            assertThat(p.getProtokollId()).isEqualTo(PROTOKOLL);
        });
        // Die Prüfpunkte hängen an den NEUEN Abschnitten, nicht an denen der Vorlage
        assertThat(punkte.get(0).getAbschnittId()).isEqualTo(gespeicherteAbschnitte.get(0).getId()).isNotIn(30L, 31L);
        assertThat(punkte.get(2).getAbschnittId()).isEqualTo(gespeicherteAbschnitte.get(1).getId());
        verify(datenfeldRepository).saveAll(datenfelderCaptor.capture());
        assertThat(datenfelderCaptor.getValue()).extracting(WartungProtokollDatenfeld::getWert).containsExactly("Vaillant");
        assertThat(datenfelderCaptor.getValue().get(0).getId()).isNull();
    }

    @Test
    @DisplayName("Folgeprüfung ohne Werte: Ergebnisse, Bemerkungen und Werte bleiben leer, das Ergebnis der Vorlage steht als 'Letztes Mal' da")
    void folgepruefungOhneWerte() {
        vorlageMitInhalt();

        kopierer.kopiereAusProtokoll(VORLAGE, PROTOKOLL, false);

        verify(pruefpunktRepository).saveAll(pruefpunkteCaptor.capture());
        assertThat(pruefpunkteCaptor.getValue()).extracting(WartungProtokollPruefpunkt::getErgebnis).containsOnlyNulls();
        assertThat(pruefpunkteCaptor.getValue()).extracting(WartungProtokollPruefpunkt::getBemerkung).containsOnlyNulls();
        assertThat(pruefpunkteCaptor.getValue()).extracting(WartungProtokollPruefpunkt::getVorherigesErgebnis)
                .containsExactly(WartungErgebnis.IO, WartungErgebnis.NIO, WartungErgebnis.NA);
        verify(datenfeldRepository).saveAll(datenfelderCaptor.capture());
        assertThat(datenfelderCaptor.getValue()).extracting(WartungProtokollDatenfeld::getWert).containsOnlyNulls();
    }

    @Test
    @DisplayName("Die Vorlage wird nur gelesen: Es wird nichts an ihr gespeichert oder gelöscht")
    void vorlageBleibtUnveraendert() {
        vorlageMitInhalt();

        kopierer.kopiereAusProtokoll(VORLAGE, PROTOKOLL, true);

        verify(pruefpunktRepository, never()).delete(any(WartungProtokollPruefpunkt.class));
        verify(pruefpunktRepository, never()).deleteAll(any(Iterable.class));
        verify(abschnittRepository, never()).delete(any(WartungProtokollAbschnitt.class));
        // Gespeichert wird genau eine Liste (die neuen Prüfpunkte), und alle gehören zum neuen Protokoll
        verify(pruefpunktRepository).saveAll(pruefpunkteCaptor.capture());
        assertThat(pruefpunkteCaptor.getValue()).noneMatch(p -> p.getProtokollId() == VORLAGE);
    }
}
