package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentProjektAnteil;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.LieferantGeschaeftsdokument;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.service.TagesSollService;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangEingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;

/** Lieferung bei Wartevorgängen: übliche Lieferzeit des Lieferanten, „bestellt am“, Liefertermin aus der Auftragsbestätigung (#331, Entscheidung H). Nur Dummy-Daten. */
@DataJpaTest
@Import({ PlanungsService.class, PlanKapazitaetQuelle.class, org.example.kalkulationsprogramm.service.planung.PlanungsIst.class, PlanungsHistorie.class, PlanungsEinstellungen.class, PlanungsZuweisung.class })
class PlanungsLieferungTest {

    @MockitoBean
    private AngebotsZeiten angebotsZeiten;
    @MockitoBean
    private TagesSollService tagesSollService;
    @MockitoBean
    private org.example.kalkulationsprogramm.service.FeiertagService feiertagService;
    @Autowired
    private PlanungsService planung;
    @Autowired
    private PlanungsHistorie historie;
    @Autowired
    private TestEntityManager em;

    private Long projektId;
    private Long vorgangId;
    private Long lieferantId;

    @BeforeEach
    void setUp() {
        Long material = planung.typSpeichern(null, "Materialbeschaffung", "#d97706", 20, 3, true, null, null, true, false).id();
        Projekt p = new Projekt();
        p.setBauvorhaben("Geländer");
        p.setAuftragsnummer("A-1");
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        projektId = em.persistAndFlush(p).getId();
        Lieferanten l = new Lieferanten();
        l.setLieferantenname("Stahlhandel Muster");
        l.setStandardLieferzeitTage(8);
        lieferantId = em.persistAndFlush(l).getId();
        vorgangId = planung.vorgangAnlegen(projektId, new VorgangEingabe(material, "Stahl bestellen", null, null, null, null)).vorgaenge().get(0).id();
    }

    private PlanungsService.VorgangDto vorgang() {
        return planung.auftrag(projektId).vorgaenge().get(0);
    }

    @Test
    void dieUeblicheLieferzeitDesLieferantenGiltSolangeKeineDauerVonHandSteht() {
        assertThat(vorgang().dauerTage()).isEqualTo(3);

        var v = planung.lieferungSetzen(vorgangId, lieferantId, null, null, null).vorgaenge().get(0);

        assertThat(v.dauerTage()).isEqualTo(8);
        assertThat(v.lieferantName()).isEqualTo("Stahlhandel Muster");
        assertThat(v.hinweis()).contains("übliche Lieferzeit von Stahlhandel Muster: 8 Arbeitstage");
        assertThat(v.start()).isNotNull();
    }

    @Test
    void eineDauerVonHandSchlaegtDieLieferzeitDesLieferanten() {
        planung.vorgangAendern(vorgangId, new VorgangEingabe(null, null, 4, null, null, null));
        var v = planung.lieferungSetzen(vorgangId, lieferantId, null, null, null).vorgaenge().get(0);

        assertThat(v.dauerTage()).isEqualTo(4);
        assertThat(v.hinweis()).as("keine übliche Lieferzeit im Spiel").isNull();
    }

    @Test
    void dieLieferzeitZaehltAbDerBestellung() {
        LocalDate vorAchtTagen = LocalDate.now().minusDays(8);
        var v = planung.lieferungSetzen(vorgangId, lieferantId, vorAchtTagen, null, null).vorgaenge().get(0);

        assertThat(v.start()).as("der Vorgang beginnt am Bestelltag (oder dem nächsten Arbeitstag), nicht heute").isBeforeOrEqualTo(vorAchtTagen.plusDays(2));
        assertThat(v.hinweis()).contains("bestellt am");
    }

    @Test
    void einVonHandBestaetigterLieferterminIstDasEnde() {
        LocalDate montag = LocalDate.now().plusDays(21);
        while (montag.getDayOfWeek().getValue() != 1) {
            montag = montag.plusDays(1);
        }
        var v = planung.lieferungSetzen(vorgangId, lieferantId, null, montag, null).vorgaenge().get(0);

        assertThat(v.ende()).isEqualTo(montag);
        assertThat(v.hinweis()).contains("Liefertermin von Hand");
        assertThat(planung.lieferungSetzen(vorgangId, null, null, null, null).vorgaenge().get(0).lieferantId()).as("alles leer hebt die Lieferung auf").isNull();
    }

    @Test
    void dieAuftragsbestaetigungDesLieferantenGibtDenLiefertermin() {
        LocalDate termin = LocalDate.now().plusDays(28);
        while (termin.getDayOfWeek().getValue() != 3) {
            termin = termin.plusDays(1);
        }
        LieferantDokument d = new LieferantDokument();
        d.setLieferant(em.find(Lieferanten.class, lieferantId));
        d.setTyp(LieferantDokumentTyp.AUFTRAGSBESTAETIGUNG);
        d.setUploadDatum(LocalDateTime.now());
        d = em.persistAndFlush(d);
        LieferantGeschaeftsdokument g = new LieferantGeschaeftsdokument();
        g.setDokument(d);
        g.setLiefertermin(termin);
        em.persist(g);
        LieferantDokumentProjektAnteil a = new LieferantDokumentProjektAnteil();
        a.setDokument(d);
        a.setProjekt(em.find(Projekt.class, projektId));
        a.setProzent(100);
        em.persistAndFlush(a);
        em.clear();

        var v = planung.lieferungSetzen(vorgangId, lieferantId, null, null, null).vorgaenge().get(0);

        assertThat(v.ende()).isEqualTo(termin);
        assertThat(v.hinweis()).contains("Liefertermin laut Auftragsbestätigung von Stahlhandel Muster");
    }

    @Test
    void nurEinWartevorgangHatEineLieferung() {
        Long fertigung = planung.typSpeichern(null, "Fertigung", "#be123c", 30, 5, true, null, null).id();
        Long arbeit = planung.vorgangAnlegen(projektId, new VorgangEingabe(fertigung, null, null, null, null, null)).vorgaenge().get(1).id();

        assertThatThrownBy(() -> planung.lieferungSetzen(arbeit, lieferantId, null, null, null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Wartevorgang");
        assertThatThrownBy(() -> planung.lieferungSetzen(vorgangId, 999999L, null, null, null)).hasMessageContaining("Lieferanten");
        assertThatThrownBy(() -> planung.lieferungSetzen(vorgangId, lieferantId, LocalDate.now(), LocalDate.now().minusDays(1), null)).hasMessageContaining("nicht vor dem Bestelldatum");
    }

    @Test
    void dieLieferungStehtInDerAenderungslisteUndLaesstSichZuruecknehmen() {
        planung.lieferungSetzen(vorgangId, lieferantId, LocalDate.of(2026, 10, 5), null, null);

        var e = historie.liste(5).get(0);
        assertThat(e.beschreibung()).contains("Lieferant geändert").contains("bestellt am 5.10.");
        planung.rueckgaengig(e.id());
        assertThat(vorgang().lieferantId()).isNull();
        assertThat(vorgang().bestelltAm()).isNull();
    }
}
