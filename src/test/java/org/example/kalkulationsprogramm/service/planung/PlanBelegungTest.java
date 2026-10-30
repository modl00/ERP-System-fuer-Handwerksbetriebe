package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.service.planung.PlanBelegung.Antwort;
import org.example.kalkulationsprogramm.service.planung.PlanBelegung.Eintrag;
import org.example.kalkulationsprogramm.service.planung.PlanBelegung.Zeile;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangEingabe;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung.Zuweisung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

/** Belegung der Plantafel (#323): Vorgänge, Termine und Abwesenheiten je Mitarbeiter und Ressource, mit Hinweisen. Nur Dummy-Daten. */
@DataJpaTest
@Import({ PlanungsService.class, PlanKapazitaetQuelle.class, org.example.kalkulationsprogramm.service.planung.PlanungsIst.class, org.example.kalkulationsprogramm.service.planung.PlanungsHistorie.class, PlanungsEinstellungen.class, PlanungsZuweisung.class, PlanBelegung.class })
class PlanBelegungTest {

    @MockitoBean
    private AngebotsZeiten angebotsZeiten;

    @MockitoBean
    private org.example.kalkulationsprogramm.service.TagesSollService tagesSollService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.FeiertagService feiertagService;
    @Autowired
    private PlanungsService planung;
    @Autowired
    private PlanungsZuweisung zuweisung;
    @Autowired
    private PlanBelegung belegung;
    @Autowired
    private PlanungsEinstellungen planungsEinstellungen;
    @Autowired
    private TestEntityManager em;

    private Long typ;
    private Long max;
    private Long erika;
    private Long werkstatt;
    private Long lkw;

    // Montag 2026-10-12
    private static final LocalDate MO = LocalDate.of(2026, 10, 12);
    private static final LocalDate SO = LocalDate.of(2026, 10, 18);

    @BeforeEach
    void setUp() {
        typ = planung.typSpeichern(null, "Fertigung", "#be123c", 30, 3, true, null, null).id();
        max = mitarbeiter("Max", "Mustermann");
        erika = mitarbeiter("Erika", "Musterfrau");
        werkstatt = zuweisung.ressourceSpeichern(null, "Werkstatt", "ORT", 20, null).id();
        lkw = zuweisung.ressourceSpeichern(null, "Lkw", "FAHRZEUG", 30, null).id();
    }

    private Long mitarbeiter(String vor, String nach) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(vor);
        m.setNachname(nach);
        return em.persistAndFlush(m).getId();
    }

    private Long vorgang(String auftrag, LocalDate festerStart, int tage, List<Long> personen, Long ressource) {
        Projekt p = new Projekt();
        p.setBauvorhaben(auftrag);
        p.setAuftragsnummer("A-" + auftrag);
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        Long pid = em.persistAndFlush(p).getId();
        Long vid = planung.vorgangAnlegen(pid, new VorgangEingabe(typ, auftrag + " fertigen", tage, festerStart, null, null)).vorgaenge().get(0).id();
        zuweisung.zuweisen(vid, new Zuweisung(personen, ressource, null));
        return vid;
    }

    private Zeile zeile(Antwort a, String schluessel) {
        return a.zeilen().stream().filter(z -> z.schluessel().equals(schluessel)).findFirst().orElseThrow();
    }

    @Test
    void ZeilenSindAlleMitarbeiterUndRessourcenAuchOhneEintraege() {
        Antwort a = belegung.belegung(MO, SO);

        assertThat(a.zeilen()).extracting(Zeile::schluessel).containsExactly("M" + erika, "M" + max, "R" + werkstatt, "R" + lkw);
        assertThat(a.zeilen()).extracting(Zeile::gruppe).containsExactly("MITARBEITER", "MITARBEITER", "RESSOURCE", "RESSOURCE");
    }

    @Test
    void einZugewiesenerVorgangErscheintBeiMitarbeiterUndRessourceEinNichtZugewiesenerInDerAllgemeinZeile() {
        vorgang("Geländer", MO, 2, List.of(max), werkstatt);
        vorgang("Tor", MO, 2, List.of(), null);

        Antwort a = belegung.belegung(MO, SO);

        assertThat(zeile(a, "M" + max).eintraege()).extracting(Eintrag::titel).containsExactly("Geländer fertigen");
        assertThat(zeile(a, "R" + werkstatt).eintraege()).extracting(Eintrag::titel).containsExactly("Geländer fertigen");
        assertThat(zeile(a, "A").eintraege()).extracting(Eintrag::titel).containsExactly("Tor fertigen");
        Eintrag e = zeile(a, "M" + max).eintraege().get(0);
        assertThat(e.quelle()).isEqualTo("VORGANG");
        assertThat(e.von()).isEqualTo(MO);
        assertThat(e.bis()).isEqualTo(MO.plusDays(1));
        assertThat(e.fest()).isTrue();
        assertThat(e.untertitel()).contains("Geländer").contains("A-Geländer");
    }

    @Test
    void vorgaengeAusserhalbDesZeitraumsFehlen() {
        vorgang("Spaeterauftrag", MO.plusDays(60), 2, List.of(max), null);

        assertThat(zeile(belegung.belegung(MO, SO), "M" + max).eintraege()).isEmpty();
    }

    @Test
    void zweiVorgaengeDerselbenPersonZurGleichenZeitSindEinHinweisAberEineWerkstattNimmtMehrereAuf() {
        vorgang("Geländer", MO, 3, List.of(max), werkstatt);
        vorgang("Tor", MO.plusDays(1), 3, List.of(max), werkstatt);

        Antwort a = belegung.belegung(MO, SO);

        assertThat(zeile(a, "M" + max).eintraege()).allSatisfy(e -> assertThat(e.hinweis()).startsWith("Doppelt belegt mit"));
        assertThat(zeile(a, "R" + werkstatt).eintraege()).allSatisfy(e -> assertThat(e.hinweis()).isNull());
    }

    @Test
    void einFahrzeugDasZweimalGebuchtIstWirdGemeldet() {
        vorgang("Geländer", MO, 2, List.of(), lkw);
        vorgang("Tor", MO, 2, List.of(), lkw);

        assertThat(zeile(belegung.belegung(MO, SO), "R" + lkw).eintraege()).allSatisfy(e -> assertThat(e.hinweis()).startsWith("Doppelt belegt"));
    }

    @Test
    void hintereinanderLiegendeVorgaengeSindKeinHinweis() {
        vorgang("Geländer", MO, 2, List.of(max), null);
        vorgang("Tor", MO.plusDays(2), 2, List.of(max), null);

        assertThat(zeile(belegung.belegung(MO, SO), "M" + max).eintraege()).allSatisfy(e -> assertThat(e.hinweis()).isNull());
    }

    @Test
    void einVorgangInEinerAbwesenheitIstEinHinweisAufeinanderfolgendeTageSindEinEintrag() {
        for (int i = 1; i <= 3; i++) {
            Abwesenheit ab = new Abwesenheit();
            ab.setMitarbeiter(em.find(Mitarbeiter.class, max));
            ab.setTyp(AbwesenheitsTyp.URLAUB);
            ab.setDatum(MO.plusDays(i));
            ab.setStunden(new BigDecimal("8"));
            em.persist(ab);
        }
        em.flush();
        vorgang("Geländer", MO.plusDays(2), 2, List.of(max), null);

        List<Eintrag> liste = zeile(belegung.belegung(MO, SO), "M" + max).eintraege();

        assertThat(liste).extracting(Eintrag::quelle).containsExactlyInAnyOrder("ABWESENHEIT", "VORGANG");
        Eintrag urlaub = liste.stream().filter(e -> e.quelle().equals("ABWESENHEIT")).findFirst().orElseThrow();
        assertThat(urlaub.titel()).isEqualTo("Urlaub");
        assertThat(urlaub.von()).isEqualTo(MO.plusDays(1));
        assertThat(urlaub.bis()).isEqualTo(MO.plusDays(3));
        assertThat(liste.stream().filter(e -> e.quelle().equals("VORGANG")).findFirst().orElseThrow().hinweis()).isEqualTo("Eingeplant, obwohl: Urlaub");
    }

    @Test
    void kalenderterminErscheinenBeiDenTeilnehmernOhneTeilnehmerInDerAllgemeinZeileUndOhneHinweis() {
        KalenderEintrag k = new KalenderEintrag();
        k.setTitel("Aufmaß Familie Muster");
        k.setDatum(MO.plusDays(1));
        k.setStartZeit(LocalTime.of(8, 30));
        k.setEndeZeit(LocalTime.of(10, 0));
        k.setErstelltAm(java.time.LocalDateTime.now());
        k.getTeilnehmer().add(em.find(Mitarbeiter.class, erika));
        em.persist(k);
        KalenderEintrag firma = new KalenderEintrag();
        firma.setTitel("Betriebsversammlung");
        firma.setDatum(MO.plusDays(2));
        firma.setGanztaegig(true);
        firma.setErstelltAm(java.time.LocalDateTime.now());
        em.persist(firma);
        em.flush();
        vorgang("Geländer", MO, 5, List.of(erika), null);

        Antwort a = belegung.belegung(MO, SO);

        Eintrag termin = zeile(a, "M" + erika).eintraege().stream().filter(e -> e.quelle().equals("TERMIN")).findFirst().orElseThrow();
        assertThat(termin.vonZeit()).isEqualTo(LocalTime.of(8, 30));
        assertThat(termin.hinweis()).as("ein Termin neben einem Vorgang ist normal").isNull();
        assertThat(zeile(a, "A").eintraege()).extracting(Eintrag::titel).containsExactly("Betriebsversammlung");
        assertThat(zeile(a, "A").eintraege().get(0).vonZeit()).as("ganztägig = ohne Uhrzeit").isNull();
    }

    @Test
    void einMehrtaegigerTerminStehtAlsBalkenUndErscheintAuchWennerVorDemZeitraumBeginnt() {
        KalenderEintrag messe = new KalenderEintrag();
        messe.setTitel("Messe Nürnberg");
        messe.setDatum(MO.minusDays(2));
        messe.setDatumBis(MO.plusDays(1));
        messe.setGanztaegig(true);
        messe.setErstelltAm(java.time.LocalDateTime.now());
        messe.getTeilnehmer().add(em.find(Mitarbeiter.class, erika));
        em.persist(messe);
        KalenderEintrag spaeter = new KalenderEintrag();
        spaeter.setTitel("Weit weg");
        spaeter.setDatum(SO.plusDays(3));
        spaeter.setDatumBis(SO.plusDays(5));
        spaeter.setGanztaegig(true);
        spaeter.setErstelltAm(java.time.LocalDateTime.now());
        em.persist(spaeter);
        em.flush();

        Antwort a = belegung.belegung(MO, SO);

        Eintrag e = zeile(a, "M" + erika).eintraege().stream().filter(x -> x.quelle().equals("TERMIN")).findFirst().orElseThrow();
        assertThat(e.von()).isEqualTo(MO.minusDays(2));
        assertThat(e.bis()).isEqualTo(MO.plusDays(1));
        assertThat(e.vonZeit()).isNull();
        assertThat(a.zeilen()).flatExtracting(z -> z.eintraege()).extracting(Eintrag::titel).doesNotContain("Weit weg");
    }

    @Test
    void fertigeVorgaengeMeldenKeinenHinweis() {
        Long v1 = vorgang("Geländer", MO, 3, List.of(max), null);
        vorgang("Tor", MO, 3, List.of(max), null);
        planung.vorgangAendern(v1, new VorgangEingabe(null, null, null, MO, "FERTIG", null));

        assertThat(zeile(belegung.belegung(MO, SO), "M" + max).eintraege()).allSatisfy(e -> assertThat(e.hinweis()).isNull());
    }

    @Test
    void ZeitraumWirdGeprueft() {
        assertThatThrownBy(() -> belegung.belegung(SO, MO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> belegung.belegung(MO, MO.plusDays(PlanBelegung.MAX_TAGE + 1))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("höchstens");
        assertThatThrownBy(() -> belegung.belegung(null, MO)).isInstanceOf(IllegalArgumentException.class);
    }

    @org.junit.jupiter.api.Test
    void feiertageUndBetriebsurlaubStehenAlsFreieTageInDerAntwort() {
        org.example.kalkulationsprogramm.domain.Feiertag f = new org.example.kalkulationsprogramm.domain.Feiertag();
        f.setDatum(MO.plusDays(2));
        f.setBezeichnung("Testfeiertag");
        f.setBundesland("BY");
        em.persistAndFlush(f);
        org.example.kalkulationsprogramm.domain.Feiertag halb = new org.example.kalkulationsprogramm.domain.Feiertag();
        halb.setDatum(MO.plusDays(3));
        halb.setBezeichnung("Halber Tag");
        halb.setBundesland("BY");
        halb.setHalbTag(true);
        em.persistAndFlush(halb);
        planungsEinstellungen.speichern(java.util.EnumSet.range(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY), 2,
                java.util.List.of(new PlanungsEinstellungen.Urlaub(MO.plusDays(4), MO.plusDays(5), "Sommerpause")));

        var tage = belegung.belegung(MO, SO).freieTage();

        assertThat(tage).extracting(PlanBelegung.FreierTag::datum, PlanBelegung.FreierTag::art, PlanBelegung.FreierTag::name)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(MO.plusDays(2), "FEIERTAG", "Testfeiertag"), org.assertj.core.groups.Tuple.tuple(MO.plusDays(4), "BETRIEBSURLAUB", "Sommerpause"),
                        org.assertj.core.groups.Tuple.tuple(MO.plusDays(5), "BETRIEBSURLAUB", "Sommerpause"));
    }

    @org.junit.jupiter.api.Test
    void eineFaelligeWartungStehtInDerZeileFaelligeWartungenNurImZeitraum() {
        org.example.kalkulationsprogramm.domain.Kunde kunde = new org.example.kalkulationsprogramm.domain.Kunde();
        kunde.setKundennummer("K-1");
        kunde.setName("Muster GmbH");
        em.persist(kunde);
        org.example.kalkulationsprogramm.domain.wartung.WartungObjekt objekt = new org.example.kalkulationsprogramm.domain.wartung.WartungObjekt();
        objekt.setKunde(kunde);
        objekt.setBezeichnung("Halle 1");
        em.persist(objekt);
        wartungstuer(objekt, "Rolltor Nord", MO.plusDays(1), true);
        wartungstuer(objekt, "Rolltor Süd", MO.plusDays(40), true);
        wartungstuer(objekt, "Altes Tor", MO.plusDays(2), false);
        em.flush();

        var zeile = zeile(belegung.belegung(MO, SO), "W");

        assertThat(zeile.name()).isEqualTo("Fällige Wartungen");
        assertThat(zeile.eintraege()).extracting(PlanBelegung.Eintrag::quelle, PlanBelegung.Eintrag::titel, PlanBelegung.Eintrag::von, PlanBelegung.Eintrag::untertitel)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("WARTUNG", "Rolltor Nord (Wartung)", MO.plusDays(1), "Halle 1"));
    }

    private void wartungstuer(org.example.kalkulationsprogramm.domain.wartung.WartungObjekt objekt, String name, LocalDate faellig, boolean aktiv) {
        org.example.kalkulationsprogramm.domain.wartung.WartungTuer t = new org.example.kalkulationsprogramm.domain.wartung.WartungTuer();
        t.setObjekt(objekt);
        t.setBezeichnung(name);
        t.setNaechsteFaelligkeit(faellig);
        t.setAktiv(aktiv);
        em.persist(t);
    }

    @org.junit.jupiter.api.Test
    void einLangerZeitraumBisZweiJahreIstErlaubt() {
        assertThat(belegung.belegung(MO, MO.plusDays(730)).zeilen()).isNotEmpty();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> belegung.belegung(MO, MO.plusDays(900))).hasMessageContaining("höchstens");
    }

    @org.junit.jupiter.api.Test
    void mehrereFaelligeAnlagenEinesObjektsSindEinEintrag() {
        org.example.kalkulationsprogramm.domain.Kunde kunde = new org.example.kalkulationsprogramm.domain.Kunde();
        kunde.setKundennummer("K-2");
        kunde.setName("Beispiel AG");
        em.persist(kunde);
        org.example.kalkulationsprogramm.domain.wartung.WartungObjekt halle = new org.example.kalkulationsprogramm.domain.wartung.WartungObjekt();
        halle.setKunde(kunde);
        halle.setBezeichnung("Halle 2");
        em.persist(halle);
        org.example.kalkulationsprogramm.domain.wartung.WartungObjekt lager = new org.example.kalkulationsprogramm.domain.wartung.WartungObjekt();
        lager.setKunde(kunde);
        lager.setBezeichnung("Lager");
        em.persist(lager);
        wartungstuer(halle, "Tor 1", MO.plusDays(1), true);
        wartungstuer(halle, "Tor 2", MO.plusDays(3), true);
        wartungstuer(halle, "Tor 3", MO.plusDays(2), true);
        wartungstuer(halle, "Tor 4", MO.plusDays(4), true);
        wartungstuer(lager, "Rolltor", MO.plusDays(2), true);
        em.flush();

        var eintraege = zeile(belegung.belegung(MO, SO), "W").eintraege();

        assertThat(eintraege).hasSize(2);
        var objekt = eintraege.stream().filter(e -> e.id().startsWith("O")).findFirst().orElseThrow();
        assertThat(objekt.titel()).isEqualTo("Halle 2: 4 Anlagen fällig");
        assertThat(objekt.von()).isEqualTo(MO.plusDays(1));
        assertThat(objekt.bis()).isEqualTo(MO.plusDays(4));
        assertThat(objekt.untertitel()).isEqualTo("Tor 1, Tor 3, Tor 2 …");
        assertThat(eintraege.stream().filter(e -> e.id().startsWith("W")).findFirst().orElseThrow().titel()).isEqualTo("Rolltor (Wartung)");
    }
}
