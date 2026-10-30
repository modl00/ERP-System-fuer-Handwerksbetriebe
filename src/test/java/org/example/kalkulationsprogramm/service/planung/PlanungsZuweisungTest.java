package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangEingabe;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung.RessourceDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsZuweisung.Zuweisung;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

/** Zuweisung von Mitarbeitern, Ressource und Priorität zu einem Vorgang (#323). Nur Dummy-Daten. */
@DataJpaTest
@Import({ PlanungsService.class, PlanKapazitaetQuelle.class, org.example.kalkulationsprogramm.service.planung.PlanungsIst.class, org.example.kalkulationsprogramm.service.planung.PlanungsHistorie.class, PlanungsEinstellungen.class, PlanungsZuweisung.class })
class PlanungsZuweisungTest {

    @MockitoBean
    private AngebotsZeiten angebotsZeiten;

    @MockitoBean
    private org.example.kalkulationsprogramm.service.TagesSollService tagesSollService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.FeiertagService feiertagService;
    @Autowired
    private PlanungsService planung;
    @Autowired
    private PlanungsZuweisung service;
    @Autowired
    private TestEntityManager em;

    private Long projektId;
    private Long vorgangId;
    private Long mitarbeiterA;
    private Long mitarbeiterB;

    @BeforeEach
    void setUp() {
        Projekt p = new Projekt();
        p.setBauvorhaben("Geländer Musterstraße");
        p.setAuftragsnummer("A-2026-001");
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        projektId = em.persistAndFlush(p).getId();
        Long typ = planung.typSpeichern(null, "Fertigung", "#be123c", 30, 5, true, null, null).id();
        vorgangId = planung.vorgangAnlegen(projektId, new VorgangEingabe(typ, null, null, null, null, null)).vorgaenge().get(0).id();
        mitarbeiterA = mitarbeiter("Max", "Mustermann");
        mitarbeiterB = mitarbeiter("Erika", "Musterfrau");
    }

    private Long mitarbeiter(String vor, String nach) {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname(vor);
        m.setNachname(nach);
        return em.persistAndFlush(m).getId();
    }

    @Test
    void einNeuerVorgangHatKeineZuweisungUndNormalePrioritaet() {
        var v = planung.auftrag(projektId).vorgaenge().get(0);

        assertThat(v.mitarbeiterIds()).isEmpty();
        assertThat(v.ressourceId()).isNull();
        assertThat(v.prioritaet()).isEqualTo("NORMAL");
    }

    @Test
    void mitarbeiterRessourceUndPrioritaetWerdenGespeichertUndMitgeliefert() {
        RessourceDto werkstatt = service.ressourceSpeichern(null, "Werkstatt", "ORT", 20, null);

        AuftragDto a = service.zuweisen(vorgangId, new Zuweisung(List.of(mitarbeiterB, mitarbeiterA), werkstatt.id(), "dringend"));

        var v = a.vorgaenge().get(0);
        assertThat(v.mitarbeiterIds()).containsExactlyInAnyOrder(mitarbeiterA, mitarbeiterB);
        assertThat(v.ressourceId()).isEqualTo(werkstatt.id());
        assertThat(v.prioritaet()).isEqualTo("DRINGEND");
    }

    @Test
    void eineZweiteZuweisungErsetztDieErsteUndLeerHebtSieAuf() {
        service.zuweisen(vorgangId, new Zuweisung(List.of(mitarbeiterA, mitarbeiterB), null, "DRINGEND"));

        var v = service.zuweisen(vorgangId, new Zuweisung(List.of(mitarbeiterB), null, null)).vorgaenge().get(0);
        assertThat(v.mitarbeiterIds()).containsExactly(mitarbeiterB);
        assertThat(v.prioritaet()).as("ohne Angabe bleibt die Priorität").isEqualTo("DRINGEND");

        assertThat(service.zuweisen(vorgangId, new Zuweisung(List.of(), null, null)).vorgaenge().get(0).mitarbeiterIds()).isEmpty();
    }

    @Test
    void dieZuweisungAendertKeineTermine() {
        LocalDate vorher = planung.auftrag(projektId).vorgaenge().get(0).ende();

        service.zuweisen(vorgangId, new Zuweisung(List.of(mitarbeiterA), null, "DRINGEND"));

        assertThat(planung.auftrag(projektId).vorgaenge().get(0).ende()).isEqualTo(vorher);
    }

    @Test
    void unbekannteMitarbeiterRessourcenOderPrioritaetenWerdenMitKlartextAbgelehnt() {
        assertThatThrownBy(() -> service.zuweisen(vorgangId, new Zuweisung(List.of(999999L), null, null))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Mitarbeiter");
        assertThatThrownBy(() -> service.zuweisen(vorgangId, new Zuweisung(List.of(), 999999L, null))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Ressource");
        assertThatThrownBy(() -> service.zuweisen(vorgangId, new Zuweisung(List.of(), null, "WICHTIG"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Priorität");
    }

    @Test
    void eineAusgeblendeteRessourceKannNichtNeuGewaehltWerden() {
        RessourceDto r = service.ressourceSpeichern(null, "Alter Lkw", "FAHRZEUG", 5, null);
        service.ressourceSpeichern(r.id(), null, null, null, false);

        assertThatThrownBy(() -> service.zuweisen(vorgangId, new Zuweisung(List.of(), r.id(), null))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ausgeblendet");
    }

    @Test
    void ressourcenBrauchenEinenEindeutigenNamenUndEineGueltigeArt() {
        service.ressourceSpeichern(null, "Werkstatt", "ORT", 20, null);

        assertThatThrownBy(() -> service.ressourceSpeichern(null, " werkstatt ", "ORT", null, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("gibt es schon");
        assertThatThrownBy(() -> service.ressourceSpeichern(null, "   ", "ORT", null, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Namen");
        assertThatThrownBy(() -> service.ressourceSpeichern(null, "Kran", "KRAN", null, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Art");
        assertThat(service.ressourcen()).extracting(RessourceDto::name).contains("Werkstatt");
    }

    @Test
    void dieAuswahlListeEnthaeltNurAktiveMitarbeiterMitNamenSortiertUndOhneLohn() {
        Mitarbeiter weg = new Mitarbeiter();
        weg.setVorname("Otto");
        weg.setNachname("Ausgeschieden");
        weg.setAktiv(false);
        em.persistAndFlush(weg);

        assertThat(service.personen()).extracting(PlanungsZuweisung.PersonDto::name).containsExactly("Erika Musterfrau", "Max Mustermann");
    }

    @Test
    void einUnbekannterVorgangIstEin404() {
        assertThatThrownBy(() -> service.zuweisen(424242L, new Zuweisung(List.of(), null, null))).hasMessageContaining("Vorgang");
    }
    private Long zweiterAuftrag(String name, String prio, int stunden, Long mitarbeiter) {
        Projekt p = new Projekt();
        p.setBauvorhaben(name);
        p.setAuftragsnummer("A-" + name);
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        Long pid = em.persistAndFlush(p).getId();
        Long typ = planung.typen().get(0).id();
        Long vid = planung.vorgangAnlegen(pid, new VorgangEingabe(typ, name, null, null, null, null, new BigDecimal(stunden))).vorgaenge().get(0).id();
        service.zuweisen(vid, new PlanungsZuweisung.Zuweisung(java.util.List.of(mitarbeiter), null, prio));
        return pid;
    }

    @Test
    void einDringenderAuftragNimmtDemNormalenDieZeitWegUndDasStehtImHinweis() {
        Long normal = zweiterAuftrag("Normal", "NORMAL", 16, mitarbeiterA);
        var vorher = planung.auftrag(normal).vorgaenge().get(0);
        assertThat(vorher.aufwandStunden()).isEqualByComparingTo("16");
        assertThat(vorher.aufwandHerkunft()).isEqualTo("VON_HAND");

        Long dringend = zweiterAuftrag("Eilig", "DRINGEND", 16, mitarbeiterA);

        var eilig = planung.auftrag(dringend).vorgaenge().get(0);
        var nachher = planung.auftrag(normal).vorgaenge().get(0);
        assertThat(nachher.start()).as("der normale Auftrag wartet auf den dringenden").isAfterOrEqualTo(eilig.ende());
        assertThat(nachher.ende()).isAfter(vorher.ende());
        assertThat(nachher.hinweis()).contains("Mustermann").contains("Eilig (dringend)");
        assertThat(eilig.ohneZuweisungGeschaetzt()).isFalse();
    }

    @Test
    void ohneZuweisungRechnetMitDemStandardwertUndSagtGeschaetzt() {
        var v = planung.auftrag(projektId).vorgaenge().get(0);

        assertThat(v.ohneZuweisungGeschaetzt()).isTrue();
        assertThat(v.aufwandStunden()).as("fünf Tage mal Standard-Arbeitstag").isEqualByComparingTo("40");
        assertThat(v.dauerTage()).isEqualTo(5);
    }

    @Test
    void zweiPersonenSchaffenDenAufwandInDerHaelfteDerTage() {
        var einer = service.zuweisen(vorgangId, new PlanungsZuweisung.Zuweisung(java.util.List.of(mitarbeiterA), null, null)).vorgaenge().get(0);
        var zwei = service.zuweisen(vorgangId, new PlanungsZuweisung.Zuweisung(java.util.List.of(mitarbeiterA, mitarbeiterB), null, null)).vorgaenge().get(0);

        assertThat(einer.dauerTage()).isEqualTo(5);
        assertThat(zwei.dauerTage()).isEqualTo(3);
        assertThat(zwei.ohneZuweisungGeschaetzt()).isFalse();
    }

    @Test
    void frueheestensAbVerschiebtDenBeginnOhneFixtermin() {
        LocalDate spaeter = LocalDate.now().plusDays(21);
        while (spaeter.getDayOfWeek().getValue() > 5) {
            spaeter = spaeter.plusDays(1);
        }

        var v = planung.fruehestensSetzen(vorgangId, spaeter).vorgaenge().get(0);

        assertThat(v.start()).isEqualTo(spaeter);
        assertThat(v.fruehestensAb()).isEqualTo(spaeter);
        assertThat(v.festerStart()).as("das Ziehen setzt keinen Fixtermin").isNull();
        assertThat(planung.fruehestensSetzen(vorgangId, null).vorgaenge().get(0).fruehestensAb()).isNull();
    }

    @Test
    void dieReihenfolgeDesPlanersEntscheidetUnterGleichWichtigen() {
        Long erster = zweiterAuftrag("Erster", "NORMAL", 8, mitarbeiterA);
        Long zweiter = zweiterAuftrag("Zweiter", "NORMAL", 8, mitarbeiterA);
        Long vErster = planung.auftrag(erster).vorgaenge().get(0).id();
        Long vZweiter = planung.auftrag(zweiter).vorgaenge().get(0).id();

        planung.rangSetzen(vZweiter, 1);
        planung.rangSetzen(vErster, 2);

        assertThat(planung.auftrag(zweiter).vorgaenge().get(0).start()).isBefore(planung.auftrag(erster).vorgaenge().get(0).start());
        assertThatThrownBy(() -> planung.rangSetzen(vErster, -1)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Reihenfolge");
    }
}
