package org.example.kalkulationsprogramm.service.wartung;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungModul;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulAuswahl;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulDatenfeld;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulPruefpunkt;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulKategorie;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.wartung.WartungGlobalesDatenfeldDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulBearbeitenDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulBearbeitenDto.Abschnitt;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulBearbeitenDto.Datenfeld;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulBearbeitenDto.Pruefpunkt;
import org.example.kalkulationsprogramm.dto.wartung.WartungModulBearbeitenDto.Untertyp;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulAbschnittRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulDatenfeldRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulPruefpunktRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungModulUntertypRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Der Modul-Editor gegen die Test-Datenbank: Abgleich nach Nummer, Schutz benutzter Einträge, Reihenfolge, globale Felder. */
@DataJpaTest
@Import(WartungModulAdminService.class)
class WartungModulAdminServiceTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private WartungModulAdminService service;
    @Autowired
    private WartungModulRepository modulRepository;
    @Autowired
    private WartungModulUntertypRepository untertypRepository;
    @Autowired
    private WartungModulAbschnittRepository abschnittRepository;
    @Autowired
    private WartungModulPruefpunktRepository pruefpunktRepository;
    @Autowired
    private WartungModulDatenfeldRepository datenfeldRepository;

    private WartungModulBearbeitenDto tuerModul;

    @BeforeEach
    void setUp() {
        tuerModul = service.anlegen(modul("Brandschutztür", WartungModulKategorie.BASIS, List.of(),
                List.of(new Untertyp(null, null, "1-flügelig"), new Untertyp(null, null, "2-flügelig")),
                List.of(new Abschnitt(null, WartungBereich.PRUEFPUNKTE, "Prüfpunkte", List.of(
                                new Pruefpunkt(null, "Zulassungsschild lesbar", null),
                                new Pruefpunkt(null, "Zweiter Flügel schließt mit", "2fluegelig")), List.of()),
                        new Abschnitt(null, WartungBereich.DATENFELDER, "Datenaufnahme", List.of(), List.of(
                                new Datenfeld(null, "Hersteller", WartungFeldtyp.TEXT, null),
                                new Datenfeld(null, "Zustand", WartungFeldtyp.AUSWAHL, List.of("gut", "mittel", "schlecht")))))));
        flush();
    }

    private static WartungModulBearbeitenDto modul(String name, WartungModulKategorie art, List<Long> andockbarAn,
                                                  List<Untertyp> untertypen, List<Abschnitt> abschnitte) {
        return new WartungModulBearbeitenDto(null, null, null, name, null, "DoorClosed", art, false, true, 12,
                andockbarAn, untertypen, abschnitte, 0, 0);
    }

    /** Wie {@link #mitVersion}, aber mit dem aktuellen Stand aus der Datenbank. In diesem Test läuft alles in einer Transaktion: Ein fehlgeschlagener Aufruf hat die Version schon hochgezählt (in Produktion rollt er zurück). */
    private WartungModulBearbeitenDto mit(WartungModulBearbeitenDto alt, List<Untertyp> untertypen, List<Abschnitt> abschnitte) {
        return mitVersion(alt, service.laden(alt.id()).version(), untertypen, abschnitte);
    }

    private static WartungModulBearbeitenDto mitVersion(WartungModulBearbeitenDto alt, Long version, List<Untertyp> untertypen, List<Abschnitt> abschnitte) {
        return new WartungModulBearbeitenDto(alt.id(), version, alt.modulKey(), alt.name(), alt.beschreibung(), alt.icon(),
                alt.kategorie(), alt.systemModul(), alt.aktiv(), alt.standardIntervallMonate(), alt.andockbarAn(),
                untertypen, abschnitte, 0, 0);
    }

    private void flush() {
        em.flush();
        em.clear();
    }

    private WartungTuer tuerMit(Long modulId, String bauart) {
        Kunde kunde = new Kunde();
        kunde.setKundennummer("K-" + System.nanoTime());
        kunde.setName("Max Mustermann GmbH");
        em.persist(kunde);
        WartungObjekt objekt = new WartungObjekt();
        objekt.setKunde(kunde);
        objekt.setBezeichnung("Werk Nord");
        em.persist(objekt);
        WartungTuer tuer = new WartungTuer();
        tuer.setObjekt(objekt);
        tuer.setBezeichnung("Tür 1.01");
        tuer.getModule().add(new WartungModulAuswahl(modulId, bauart));
        em.persist(tuer);
        flush();
        return tuer;
    }

    private static void erwarte(Runnable aktion, HttpStatus status, String meldungsteil) {
        assertThatThrownBy(aktion::run).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
            assertThat(e.getStatusCode()).isEqualTo(status);
            assertThat(e.getReason()).contains(meldungsteil);
        });
    }

    // ── Anlegen ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Anlegen bildet Schlüssel für Modul und Bauarten, behält die Reihenfolge und legt den Inhalt an")
    void anlegen() {
        WartungModulBearbeitenDto gelesen = service.laden(tuerModul.id());

        assertThat(gelesen.modulKey()).isEqualTo("m_brandschutztuer");
        assertThat(gelesen.systemModul()).isFalse();
        assertThat(gelesen.untertypen()).extracting(Untertyp::schluessel).containsExactly("1fluegelig", "2fluegelig");
        assertThat(gelesen.abschnitte()).extracting(Abschnitt::label).containsExactly("Prüfpunkte", "Datenaufnahme");
        assertThat(gelesen.abschnitte().get(0).pruefpunkte()).extracting(Pruefpunkt::label)
                .containsExactly("Zulassungsschild lesbar", "Zweiter Flügel schließt mit");
        assertThat(gelesen.abschnitte().get(0).pruefpunkte().get(1).nurBeiUntertyp()).isEqualTo("2fluegelig");
        assertThat(gelesen.abschnitte().get(1).datenfelder().get(1).optionen()).containsExactly("gut", "mittel", "schlecht");
        assertThat(gelesen.abschnitte().get(1).datenfelder().get(0).optionen()).isEmpty();
    }

    @Test
    @DisplayName("Gleiche Namen ergeben verschiedene Modulschlüssel, und neue Module kommen ans Ende")
    void schluesselEindeutig() {
        WartungModulBearbeitenDto zweites = service.anlegen(modul("Brandschutztür", WartungModulKategorie.BASIS, List.of(), List.of(), List.of()));

        assertThat(zweites.modulKey()).isEqualTo("m_brandschutztuer_2");
        assertThat(service.alleModule()).extracting("id").containsExactly(tuerModul.id(), zweites.id());
    }

    // ── Speichern: Nummern bleiben stabil ───────────────────────────────────

    @Test
    @DisplayName("Speichern behält die Nummern vorhandener Einträge, legt neue an und löscht fehlende")
    void abgleichNachNummer() {
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());
        Abschnitt pruefAbschnitt = alt.abschnitte().get(0);
        Pruefpunkt erster = pruefAbschnitt.pruefpunkte().get(0);
        Pruefpunkt entfernt = pruefAbschnitt.pruefpunkte().get(1);
        Abschnitt datenAbschnitt = alt.abschnitte().get(1);

        WartungModulBearbeitenDto neu = mit(alt, alt.untertypen(), List.of(
                new Abschnitt(pruefAbschnitt.id(), WartungBereich.PRUEFPUNKTE, "Prüfpunkte (neu benannt)", List.of(
                        new Pruefpunkt(null, "Neuer Punkt zuerst", null),
                        new Pruefpunkt(erster.id(), "Zulassungsschild gut lesbar", null)), List.of()),
                new Abschnitt(datenAbschnitt.id(), WartungBereich.DATENFELDER, datenAbschnitt.label(), List.of(), List.of(
                        datenAbschnitt.datenfelder().get(1)))));
        WartungModulBearbeitenDto gespeichert = service.speichern(alt.id(), neu);
        flush();

        WartungModulBearbeitenDto gelesen = service.laden(alt.id());
        assertThat(gelesen.abschnitte().get(0).id()).isEqualTo(pruefAbschnitt.id());
        assertThat(gelesen.abschnitte().get(0).label()).isEqualTo("Prüfpunkte (neu benannt)");
        List<Pruefpunkt> punkte = gelesen.abschnitte().get(0).pruefpunkte();
        assertThat(punkte).extracting(Pruefpunkt::label).containsExactly("Neuer Punkt zuerst", "Zulassungsschild gut lesbar");
        assertThat(punkte.get(1).id()).isEqualTo(erster.id());
        assertThat(pruefpunktRepository.findById(entfernt.id())).isEmpty();
        assertThat(gelesen.abschnitte().get(1).datenfelder()).extracting(Datenfeld::label).containsExactly("Zustand");
        assertThat(gespeichert.version()).isGreaterThan(alt.version());
    }

    @Test
    @DisplayName("Ein Prüfpunkt lässt sich in einen anderen Abschnitt verschieben und behält dabei seine Nummer")
    void punktVerschieben() {
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());
        Pruefpunkt punkt = alt.abschnitte().get(0).pruefpunkte().get(0);
        Abschnitt a1 = alt.abschnitte().get(0);

        WartungModulBearbeitenDto neu = mit(alt, alt.untertypen(), List.of(
                new Abschnitt(a1.id(), WartungBereich.PRUEFPUNKTE, "Eins", List.of(a1.pruefpunkte().get(1)), List.of()),
                new Abschnitt(null, WartungBereich.PRUEFPUNKTE, "Zwei", List.of(punkt), List.of()),
                alt.abschnitte().get(1)));
        service.speichern(alt.id(), neu);
        flush();

        WartungModulBearbeitenDto gelesen = service.laden(alt.id());
        assertThat(gelesen.abschnitte().get(1).label()).isEqualTo("Zwei");
        assertThat(gelesen.abschnitte().get(1).pruefpunkte().get(0).id()).isEqualTo(punkt.id());
    }

    @Test
    @DisplayName("Speichern mit altem Stand ergibt 409, nichts wird überschrieben")
    void versionskonflikt() {
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());
        service.speichern(alt.id(), mit(alt, alt.untertypen(), alt.abschnitte()));
        flush();

        erwarte(() -> service.speichern(alt.id(), mitVersion(alt, alt.version(), alt.untertypen(), List.of())), HttpStatus.CONFLICT, "gerade geändert");
        assertThat(service.laden(alt.id()).abschnitte()).hasSize(2);
    }

    @Test
    @DisplayName("Ein Eintrag aus einem anderen Modul wird nicht angenommen")
    void fremdeNummer() {
        WartungModulBearbeitenDto anderes = service.anlegen(modul("Rauchschutztür", WartungModulKategorie.BASIS, List.of(), List.of(),
                List.of(new Abschnitt(null, WartungBereich.PRUEFPUNKTE, "Prüfpunkte", List.of(new Pruefpunkt(null, "Rauchdichtung", null)), List.of()))));
        flush();
        Pruefpunkt fremd = service.laden(anderes.id()).abschnitte().get(0).pruefpunkte().get(0);
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());

        erwarte(() -> service.speichern(alt.id(), mit(alt, alt.untertypen(), List.of(
                new Abschnitt(null, WartungBereich.PRUEFPUNKTE, "X", List.of(fremd), List.of())))), HttpStatus.BAD_REQUEST, "gelöscht");
        assertThat(service.laden(anderes.id()).abschnitte().get(0).pruefpunkte()).hasSize(1);
    }

    // ── Einträge ohne Abschnitt (mitgelieferte Datenfelder) ─────────────────

    private void legeVerwaisteEintraegeAn() {
        Long modulId = tuerModul.id();
        WartungModulDatenfeld ohne = new WartungModulDatenfeld();
        ohne.setModulId(modulId);
        ohne.setLabel("Feuerwiderstand / Rauchschutzklasse");
        ohne.setFeldtyp(WartungFeldtyp.TEXT);
        ohne.setSortierung(9);
        em.persist(ohne);
        // Ein Datenfeld unter einem Abschnitt für Prüfpunkte gehört auch nirgends hin
        WartungModulDatenfeld falsch = new WartungModulDatenfeld();
        falsch.setModulId(modulId);
        falsch.setAbschnittId(tuerModul.abschnitte().get(0).id());
        falsch.setLabel("Gebrauchsanweisung liegt vor");
        falsch.setFeldtyp(WartungFeldtyp.JA_NEIN);
        falsch.setSortierung(10);
        em.persist(falsch);
        WartungModulPruefpunkt punkt = new WartungModulPruefpunkt();
        punkt.setModulId(modulId);
        punkt.setLabel("Punkt ohne Abschnitt");
        punkt.setSortierung(99);
        em.persist(punkt);
        flush();
    }

    @Test
    @DisplayName("Einträge ohne passenden Abschnitt werden in eigenen Abschnitten gezeigt, die Zähler stimmen mit dem Editor überein")
    void verwaisteEintraegeSichtbar() {
        legeVerwaisteEintraegeAn();

        WartungModulBearbeitenDto gelesen = service.laden(tuerModul.id());

        assertThat(gelesen.abschnitte()).hasSize(4);
        Abschnitt daten = gelesen.abschnitte().get(3);
        assertThat(daten).extracting(Abschnitt::id, Abschnitt::bereich, Abschnitt::label).containsExactly(null, WartungBereich.DATENFELDER, "Datenaufnahme");
        assertThat(daten.datenfelder()).extracting(Datenfeld::label).containsExactly("Feuerwiderstand / Rauchschutzklasse", "Gebrauchsanweisung liegt vor");
        Abschnitt pruefung = gelesen.abschnitte().get(2);
        assertThat(pruefung).extracting(Abschnitt::id, Abschnitt::bereich, Abschnitt::label).containsExactly(null, WartungBereich.PRUEFPUNKTE, "Prüfpunkte");
        assertThat(pruefung.pruefpunkte()).extracting(Pruefpunkt::label).containsExactly("Punkt ohne Abschnitt");
        // Was der Editor zeigt, ist genau das, was die Liste zählt
        int sichtbareFelder = gelesen.abschnitte().stream().mapToInt(a -> a.datenfelder().size()).sum();
        int sichtbarePunkte = gelesen.abschnitte().stream().mapToInt(a -> a.pruefpunkte().size()).sum();
        var eintrag = service.alleModule().stream().filter(m -> m.id().equals(tuerModul.id())).findFirst().orElseThrow();
        assertThat(eintrag.anzahlDatenfelder()).isEqualTo(sichtbareFelder).isEqualTo(4);
        assertThat(eintrag.anzahlPruefpunkte()).isEqualTo(sichtbarePunkte).isEqualTo(3);
    }

    @Test
    @DisplayName("Speichern ohne Änderung löscht diese Einträge nicht, sondern legt ihre Abschnitte an")
    void verwaisteEintraegeBleibenErhalten() {
        legeVerwaisteEintraegeAn();
        WartungModulBearbeitenDto gelesen = service.laden(tuerModul.id());
        Set<Long> felderVorher = new HashSet<>();
        gelesen.abschnitte().forEach(a -> a.datenfelder().forEach(d -> felderVorher.add(d.id())));

        service.speichern(gelesen.id(), mit(gelesen, gelesen.untertypen(), gelesen.abschnitte()));
        flush();

        WartungModulBearbeitenDto danach = service.laden(gelesen.id());
        assertThat(danach.abschnitte()).hasSize(4);
        assertThat(danach.abschnitte()).allSatisfy(a -> assertThat(a.id()).isNotNull());
        Set<Long> felderDanach = new HashSet<>();
        danach.abschnitte().forEach(a -> a.datenfelder().forEach(d -> felderDanach.add(d.id())));
        assertThat(felderDanach).isEqualTo(felderVorher).hasSize(4);
        assertThat(datenfeldRepository.findByModulIdOrderBySortierungAscIdAsc(gelesen.id())).hasSize(4);
        assertThat(pruefpunktRepository.findByModulIdOrderBySortierungAscIdAsc(gelesen.id())).hasSize(3);
        assertThat(datenfeldRepository.findByModulIdOrderBySortierungAscIdAsc(gelesen.id())).allSatisfy(d -> assertThat(d.getAbschnittId()).isNotNull());
        assertThat(pruefpunktRepository.findByModulIdOrderBySortierungAscIdAsc(gelesen.id())).allSatisfy(p -> assertThat(p.getAbschnittId()).isNotNull());
    }

    // ── Prüfungen ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Dieselbe Nummer zweimal in einer Anfrage wird abgelehnt")
    void doppelteNummern() {
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());
        Pruefpunkt erster = alt.abschnitte().get(0).pruefpunkte().get(0);

        erwarte(() -> service.speichern(alt.id(), mit(alt, alt.untertypen(), List.of(
                new Abschnitt(alt.abschnitte().get(0).id(), WartungBereich.PRUEFPUNKTE, "P", List.of(erster, erster), List.of())))),
                HttpStatus.BAD_REQUEST, "doppelt");
        erwarte(() -> service.speichern(alt.id(), mit(alt, List.of(alt.untertypen().get(0), alt.untertypen().get(0)), alt.abschnitte())),
                HttpStatus.BAD_REQUEST, "doppelt");
    }

    @Test
    @DisplayName("Ein Prüfpunkt für eine nicht (mehr) vorhandene Bauart wird abgelehnt")
    void unbekannteBauart() {
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());

        erwarte(() -> service.speichern(alt.id(), mit(alt, List.of(alt.untertypen().get(0)), alt.abschnitte())),
                HttpStatus.BAD_REQUEST, "Bauart, die es nicht mehr gibt");
    }

    @Test
    @DisplayName("Eine Auswahl braucht Möglichkeiten, ohne Doppelte; Abschnitte vermischen keine Arten")
    void inhaltspruefungen() {
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());
        List<Untertyp> u = alt.untertypen();

        erwarte(() -> service.speichern(alt.id(), mit(alt, u, List.of(new Abschnitt(null, WartungBereich.DATENFELDER, "D", List.of(),
                List.of(new Datenfeld(null, "Zustand", WartungFeldtyp.AUSWAHL, List.of())))))), HttpStatus.BAD_REQUEST, "fehlen die Auswahlmöglichkeiten");
        erwarte(() -> service.speichern(alt.id(), mit(alt, u, List.of(new Abschnitt(null, WartungBereich.DATENFELDER, "D", List.of(),
                List.of(new Datenfeld(null, "Zustand", WartungFeldtyp.AUSWAHL, List.of("a", " a "))))))), HttpStatus.BAD_REQUEST, "doppelt");
        erwarte(() -> service.speichern(alt.id(), mit(alt, u, List.of(new Abschnitt(null, WartungBereich.PRUEFPUNKTE, "P", List.of(),
                List.of(new Datenfeld(null, "Hersteller", WartungFeldtyp.TEXT, null)))))), HttpStatus.BAD_REQUEST, "keine Datenfelder");
        erwarte(() -> service.speichern(alt.id(), mit(alt, List.of(new Untertyp(null, null, "A"), new Untertyp(null, null, " a ")), List.of())),
                HttpStatus.BAD_REQUEST, "doppelt");
    }

    @Test
    @DisplayName("Optionen werden nur bei einer Auswahl gespeichert")
    void optionenNurBeiAuswahl() {
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());

        service.speichern(alt.id(), mit(alt, alt.untertypen(), List.of(new Abschnitt(null, WartungBereich.DATENFELDER, "D", List.of(),
                List.of(new Datenfeld(null, "Freitext", WartungFeldtyp.TEXT, List.of("übrig")))))));
        flush();

        assertThat(service.laden(alt.id()).abschnitte().get(0).datenfelder().get(0).optionen()).isEmpty();
    }

    @Test
    @DisplayName("Der Schlüssel einer vorhandenen Bauart lässt sich nicht ändern")
    void schluesselFest() {
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());
        Untertyp u = alt.untertypen().get(0);

        erwarte(() -> service.speichern(alt.id(), mit(alt, List.of(new Untertyp(u.id(), "anders", "1-flügelig"), alt.untertypen().get(1)), alt.abschnitte())),
                HttpStatus.BAD_REQUEST, "lässt sich nicht ändern");
    }

    // ── Schutz benutzter Einträge ───────────────────────────────────────────

    @Test
    @DisplayName("Eine Bauart, die eine Tür verwendet, lässt sich nicht entfernen; eine freie schon")
    void benutzteBauartGeschuetzt() {
        tuerMit(tuerModul.id(), "2fluegelig");
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());
        List<Abschnitt> ohnePunktMitBauart = List.of(
                new Abschnitt(alt.abschnitte().get(0).id(), WartungBereich.PRUEFPUNKTE, "Prüfpunkte",
                        List.of(alt.abschnitte().get(0).pruefpunkte().get(0)), List.of()));

        erwarte(() -> service.speichern(alt.id(), mit(alt, List.of(alt.untertypen().get(0)), ohnePunktMitBauart)),
                HttpStatus.CONFLICT, "wird noch von 1 Tür verwendet");

        service.speichern(alt.id(), mit(alt, List.of(alt.untertypen().get(1)), ohnePunktMitBauart));
        flush();
        assertThat(service.laden(alt.id()).untertypen()).extracting(Untertyp::schluessel).containsExactly("2fluegelig");
    }

    @Test
    @DisplayName("Ein benutztes Modul ohne Bauart darf Bauarten bekommen: Die Tür wählt sie danach nach")
    void bauartNachtraeglichBeiBenutztemModul() {
        WartungModulBearbeitenDto ohneBauart = service.anlegen(modul("Rolltor", WartungModulKategorie.BASIS, List.of(), List.of(), List.of()));
        flush();
        tuerMit(ohneBauart.id(), null);
        WartungModulBearbeitenDto alt = service.laden(ohneBauart.id());

        service.speichern(alt.id(), mit(alt, List.of(new Untertyp(null, null, "Neu")), List.of()));
        flush();

        assertThat(service.laden(alt.id()).untertypen()).extracting(Untertyp::schluessel).containsExactly("neu");
    }

    @Test
    @DisplayName("Die Art eines benutzten Moduls ändert sich nicht")
    void kategorieBeiBenutztemModul() {
        tuerMit(tuerModul.id(), "1fluegelig");
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());
        WartungModulBearbeitenDto zusatz = new WartungModulBearbeitenDto(alt.id(), alt.version(), alt.modulKey(), alt.name(), null, null,
                WartungModulKategorie.ZUSATZ, false, true, 12, List.of(), alt.untertypen(), alt.abschnitte(), 0, 0);

        erwarte(() -> service.speichern(alt.id(), zusatz), HttpStatus.CONFLICT, "verwenden");
    }

    @Test
    @DisplayName("Ein Basis-Element, an das andere andocken, wird kein Zusatzmodul")
    void kategorieBeiAngedocktemBasis() {
        service.anlegen(modul("Feststellanlage", WartungModulKategorie.ZUSATZ, List.of(tuerModul.id()), List.of(), List.of()));
        flush();
        WartungModulBearbeitenDto alt = service.laden(tuerModul.id());
        WartungModulBearbeitenDto zusatz = new WartungModulBearbeitenDto(alt.id(), alt.version(), alt.modulKey(), alt.name(), null, null,
                WartungModulKategorie.ZUSATZ, false, true, 12, List.of(), alt.untertypen(), alt.abschnitte(), 0, 0);

        erwarte(() -> service.speichern(alt.id(), zusatz), HttpStatus.CONFLICT, "docken an dieses Basis-Element an");
    }

    // ── Andocken ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Andocken: nur Zusatzmodule, nur an vorhandene Basis-Elemente, nicht an sich selbst")
    void andocken() {
        erwarte(() -> service.anlegen(modul("Basis", WartungModulKategorie.BASIS, List.of(tuerModul.id()), List.of(), List.of())),
                HttpStatus.BAD_REQUEST, "Nur ein Zusatzmodul");
        erwarte(() -> service.anlegen(modul("Zusatz", WartungModulKategorie.ZUSATZ, List.of(987654L), List.of(), List.of())),
                HttpStatus.BAD_REQUEST, "gibt es nicht mehr");
        WartungModulBearbeitenDto zusatz = service.anlegen(modul("Zusatz", WartungModulKategorie.ZUSATZ, List.of(tuerModul.id()), List.of(), List.of()));
        flush();
        erwarte(() -> service.anlegen(modul("Zusatz 2", WartungModulKategorie.ZUSATZ, List.of(zusatz.id()), List.of(), List.of())),
                HttpStatus.BAD_REQUEST, "nur an Basis-Elemente");
        assertThat(service.laden(zusatz.id()).andockbarAn()).containsExactly(tuerModul.id());
    }

    // ── Löschen, Deaktivieren, Reihenfolge ──────────────────────────────────

    @Test
    @DisplayName("Ein unbenutztes Modul wird samt Inhalt gelöscht, der Verweis anderer Module darauf entfällt")
    void loeschen() {
        WartungModulBearbeitenDto zusatz = service.anlegen(modul("Feststellanlage", WartungModulKategorie.ZUSATZ, List.of(tuerModul.id()), List.of(), List.of()));
        flush();

        service.loeschen(tuerModul.id());
        flush();

        assertThat(modulRepository.findById(tuerModul.id())).isEmpty();
        assertThat(untertypRepository.findByModulIdOrderBySortierungAscIdAsc(tuerModul.id())).isEmpty();
        assertThat(abschnittRepository.findByModulIdOrderBySortierungAscIdAsc(tuerModul.id())).isEmpty();
        assertThat(pruefpunktRepository.findByModulIdOrderBySortierungAscIdAsc(tuerModul.id())).isEmpty();
        assertThat(datenfeldRepository.findByModulIdOrderBySortierungAscIdAsc(tuerModul.id())).isEmpty();
        assertThat(service.laden(zusatz.id()).andockbarAn()).isEmpty();
    }

    @Test
    @DisplayName("Benutzte und mitgelieferte Module lassen sich nicht löschen")
    void loeschenGeschuetzt() {
        tuerMit(tuerModul.id(), "1fluegelig");
        erwarte(() -> service.loeschen(tuerModul.id()), HttpStatus.CONFLICT, "noch benutzt");

        WartungModul system = new WartungModul();
        system.setModulKey("m_system");
        system.setName("Mitgeliefert");
        system.setSystemModul(true);
        em.persist(system);
        flush();
        erwarte(() -> service.loeschen(system.getId()), HttpStatus.CONFLICT, "mitgelieferte");
        assertThat(service.laden(tuerModul.id())).isNotNull();
    }

    @Test
    @DisplayName("Die Liste zählt Türen und Inhalt; Deaktivieren und Aktivieren ändern nur den Schalter")
    void zaehlerUndAktiv() {
        tuerMit(tuerModul.id(), "1fluegelig");
        tuerMit(tuerModul.id(), "2fluegelig");

        var eintrag = service.alleModule().get(0);
        assertThat(eintrag.anzahlTueren()).isEqualTo(2);
        assertThat(eintrag.anzahlUntertypen()).isEqualTo(2);
        assertThat(eintrag.anzahlPruefpunkte()).isEqualTo(2);
        assertThat(eintrag.anzahlDatenfelder()).isEqualTo(2);

        assertThat(service.aktivSetzen(tuerModul.id(), false).aktiv()).isFalse();
        assertThat(service.aktivSetzen(tuerModul.id(), true).aktiv()).isTrue();
    }

    @Test
    @DisplayName("Die Reihenfolge verlangt genau die vorhandenen Module")
    void reihenfolge() {
        WartungModulBearbeitenDto b = service.anlegen(modul("B", WartungModulKategorie.BASIS, List.of(), List.of(), List.of()));
        WartungModulBearbeitenDto c = service.anlegen(modul("C", WartungModulKategorie.BASIS, List.of(), List.of(), List.of()));
        flush();

        erwarte(() -> service.reihenfolgeSetzen(List.of(c.id(), b.id())), HttpStatus.BAD_REQUEST, "passt nicht");
        erwarte(() -> service.reihenfolgeSetzen(List.of(c.id(), c.id(), b.id())), HttpStatus.BAD_REQUEST, "passt nicht");

        var liste = service.reihenfolgeSetzen(List.of(c.id(), tuerModul.id(), b.id()));
        assertThat(liste).extracting("id").containsExactly(c.id(), tuerModul.id(), b.id());
    }

    // ── Globale Datenfelder ─────────────────────────────────────────────────

    @Test
    @DisplayName("Globale Felder: anlegen, umbenennen, umsortieren, löschen")
    void globaleFelder() {
        service.globaleFelderSpeichern(List.of(
                new WartungGlobalesDatenfeldDto(null, "Standort", WartungFeldtyp.TEXT, null),
                new WartungGlobalesDatenfeldDto(null, "Hersteller", WartungFeldtyp.TEXT, null),
                new WartungGlobalesDatenfeldDto(null, "Zulassung", WartungFeldtyp.TEXT, null)));
        flush();
        List<WartungGlobalesDatenfeldDto> felder = service.globaleFelder();
        assertThat(felder).extracting(WartungGlobalesDatenfeldDto::label).containsExactly("Standort", "Hersteller", "Zulassung");

        service.globaleFelderSpeichern(List.of(
                felder.get(2),
                new WartungGlobalesDatenfeldDto(felder.get(0).id(), "Standort / Lage", WartungFeldtyp.JA_NEIN, List.of("egal"))));
        flush();

        List<WartungGlobalesDatenfeldDto> danach = service.globaleFelder();
        assertThat(danach).extracting(WartungGlobalesDatenfeldDto::label).containsExactly("Zulassung", "Standort / Lage");
        assertThat(danach.get(1).id()).isEqualTo(felder.get(0).id());
        assertThat(danach.get(1).optionen()).isEmpty();
    }

    @Test
    @DisplayName("Globale Felder: doppelte Bezeichnungen und unbekannte Nummern werden abgelehnt")
    void globaleFelderPruefung() {
        erwarte(() -> service.globaleFelderSpeichern(List.of(
                new WartungGlobalesDatenfeldDto(null, "Standort", WartungFeldtyp.TEXT, null),
                new WartungGlobalesDatenfeldDto(null, " standort ", WartungFeldtyp.TEXT, null))), HttpStatus.BAD_REQUEST, "doppelt");
        erwarte(() -> service.globaleFelderSpeichern(List.of(new WartungGlobalesDatenfeldDto(999L, "X", WartungFeldtyp.TEXT, null))),
                HttpStatus.BAD_REQUEST, "gelöscht");
        erwarte(() -> service.globaleFelderSpeichern(List.of(new WartungGlobalesDatenfeldDto(null, null, WartungFeldtyp.TEXT, null))),
                HttpStatus.BAD_REQUEST, "benennen");
        erwarte(() -> service.globaleFelderSpeichern(List.of(new WartungGlobalesDatenfeldDto(null, "X", null, null))),
                HttpStatus.BAD_REQUEST, "benennen");
        erwarte(() -> service.globaleFelderSpeichern(List.of(new WartungGlobalesDatenfeldDto(null, "x".repeat(201), WartungFeldtyp.TEXT, null))),
                HttpStatus.BAD_REQUEST, "zu lang");
    }

    // ── Hilfen ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Schlüssel entstehen aus Kleinbuchstaben und Ziffern, Umlaute werden umschrieben")
    void slug() {
        assertThat(WartungModulAdminService.slug("2-flügelig", 30)).isEqualTo("2fluegelig");
        assertThat(WartungModulAdminService.slug("Große Straße!", 30)).isEqualTo("grossestrasse");
        assertThat(WartungModulAdminService.slug("???", 30)).isEqualTo("x");
        assertThat(WartungModulAdminService.slug("a".repeat(100), 10)).hasSize(10);
    }
}
