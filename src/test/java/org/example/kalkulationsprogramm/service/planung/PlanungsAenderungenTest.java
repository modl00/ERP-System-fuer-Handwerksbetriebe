package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.service.TagesSollService;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangEingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;

/** Änderungsliste, Rückgängig, Schutz vor gleichzeitigem Bearbeiten und Vorschau „was ändert sich“ (#331, Stufe 4). Nur Dummy-Daten. */
@DataJpaTest
@Import({ PlanungsService.class, PlanKapazitaetQuelle.class, org.example.kalkulationsprogramm.service.planung.PlanungsIst.class, PlanungsHistorie.class, PlanungsEinstellungen.class, PlanungsZuweisung.class })
class PlanungsAenderungenTest {

    @MockBean
    private AngebotsZeiten angebotsZeiten;
    @MockBean
    private TagesSollService tagesSollService;
    @Autowired
    private PlanungsService planung;
    @Autowired
    private PlanungsZuweisung zuweisung;
    @Autowired
    private PlanungsHistorie historie;
    @Autowired
    private TestEntityManager em;

    private Long typ;
    private Long max;
    private Long projektId;
    private Long vorgangId;

    @BeforeEach
    void setUp() {
        typ = planung.typSpeichern(null, "Fertigung", "#be123c", 30, 3, true, null, null).id();
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname("Max");
        m.setNachname("Mustermann");
        max = em.persistAndFlush(m).getId();
        projektId = auftrag("Geländer");
        vorgangId = planung.vorgangAnlegen(projektId, new VorgangEingabe(typ, "Geländer fertigen", null, null, null, null, new BigDecimal("16"))).vorgaenge().get(0).id();
        zuweisung.zuweisen(vorgangId, new PlanungsZuweisung.Zuweisung(List.of(max), null, null));
    }

    private Long auftrag(String name) {
        Projekt p = new Projekt();
        p.setBauvorhaben(name);
        p.setAuftragsnummer("A-" + name);
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        return em.persistAndFlush(p).getId();
    }

    private PlanungsService.VorgangDto vorgang() {
        return planung.auftrag(projektId).vorgaenge().get(0);
    }

    @Test
    void eineAenderungStehtMitTextUndBenutzerInDerListe() {
        planung.vorgangAendern(vorgangId, new VorgangEingabe(null, null, null, null, null, null, new BigDecimal("32")));

        var eintrag = historie.liste(10).get(0);
        assertThat(eintrag.beschreibung()).contains("Geländer fertigen").contains("Aufwand von 16 Stunden auf 32 Stunden");
        assertThat(eintrag.benutzer()).isEqualTo("unbekannt");
        assertThat(eintrag.rueckgaengigMoeglich()).isTrue();
        assertThat(eintrag.zurueckgenommen()).isFalse();
    }

    @Test
    void ohneAenderungGibtEsKeinenEintrag() {
        int vorher = historie.liste(50).size();
        planung.vorgangAendern(vorgangId, new VorgangEingabe(null, null, null, null, null, null, new BigDecimal("16")));
        assertThat(historie.liste(50)).hasSize(vorher);
    }

    @Test
    void rueckgaengigStelltDenZustandDavorWiederHerUndRechnetNeu() {
        LocalDate endeVorher = vorgang().ende();
        planung.vorgangAendern(vorgangId, new VorgangEingabe(null, null, null, null, null, null, new BigDecimal("80")));
        assertThat(vorgang().ende()).isAfter(endeVorher);
        Long id = historie.liste(10).get(0).id();

        AuftragDto zurueck = planung.rueckgaengig(id);

        assertThat(zurueck.vorgaenge().get(0).aufwandStunden()).isEqualByComparingTo("16");
        assertThat(zurueck.vorgaenge().get(0).ende()).isEqualTo(endeVorher);
        assertThat(historie.liste(10).get(0).zurueckgenommen()).isTrue();
        assertThat(historie.liste(10).get(0).rueckgaengigMoeglich()).isFalse();
        assertThatThrownBy(() -> planung.rueckgaengig(id)).hasMessageContaining("schon zurückgenommen");
    }

    @Test
    void nurDieLetzteAenderungLaesstSichZuruecknehmen() {
        planung.vorgangAendern(vorgangId, new VorgangEingabe(null, null, null, null, null, null, new BigDecimal("24")));
        Long erste = historie.liste(10).get(0).id();
        planung.rangSetzen(vorgangId, 2);

        assertThatThrownBy(() -> planung.rueckgaengig(erste)).hasMessageContaining("nur die letzte");
    }

    @Test
    void anlegenUndLoeschenStehenInDerListeLassenSichAberNichtZuruecknehmen() {
        Long neuer = planung.vorgangAnlegen(projektId, new VorgangEingabe(typ, "Tor", null, null, null, null, new BigDecimal("8"))).vorgaenge().get(1).id();
        var angelegt = historie.liste(10).get(0);
        assertThat(angelegt.beschreibung()).contains("„Tor“").contains("angelegt");
        assertThat(angelegt.rueckgaengigMoeglich()).isFalse();
        assertThatThrownBy(() -> planung.rueckgaengig(angelegt.id())).hasMessageContaining("nicht zurücknehmen");

        planung.vorgangLoeschen(neuer);
        assertThat(historie.liste(10).get(0).beschreibung()).contains("gelöscht");
    }

    @Test
    void einAlterStandWirdAlsKonfliktAbgelehntUndNichtUeberschrieben() {
        int stand = vorgang().stand();
        planung.vorgangAendern(vorgangId, new VorgangEingabe(null, null, null, null, null, null, new BigDecimal("24"), stand)); // jemand anderes speichert zuerst

        assertThatThrownBy(() -> planung.vorgangAendern(vorgangId, new VorgangEingabe(null, null, null, null, null, null, new BigDecimal("40"), stand)))
                .isInstanceOf(PlanKonfliktException.class).hasMessageContaining("inzwischen von jemand anderem geändert");
        assertThat(vorgang().aufwandStunden()).isEqualByComparingTo("24");
        assertThatThrownBy(() -> planung.fruehestensSetzen(vorgangId, LocalDate.now().plusDays(9), stand)).isInstanceOf(PlanKonfliktException.class);
        assertThatThrownBy(() -> planung.rangSetzen(vorgangId, 1, stand)).isInstanceOf(PlanKonfliktException.class);
        assertThatThrownBy(() -> zuweisung.zuweisen(vorgangId, new PlanungsZuweisung.Zuweisung(List.of(), null, null, stand))).isInstanceOf(PlanKonfliktException.class);
        planung.vorgangAendern(vorgangId, new VorgangEingabe(null, null, null, null, null, null, new BigDecimal("40"), vorgang().stand()));
        assertThat(vorgang().aufwandStunden()).as("mit dem aktuellen Stand klappt es").isEqualByComparingTo("40");
    }

    @Test
    void dieAutomatischeNeuberechnungVeraendertDenStandNicht() {
        int stand = vorgang().stand();
        planung.neuBerechnenAlle();
        assertThat(vorgang().stand()).isEqualTo(stand);
    }

    @Test
    void dieVorschauNenntDieNeueLieferzeitUndAendertNichts() {
        var vorher = vorgang();

        var v = planung.vorschau(vorgangId, new PlanungsService.Probe(new BigDecimal("80"), null, null, false, null, false, null, null));

        assertThat(v.auftraege()).hasSize(1);
        var a = v.auftraege().get(0);
        assertThat(a.eigener()).isTrue();
        assertThat(a.altLiefertermin()).isEqualTo(vorher.ende());
        assertThat(a.neuLiefertermin()).isAfter(vorher.ende());
        assertThat(a.tage()).isPositive();
        assertThat(v.vorgaenge()).extracting(PlanungsService.VorgangAenderung::vorgangId).contains(vorgangId);
        assertThat(vorgang().ende()).as("nichts wurde gespeichert").isEqualTo(vorher.ende());
        assertThat(vorgang().aufwandStunden()).isEqualByComparingTo("16");
    }

    @Test
    void dieVorschauZeigtAuchAndereAuftraegeDieSichVerschieben() {
        Long zweiter = auftrag("Tor");
        Long v2 = planung.vorgangAnlegen(zweiter, new VorgangEingabe(typ, "Tor fertigen", null, null, null, null, new BigDecimal("16"))).vorgaenge().get(0).id();
        zuweisung.zuweisen(v2, new PlanungsZuweisung.Zuweisung(List.of(max), null, null));
        Long erster = planung.auftrag(projektId).vorgaenge().get(0).id();
        // Beide Aufträge teilen sich Max; der erste Auftrag wird dringend: Das Tor muss warten
        planung.rangSetzen(v2, 2);
        planung.rangSetzen(erster, 1);

        var v = planung.vorschau(erster, new PlanungsService.Probe(new BigDecimal("48"), null, null, false, null, false, null, null));

        assertThat(v.auftraege()).extracting(PlanungsService.AuftragAenderung::projektId).contains(projektId, zweiter);
        assertThat(v.auftraege().get(0).eigener()).as("der eigene Auftrag steht zuerst").isTrue();
    }

    @Test
    void dieVorschauPruefDieEingabe() {
        assertThatThrownBy(() -> planung.vorschau(vorgangId, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planung.vorschau(vorgangId, new PlanungsService.Probe(new BigDecimal("-1"), null, null, false, null, false, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planung.vorschau(999999L, new PlanungsService.Probe(null, null, null, false, null, false, null, null))).hasMessageContaining("Vorgang");
    }

    @Test
    void einAuftragLaesstSichKomplettAusDerPlantafelNehmenDieAnderenRechnenNeu() {
        Long zweiter = auftrag("Tor");
        Long v2 = planung.vorgangAnlegen(zweiter, new VorgangEingabe(typ, "Tor fertigen", null, null, null, null, new BigDecimal("16"))).vorgaenge().get(0).id();
        zuweisung.zuweisen(v2, new PlanungsZuweisung.Zuweisung(List.of(max), null, null));
        planung.rangSetzen(vorgangId, 1);
        planung.rangSetzen(v2, 2);
        LocalDate wartend = planung.auftrag(zweiter).vorgaenge().get(0).start();

        planung.auftragEntfernen(projektId);

        assertThat(planung.auftrag(projektId).vorgaenge()).isEmpty();
        assertThat(planung.auftraege()).extracting(PlanungsService.AuftragDto::projektId).containsExactly(zweiter);
        assertThat(planung.auftrag(zweiter).vorgaenge().get(0).start()).as("der andere Auftrag rückt vor, weil Max frei wird").isBeforeOrEqualTo(wartend);
        assertThat(historie.liste(5).get(0).beschreibung()).contains("aus der Plantafel entfernt");
        assertThatThrownBy(() -> planung.auftragEntfernen(projektId)).hasMessageContaining("nicht in der Plantafel");
    }

    @Test
    void dieWichtigkeitGiltFuerAlleOffenenVorgaengeDesAuftrags() {
        Long zweiterVorgang = planung.vorgangAnlegen(projektId, new VorgangEingabe(typ, "Montage", null, null, null, null, new BigDecimal("8"))).vorgaenge().get(1).id();
        planung.vorgangAendern(zweiterVorgang, new VorgangEingabe(null, null, null, null, "FERTIG", null));

        var a = planung.auftragPrioritaet(projektId, "dringend");

        assertThat(a.vorgaenge().get(0).prioritaet()).isEqualTo("DRINGEND");
        assertThat(a.vorgaenge().get(1).prioritaet()).as("Erledigtes bleibt").isEqualTo("NORMAL");
        assertThatThrownBy(() -> planung.auftragPrioritaet(projektId, "sofort")).hasMessageContaining("Priorität");
    }
}
