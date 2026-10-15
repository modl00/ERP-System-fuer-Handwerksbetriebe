package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.TypDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangEingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** Plantafel gegen die echte Datenbank-Abbildung (H2): Vorgänge, Abhängigkeiten, Neuberechnung, Kreis, Ampel. Nur Dummy-Daten. */
@DataJpaTest
@Import({ PlanungsService.class, PlanKapazitaetQuelle.class, org.example.kalkulationsprogramm.service.planung.PlanungsIst.class, org.example.kalkulationsprogramm.service.planung.PlanungsHistorie.class, PlanungsEinstellungen.class })
class PlanungsServiceTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private AngebotsZeiten angebotsZeiten;
    @org.springframework.boot.test.mock.mockito.MockBean
    private org.example.kalkulationsprogramm.service.TagesSollService tagesSollService;
    @Autowired
    private org.example.kalkulationsprogramm.repository.AbteilungRepository abteilungen;

    @Autowired
    private PlanungsService service;
    @Autowired
    private TestEntityManager em;

    private Long projektId;
    private Long planung;
    private Long fertigung;

    @BeforeEach
    void setUp() {
        Projekt p = new Projekt();
        p.setBauvorhaben("Geländer Musterstraße");
        p.setAuftragsnummer("A-2026-001");
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        projektId = em.persistAndFlush(p).getId();
        TypDto t1 = service.typSpeichern(null, "Planung", "#6366f1", 10, 2, true, null, null);
        TypDto t2 = service.typSpeichern(null, "Fertigung", "#be123c", 30, 5, true, null, null);
        AuftragDto a = service.vorgangAnlegen(projektId, new VorgangEingabe(t1.id(), null, null, null, null, null));
        planung = a.vorgaenge().get(0).id();
        a = service.vorgangAnlegen(projektId, new VorgangEingabe(t2.id(), "Geländer fertigen", 3, null, null, null));
        fertigung = a.vorgaenge().get(1).id();
    }

    @Test
    void einNeuerVorgangUebernimmtDieVorgabeDerArtUndSagtDassSieGeschaetztIst() {
        VorgangDto v = service.auftrag(projektId).vorgaenge().get(0);

        assertThat(v.bezeichnung()).isEqualTo("Planung");
        assertThat(v.dauerTage()).isEqualTo(2);
        assertThat(v.dauerHerkunft()).isEqualTo("GESCHAETZT");
        assertThat(v.start()).isNotNull();
        assertThat(service.auftrag(projektId).vorgaenge().get(1).dauerHerkunft()).isEqualTo("VON_HAND");
    }

    @Test
    void eineAbhaengigkeitSchiebtDenNachfolgerHinterDenVorgaengerUndDasEndeIstDieLieferzeit() {
        AuftragDto vorher = service.auftrag(projektId);
        assertThat(vorher.vorgaenge().get(1).start()).as("ohne Abhängigkeit beginnen beide gleich").isEqualTo(vorher.vorgaenge().get(0).start());

        AuftragDto a = service.abhaengigkeitSetzen(fertigung, planung, 0);

        VorgangDto p = a.vorgaenge().get(0);
        VorgangDto f = a.vorgaenge().get(1);
        assertThat(f.start()).isAfter(p.ende());
        assertThat(f.vorgaenger()).extracting(PlanungsService.AbhaengigkeitDto::vorgaengerId).containsExactly(planung);
        assertThat(a.liefertermin()).isEqualTo(f.ende());
    }

    @Test
    void einKreisWirdAbgelehntUndNichtGespeichert() {
        service.abhaengigkeitSetzen(fertigung, planung, 0);

        assertThatThrownBy(() -> service.abhaengigkeitSetzen(planung, fertigung, 0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("im Kreis");
    }

    @Test
    void einVorgangKannNichtVonSichSelbstAbhaengen() {
        assertThatThrownBy(() -> service.abhaengigkeitSetzen(planung, planung, 0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("von sich selbst");
    }

    @Test
    void einFesterStartBleibtStehenUndEinSpaeterVorgaengerMeldetEinenKonflikt() {
        service.abhaengigkeitSetzen(fertigung, planung, 0);
        LocalDate heute = LocalDate.now();

        AuftragDto a = service.vorgangAendern(fertigung, new VorgangEingabe(null, null, null, heute, null, null));

        VorgangDto f = a.vorgaenge().get(1);
        assertThat(f.festerStart()).isEqualTo(heute);
        assertThat(f.konflikt()).as("Planung dauert 2 Tage, Fertigung soll heute schon starten").isTrue();

        AuftragDto ohne = service.vorgangAendern(fertigung, new VorgangEingabe(null, null, null, null, null, null));
        assertThat(ohne.vorgaenge().get(1).festerStart()).isNull();
        assertThat(ohne.vorgaenge().get(1).konflikt()).isFalse();
    }

    @Test
    void dieAmpelVergleichtLieferterminUndZiel() {
        LocalDate ziel = LocalDate.of(2026, 10, 30); // Freitag
        assertThat(PlanungsService.ampel(LocalDate.of(2026, 10, 29), ziel)).as("1 Arbeitstag Luft").isEqualTo("KNAPP");
        assertThat(PlanungsService.ampel(LocalDate.of(2026, 10, 28), ziel)).isEqualTo("OK");
        assertThat(PlanungsService.ampel(LocalDate.of(2026, 11, 2), ziel)).isEqualTo("VERSPAETET");
        assertThat(PlanungsService.ampel(null, ziel)).isEqualTo("OHNE_ZIEL");
        assertThat(PlanungsService.ampel(ziel, null)).isEqualTo("OHNE_ZIEL");
    }

    @Test
    void einZielVorDemStartWirdAbgelehntUndDasZielFliesstInDieAmpelEin() {
        assertThatThrownBy(() -> service.setzeTermine(projektId, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1))).isInstanceOf(IllegalArgumentException.class);

        AuftragDto a = service.setzeTermine(projektId, null, LocalDate.of(2020, 1, 3));

        assertThat(a.zielDatum()).isEqualTo(LocalDate.of(2020, 1, 3));
        assertThat(a.ampel()).isEqualTo("VERSPAETET");
    }

    @Test
    void einVorgangLoeschenNimmtSeineAbhaengigkeitenMitUndRechnetNeu() {
        service.abhaengigkeitSetzen(fertigung, planung, 0);

        AuftragDto a = service.vorgangLoeschen(planung);

        assertThat(a.vorgaenge()).hasSize(1);
        assertThat(a.vorgaenge().get(0).vorgaenger()).isEmpty();
    }

    @Test
    void vorgangsartenBraucheneinenEindeutigenNamenUndEineGueltigeFarbe() {
        assertThatThrownBy(() -> service.typSpeichern(null, "planung", null, null, null, null, null, null)).hasMessageContaining("schon");
        assertThatThrownBy(() -> service.typSpeichern(null, "Lager", "rot", null, null, null, null, null)).hasMessageContaining("#rrggbb");
        assertThatThrownBy(() -> service.typSpeichern(null, " ", null, null, null, null, null, null)).hasMessageContaining("Namen");
    }

    private Long abteilung(String name) {
        var a = new org.example.kalkulationsprogramm.domain.Abteilung();
        a.setName(name);
        return em.persistAndFlush(a).getId();
    }

    @Test
    void dieArtBekommtAbteilungUndStundenProTagUndLehntUnsinnAb() {
        Long werkstatt = abteilung("Werkstatt");

        TypDto t = service.typSpeichern(null, "Fertigung 2", "#be123c", 35, 5, true, werkstatt, new java.math.BigDecimal("24"));

        assertThat(t.abteilungId()).isEqualTo(werkstatt);
        assertThat(t.stundenProTag()).isEqualByComparingTo("24.00");
        assertThatThrownBy(() -> service.typSpeichern(t.id(), null, null, null, null, null, 999999L, null)).hasMessageContaining("Abteilung");
        assertThatThrownBy(() -> service.typSpeichern(t.id(), null, null, null, null, null, werkstatt, java.math.BigDecimal.ZERO)).hasMessageContaining("Stunden");
        assertThat(service.typSpeichern(t.id(), null, null, null, null, null, null, null).stundenProTag()).as("leer = kein Vorschlag").isNull();
    }

    @Test
    void derVorschlagRechnetStundenDurchStundenProTagAufgerundetUndAendertNichts() {
        Long werkstatt = abteilung("Werkstatt");
        TypDto art = service.typSpeichern(null, "Fertigung 3", "#be123c", 36, 5, true, werkstatt, new java.math.BigDecimal("24"));
        AuftragDto a = service.vorgangAnlegen(projektId, new VorgangEingabe(art.id(), "Geländer fertigen", null, null, null, null));
        Long vorgangId = a.vorgaenge().get(a.vorgaenge().size() - 1).id();
        org.mockito.Mockito.when(angebotsZeiten.fuerProjekt(projektId)).thenReturn(new AngebotsZeiten.Zeiten("ANGEBOT", java.util.Map.of(werkstatt, new java.math.BigDecimal("50.00")), java.util.List.of()));

        var antwort = service.vorschlag(projektId);

        assertThat(antwort.quelle()).isEqualTo("ANGEBOT");
        var zeile = antwort.zeilen().stream().filter(z -> z.vorgangId().equals(vorgangId)).findFirst().orElseThrow();
        assertThat(zeile.vorgeschlageneDauer()).as("50 h / 24 h = 2,08 -> 3 Tage").isEqualTo(3);
        assertThat(zeile.aktuelleDauer()).isEqualTo(5);
        assertThat(antwort.zeilen()).as("Arten ohne Abteilung rechnen nicht aus dem Angebot").extracting(z -> z.art()).containsOnly("Fertigung 3");
        assertThat(service.auftrag(projektId).vorgaenge().stream().filter(v -> v.id().equals(vorgangId)).findFirst().orElseThrow().dauerTage()).isEqualTo(5);
    }

    @Test
    void dasUebernehmenSetztDieDauerAusDerKalkulationUndRechnetNeu() {
        Long werkstatt = abteilung("Werkstatt");
        TypDto art = service.typSpeichern(null, "Fertigung 4", "#be123c", 37, 5, true, werkstatt, new java.math.BigDecimal("8"));
        AuftragDto a = service.vorgangAnlegen(projektId, new VorgangEingabe(art.id(), null, 9, null, null, null));
        Long vorgangId = a.vorgaenge().get(a.vorgaenge().size() - 1).id();
        org.mockito.Mockito.when(angebotsZeiten.fuerProjekt(projektId)).thenReturn(new AngebotsZeiten.Zeiten("AUFTRAGSBESTAETIGUNG", java.util.Map.of(werkstatt, new java.math.BigDecimal("16.00")), java.util.List.of()));

        AuftragDto neu = service.vorschlagUebernehmen(projektId, java.util.List.of(vorgangId));

        VorgangDto v = neu.vorgaenge().stream().filter(x -> x.id().equals(vorgangId)).findFirst().orElseThrow();
        assertThat(v.dauerTage()).isEqualTo(2);
        assertThat(v.dauerHerkunft()).isEqualTo("KALKULATION");
        assertThatThrownBy(() -> service.vorschlagUebernehmen(projektId, java.util.List.of(planung))).hasMessageContaining("keinen Vorschlag");
        assertThatThrownBy(() -> service.vorschlagUebernehmen(projektId, java.util.List.of())).hasMessageContaining("mindestens einen");
    }

    @Test
    void ohneArbeitszeitImAngebotGibtEsKeinenVorschlagAberEinenHinweis() {
        Long werkstatt = abteilung("Werkstatt");
        TypDto art = service.typSpeichern(null, "Fertigung 5", "#be123c", 38, 5, true, werkstatt, new java.math.BigDecimal("8"));
        service.vorgangAnlegen(projektId, new VorgangEingabe(art.id(), null, null, null, null, null));
        org.mockito.Mockito.when(angebotsZeiten.fuerProjekt(projektId)).thenReturn(new AngebotsZeiten.Zeiten(null, java.util.Map.of(), java.util.List.of()));

        var zeile = service.vorschlag(projektId).zeilen().get(0);

        assertThat(zeile.vorgeschlageneDauer()).isNull();
        assertThat(zeile.hinweis()).contains("kein Angebot");
    }

    @Test
    void dieStandardketteLegtEinenVorgangJeAktiverArtAnUndVerknuepftSieNacheinander() {
        Projekt p = new Projekt();
        p.setBauvorhaben("Tor");
        p.setAuftragsnummer("A-2026-002");
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        Long neu = em.persistAndFlush(p).getId();
        service.typSpeichern(null, "Veredelung", "#0891b2", 40, 5, true, null, null);
        service.typSpeichern(null, "Alt", "#000000", 99, 1, false, null, null);

        AuftragDto a = service.standardkette(neu);

        assertThat(a.vorgaenge()).extracting(VorgangDto::typName).containsExactly("Planung", "Fertigung", "Veredelung");
        assertThat(a.vorgaenge().get(0).vorgaenger()).isEmpty();
        assertThat(a.vorgaenge().get(1).vorgaenger()).extracting(PlanungsService.AbhaengigkeitDto::vorgaengerId).containsExactly(a.vorgaenge().get(0).id());
        assertThat(a.vorgaenge().get(2).start()).isAfter(a.vorgaenge().get(1).ende());
        assertThat(a.vorgaenge()).allSatisfy(v -> assertThat(v.dauerHerkunft()).isEqualTo("GESCHAETZT"));
        assertThatThrownBy(() -> service.standardkette(neu)).hasMessageContaining("schon Vorgänge");
    }

    @Test
    void eineAusgewaehlteKetteLegtVorgaengeMitAbhaengigkeitenAnUndNimmtNichtZweimal() {
        Projekt p = new Projekt();
        p.setBauvorhaben("Treppe");
        p.setAuftragsnummer("A-2026-002");
        p.setAnlegedatum(LocalDate.now());
        p.setBruttoPreis(BigDecimal.ZERO);
        Long neu = em.persistAndFlush(p).getId();
        Long typ1 = service.typen().get(0).id();
        Long typ2 = service.typen().get(1).id();

        AuftragDto a = service.kettePerAuswahl(neu, java.util.List.of(new PlanungsService.Schritt(typ1, 1), new PlanungsService.Schritt(typ2, 4)));

        assertThat(a.vorgaenge()).extracting(VorgangDto::dauerTage).containsExactly(1, 4);
        assertThat(a.vorgaenge()).extracting(VorgangDto::dauerHerkunft).containsOnly("GESCHAETZT");
        assertThat(a.vorgaenge().get(1).vorgaenger()).hasSize(1);
        assertThat(a.vorgaenge().get(1).start()).isAfter(a.vorgaenge().get(0).ende());
        assertThatThrownBy(() -> service.kettePerAuswahl(neu, java.util.List.of(new PlanungsService.Schritt(typ1, 1)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schon Vorgänge");
        assertThatThrownBy(() -> service.kettePerAuswahl(projektId, java.util.List.of())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mindestens einen");
    }

    @Autowired
    private PlanungsEinstellungen einstellungen;

    @Test
    void derBetriebsurlaubSchiebtDieKetteHinterSeinEnde() {
        LocalDate heute = LocalDate.now();
        LocalDate urlaubEnde = heute.plusDays(20);
        einstellungen.speichern(java.util.EnumSet.range(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.FRIDAY), 2,
                java.util.List.of(new PlanungsEinstellungen.Urlaub(heute.minusDays(1), urlaubEnde, "Betriebsurlaub")));

        AuftragDto a = service.vorgangAendern(planung, new VorgangEingabe(null, null, null, null, null, null));

        assertThat(a.vorgaenge().get(0).start()).as("kein Arbeitstag im Urlaub").isAfter(urlaubEnde);
    }

    @Test
    void dieArbeitswocheDerFirmaBestimmtWelcheTageZaehlen() {
        einstellungen.speichern(java.util.EnumSet.of(java.time.DayOfWeek.SATURDAY), 2, java.util.List.of());

        AuftragDto a = service.vorgangAendern(planung, new VorgangEingabe(null, null, null, null, null, null));

        assertThat(a.vorgaenge().get(0).start().getDayOfWeek()).isEqualTo(java.time.DayOfWeek.SATURDAY);
        assertThat(a.vorgaenge().get(0).ende().getDayOfWeek()).as("zwei Arbeitstage = zwei Samstage").isEqualTo(java.time.DayOfWeek.SATURDAY);
        assertThat(a.vorgaenge().get(0).ende()).isEqualTo(a.vorgaenge().get(0).start().plusDays(7));
    }

    @Test
    void dieSchwelleDerAmpelKommtAusDenEinstellungen() {
        LocalDate ziel = LocalDate.now().plusDays(60);
        while (ziel.getDayOfWeek() != java.time.DayOfWeek.FRIDAY) {
            ziel = ziel.plusDays(1);
        }
        java.util.function.Predicate<LocalDate> moFr = PlanungsRechner.arbeitstag(java.util.Set.of());
        LocalDate donnerstag = ziel.minusDays(1);

        assertThat(PlanungsService.ampel(donnerstag, ziel, 0, moFr)).isEqualTo("OK");
        assertThat(PlanungsService.ampel(donnerstag, ziel, 2, moFr)).isEqualTo("KNAPP");
        assertThat(PlanungsService.ampel(ziel.minusDays(4), ziel, 2, moFr)).isEqualTo("OK");
    }

    @Test
    void dieVerspaetungWirdInArbeitstagenGenanntUndFehltWennNichtsVerspaetetIst() {
        LocalDate ende = service.auftrag(projektId).liefertermin();

        // sieben Kalendertage vor dem Ende: dazwischen liegen genau fünf Arbeitstage (Montag bis Freitag, keine Feiertage in der Testdatenbank)
        AuftragDto spaet = service.setzeTermine(projektId, null, ende.minusDays(7));
        assertThat(spaet.ampel()).isEqualTo("VERSPAETET");
        assertThat(spaet.verspaetungTage()).isEqualTo(5);

        AuftragDto puenktlich = service.setzeTermine(projektId, null, ende.plusDays(30));
        assertThat(puenktlich.verspaetungTage()).isNull();
        assertThat(service.setzeTermine(projektId, null, null).verspaetungTage()).as("ohne Zieltermin").isNull();
    }
}
