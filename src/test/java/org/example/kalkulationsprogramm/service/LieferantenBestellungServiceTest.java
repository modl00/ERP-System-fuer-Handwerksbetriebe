package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.domain.BestellHerkunft;
import org.example.kalkulationsprogramm.domain.BestellPosition;
import org.example.kalkulationsprogramm.domain.BestellStatus;
import org.example.kalkulationsprogramm.domain.DokumentnummerCounter;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.LieferantenBestellung;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellVorschlagDto;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungDto;
import org.example.kalkulationsprogramm.dto.Bestellwesen.BestellungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.BestellPositionRepository;
import org.example.kalkulationsprogramm.repository.DokumentnummerCounterRepository;
import org.example.kalkulationsprogramm.repository.LieferantenArtikelPreiseRepository;
import org.example.kalkulationsprogramm.repository.LieferantenBestellungRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.BestellPositionAnhangRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

/** Nur Dummy-Daten (Musterstadt, Muster GmbH). */
@ExtendWith(MockitoExtension.class)
class LieferantenBestellungServiceTest {

    @Mock LieferantenBestellungRepository bestellungen;
    @Mock BestellPositionRepository positionen;
    @Mock LieferantenRepository lieferanten;
    @Mock ArtikelRepository artikel;
    @Mock ProjektRepository projekte;
    @Mock ArtikelInProjektRepository projektPositionen;
    @Mock LieferantenArtikelPreiseRepository preise;
    @Mock DokumentnummerCounterRepository zaehler;
    @Mock LagerService lager;
    @Mock BestellungService projektBestellungen;
    @Mock BestellPositionAnhangRepository positionAnhaenge;

    LieferantenBestellungService service;
    Lieferanten lieferant;
    Artikel rohr;
    Artikel schraube;
    Projekt projekt;

    @BeforeEach
    void setUp() {
        service = new LieferantenBestellungService(bestellungen, positionen, lieferanten, artikel, projekte, projektPositionen, preise, zaehler, lager,
                projektBestellungen, positionAnhaenge);
        lieferant = new Lieferanten();
        lieferant.setId(3L);
        lieferant.setLieferantenname("Stahlhandel Muster");
        lieferant.setKundenEmails(new ArrayList<>(List.of("bestellung@stahl.example.org")));
        rohr = artikel(10L, "Rohr 40x40", "R-4040", Verrechnungseinheit.LAUFENDE_METER);
        schraube = artikel(11L, "Schraube M8", "S-M8", Verrechnungseinheit.STUECK);
        projekt = new Projekt();
        projekt.setId(7L);
        projekt.setBauvorhaben("Anbau Halle");
        projekt.setAuftragsnummer("2026/014");
        lenient().when(lieferanten.findById(3L)).thenReturn(Optional.of(lieferant));
        lenient().when(artikel.findById(10L)).thenReturn(Optional.of(rohr));
        lenient().when(artikel.findById(11L)).thenReturn(Optional.of(schraube));
        lenient().when(projekte.findById(7L)).thenReturn(Optional.of(projekt));
        lenient().when(bestellungen.save(any(LieferantenBestellung.class))).thenAnswer(a -> {
            LieferantenBestellung b = a.getArgument(0);
            if (b.getId() == null) {
                b.setId(100L);
            }
            long i = 1;
            for (BestellPosition p : b.getPositionen()) {
                if (p.getId() == null) {
                    p.setId(i++);
                }
            }
            return b;
        });
    }

    private static Artikel artikel(Long id, String name, String nr, Verrechnungseinheit e) {
        Artikel a = new Artikel();
        a.setId(id);
        a.setKurzbeschreibung(name);
        a.setArtikelnummer(nr);
        a.setVerrechnungseinheit(e);
        return a;
    }

    private static BestellungEingabe.Position frei(Long artikelId, String menge) {
        return new BestellungEingabe.Position(artikelId, new BigDecimal(menge), null, null, BestellHerkunft.FREI, null, null, null);
    }

    private BestellungEingabe eingabe(BestellungEingabe.Position... ps) {
        return new BestellungEingabe(3L, "Bitte Anfang der Woche liefern", List.of(ps));
    }

    private LieferantenBestellung entwurf() {
        BestellungDto d = service.anlegen(eingabe(frei(10L, "12.5"), frei(11L, "200")));
        LieferantenBestellung b = new LieferantenBestellung();
        b.setId(d.id());
        b.setLieferant(lieferant);
        b.setStatus(BestellStatus.ENTWURF);
        b.setErstelltAm(LocalDateTime.now());
        for (BestellungDto.Position p : d.positionen()) {
            BestellPosition bp = new BestellPosition();
            bp.setId(p.id());
            bp.setBestellung(b);
            bp.setArtikel(p.artikelId().equals(10L) ? rohr : schraube);
            bp.setBezeichnung(p.bezeichnung());
            bp.setMenge(p.menge());
            bp.setEinheit(p.einheit());
            bp.setHerkunft(p.herkunft());
            b.getPositionen().add(bp);
        }
        lenient().when(bestellungen.findById(d.id())).thenReturn(Optional.of(b));
        return b;
    }

    private void zaehlerStehtAuf(long stand) {
        DokumentnummerCounter z = new DokumentnummerCounter();
        z.setMonthKey("B");
        z.setCounter(stand);
        when(zaehler.findByMonthKeyMitSperre(any())).thenReturn(Optional.of(z));
    }

    // ---------------------------------------------------------------- Entwurf

    @Test
    @DisplayName("Entwurf: Bezeichnung, Nummer und Einheit kommen aus dem Artikelstamm, Menge wird auf drei Stellen gerundet, keine Bestellnummer")
    void entwurfAnlegen() {
        BestellungDto d = service.anlegen(eingabe(frei(10L, "12.5"), frei(11L, "200")));

        assertThat(d.status()).isEqualTo(BestellStatus.ENTWURF);
        assertThat(d.bestellnummer()).isNull();
        assertThat(d.lieferstand()).isEqualTo("ENTWURF");
        assertThat(d.lieferantName()).isEqualTo("Stahlhandel Muster");
        assertThat(d.lieferantEmails()).containsExactly("bestellung@stahl.example.org");
        assertThat(d.positionen()).extracting(BestellungDto.Position::bezeichnung, BestellungDto.Position::einheit, BestellungDto.Position::artikelnummer)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Rohr 40x40", "m", "R-4040"), org.assertj.core.groups.Tuple.tuple("Schraube M8", "Stk", "S-M8"));
        assertThat(d.positionen().get(0).menge()).isEqualByComparingTo("12.500");
    }

    @Test
    @DisplayName("Ohne Preis in der Eingabe gilt der aktuelle Preis des Lieferanten; die Summe rechnet Menge mal Preis")
    void preisAusPreisliste() {
        LieferantenArtikelPreise p = new LieferantenArtikelPreise();
        p.setPreis(new BigDecimal("4.2000"));
        when(preise.findByArtikel_IdAndLieferant_IdAndAktuellTrue(10L, 3L)).thenReturn(Optional.of(p));

        BestellungDto d = service.anlegen(eingabe(frei(10L, "10"), new BestellungEingabe.Position(11L, new BigDecimal("5"), null, new BigDecimal("0.10"), null, null, null, null)));

        assertThat(d.positionen().get(0).einzelpreis()).isEqualByComparingTo("4.2");
        assertThat(d.positionen().get(0).summe()).isEqualByComparingTo("42.00");
        assertThat(d.positionen().get(1).einzelpreis()).isEqualByComparingTo("0.10");
        assertThat(d.summe()).isEqualByComparingTo("42.50");
        assertThat(d.positionen().get(1).herkunft()).isEqualTo(BestellHerkunft.FREI);
    }

    @Test
    @DisplayName("Ohne Lieferant, ohne Positionen, mit Menge 0 oder unbekanntem Artikel wird abgelehnt und nichts gespeichert")
    void ablehnungen() {
        assertThatThrownBy(() -> service.anlegen(new BestellungEingabe(null, null, List.of(frei(10L, "1"))))).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Lieferanten");
        assertThatThrownBy(() -> service.anlegen(new BestellungEingabe(3L, null, List.of()))).hasMessageContaining("mindestens eine Position");
        assertThatThrownBy(() -> service.anlegen(eingabe(frei(10L, "0")))).hasMessageContaining("Menge größer als 0");
        assertThatThrownBy(() -> service.anlegen(eingabe(frei(10L, "-2")))).hasMessageContaining("Menge größer als 0");
        assertThatThrownBy(() -> service.anlegen(eingabe(frei(10L, "99999999")))).hasMessageContaining("unrealistisch");
        assertThatThrownBy(() -> service.anlegen(eingabe(frei(999L, "1")))).hasMessageContaining("Artikel gibt es nicht");
        assertThatThrownBy(() -> service.anlegen(new BestellungEingabe(99L, null, List.of(frei(10L, "1"))))).hasMessageContaining("Lieferanten gibt es nicht");
        verify(bestellungen, never()).save(any());
    }

    @Test
    @DisplayName("Projektzeile: Projekt und Materialposition müssen zusammenpassen, dieselbe Materialposition darf nicht doppelt vorkommen")
    void projektZeile() {
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(55L);
        aip.setProjekt(projekt);
        aip.setArtikel(rohr);
        when(projektPositionen.findById(55L)).thenReturn(Optional.of(aip));
        BestellungEingabe.Position gut = new BestellungEingabe.Position(10L, new BigDecimal("6"), null, null, BestellHerkunft.PROJEKT, 7L, 55L, "Zuschnitt 3 m");

        BestellungDto d = service.anlegen(eingabe(gut));

        assertThat(d.positionen().get(0).projektName()).isEqualTo("2026/014 Anbau Halle");
        assertThat(d.positionen().get(0).artikelInProjektId()).isEqualTo(55L);
        assertThat(d.projekte()).containsExactly("2026/014 Anbau Halle");
        assertThatThrownBy(() -> service.anlegen(eingabe(gut, gut))).hasMessageContaining("doppelt");
        assertThatThrownBy(() -> service.anlegen(eingabe(new BestellungEingabe.Position(10L, BigDecimal.ONE, null, null, BestellHerkunft.PROJEKT, null, null, null))))
                .hasMessageContaining("braucht ihr Projekt");
        Projekt anderes = new Projekt();
        anderes.setId(8L);
        when(projekte.findById(8L)).thenReturn(Optional.of(anderes));
        assertThatThrownBy(() -> service.anlegen(eingabe(new BestellungEingabe.Position(10L, BigDecimal.ONE, null, null, BestellHerkunft.PROJEKT, 8L, 55L, null))))
                .hasMessageContaining("gehört nicht zu diesem Projekt");
    }

    @Test
    @DisplayName("Einheit: ohne Angabe die des Artikels, mit Angabe die gewünschte aus der erlaubten Liste (Profil in Stück), sonst Ablehnung")
    void einheitUeberschreiben() {
        BestellungDto d = service.anlegen(eingabe(new BestellungEingabe.Position(10L, new BigDecimal("4"), "Stk", null, BestellHerkunft.FREI, null, null, null), frei(10L, "3")));

        assertThat(d.positionen()).extracting(BestellungDto.Position::einheit).containsExactly("Stk", "m");
        assertThatThrownBy(() -> service.anlegen(eingabe(new BestellungEingabe.Position(10L, BigDecimal.ONE, "Fuhre", null, BestellHerkunft.FREI, null, null, null))))
                .hasMessageContaining("Einheit").hasMessageContaining("Fuhre");
    }

    @Test
    @DisplayName("Ändern ersetzt die Positionen eines Entwurfs; Löschen geht nur beim Entwurf")
    void aendernUndLoeschen() {
        LieferantenBestellung b = entwurf();

        BestellungDto d = service.aendern(b.getId(), eingabe(frei(11L, "3")));
        assertThat(d.positionen()).hasSize(1);
        service.loeschen(b.getId());
        verify(bestellungen).delete(b);

        b.setStatus(BestellStatus.BESTELLT);
        assertThatThrownBy(() -> service.aendern(b.getId(), eingabe(frei(11L, "3")))).hasMessageContaining("nicht mehr ändern");
        assertThatThrownBy(() -> service.loeschen(b.getId())).hasMessageContaining("Nur ein Entwurf");
    }

    // ---------------------------------------------------------------- Bestellen

    @Test
    @DisplayName("Bestellen vergibt die nächste Nummer des Monats (B-JJJJ-MM-0001) und zählt den Zähler hoch")
    void bestellenVergibtNummer() {
        LieferantenBestellung b = entwurf();
        zaehlerStehtAuf(41);

        BestellungDto d = service.bestellen(b.getId(), "email");

        String monat = LocalDate.now().toString().substring(0, 7);
        assertThat(d.bestellnummer()).isEqualTo("B-" + monat + "-0042");
        assertThat(d.status()).isEqualTo(BestellStatus.BESTELLT);
        assertThat(d.bestelltPer()).isEqualTo("EMAIL");
        assertThat(d.bestelltAm()).isNotNull();
        assertThat(d.lieferstand()).isEqualTo("BESTELLT");
        verify(zaehler).save(org.mockito.ArgumentMatchers.argThat(z -> z.getCounter() == 42L));
    }

    @Test
    @DisplayName("Ist der Zähler des Monats noch leer, beginnt er mit 0001 und der Schlüssel enthält den Vorsatz B")
    void ersteNummerImMonat() {
        LieferantenBestellung b = entwurf();
        when(zaehler.findByMonthKeyMitSperre(any())).thenReturn(Optional.empty());

        BestellungDto d = service.bestellen(b.getId(), null);

        assertThat(d.bestellnummer()).endsWith("-0001");
        verify(zaehler).findByMonthKeyMitSperre(org.mockito.ArgumentMatchers.argThat(k -> k.startsWith("B20") && k.length() == 7));
        assertThat(d.bestelltPer()).isEqualTo("EMAIL");
    }

    @Test
    @DisplayName("Bestellen markiert die zugehörigen Projektpositionen als bestellt (über den bestehenden Weg), freie Zeilen nicht")
    void bestellenMarkiertProjektpositionen() {
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(55L);
        aip.setProjekt(projekt);
        aip.setArtikel(rohr);
        when(projektPositionen.findById(55L)).thenReturn(Optional.of(aip));
        when(projektPositionen.existsById(55L)).thenReturn(true);
        BestellungDto d0 = service.anlegen(eingabe(new BestellungEingabe.Position(10L, new BigDecimal("6"), null, null, BestellHerkunft.PROJEKT, 7L, 55L, null), frei(11L, "10")));
        LieferantenBestellung b = new LieferantenBestellung();
        b.setId(d0.id());
        b.setLieferant(lieferant);
        b.setErstelltAm(LocalDateTime.now());
        for (BestellungDto.Position p : d0.positionen()) {
            BestellPosition bp = new BestellPosition();
            bp.setId(p.id());
            bp.setBestellung(b);
            bp.setBezeichnung(p.bezeichnung());
            bp.setMenge(p.menge());
            bp.setEinheit(p.einheit());
            bp.setHerkunft(p.herkunft());
            bp.setArtikelInProjektId(p.artikelInProjektId());
            b.getPositionen().add(bp);
        }
        when(bestellungen.findById(d0.id())).thenReturn(Optional.of(b));
        zaehlerStehtAuf(0);

        service.bestellen(b.getId(), "EMAIL");

        verify(projektBestellungen).setBestellt(55L, true);
        verify(projektBestellungen, never()).setBestellt(anyLong(), eq(false));
    }

    @Test
    @DisplayName("Ist eine Projektposition inzwischen anderweitig bestellt, wird das Bestellen abgelehnt (ohne Nummer zu verbrauchen)")
    void bestellenLehntBereitsBestelltAb() {
        LieferantenBestellung b = entwurf();
        b.getPositionen().get(0).setArtikelInProjektId(55L);
        ArtikelInProjekt aip = new ArtikelInProjekt();
        aip.setId(55L);
        aip.setProjekt(projekt);
        aip.setBestellt(true);
        when(projektPositionen.findById(55L)).thenReturn(Optional.of(aip));

        assertThatThrownBy(() -> service.bestellen(b.getId(), "EMAIL")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("schon als bestellt markiert");

        assertThat(b.getBestellnummer()).isNull();
        assertThat(b.getStatus()).isEqualTo(BestellStatus.ENTWURF);
        verify(zaehler, never()).save(any());
    }

    @Test
    @DisplayName("Bestellen: zweimal geht nicht, ohne Positionen geht nicht, unbekannte Art wird abgelehnt")
    void bestellenAblehnungen() {
        LieferantenBestellung b = entwurf();
        assertThatThrownBy(() -> service.bestellen(b.getId(), "Brieftaube")).hasMessageContaining("E-Mail oder anders");
        zaehlerStehtAuf(0);
        service.bestellen(b.getId(), "ANDERS");
        assertThatThrownBy(() -> service.bestellen(b.getId(), "EMAIL")).hasMessageContaining("schon bestellt");

        LieferantenBestellung leer = new LieferantenBestellung();
        leer.setId(200L);
        leer.setLieferant(lieferant);
        leer.setErstelltAm(LocalDateTime.now());
        when(bestellungen.findById(200L)).thenReturn(Optional.of(leer));
        assertThatThrownBy(() -> service.bestellen(200L, "EMAIL")).hasMessageContaining("noch keine Positionen");
    }

    // ---------------------------------------------------------------- Lieferung

    private LieferantenBestellung bestellt() {
        LieferantenBestellung b = entwurf();
        zaehlerStehtAuf(0);
        service.bestellen(b.getId(), "EMAIL");
        return b;
    }

    @Test
    @DisplayName("Lieferstand: bestellt, teilweise geliefert, geliefert; zurücknehmen geht")
    void lieferstand() {
        LieferantenBestellung b = bestellt();
        Long erste = b.getPositionen().get(0).getId();

        assertThat(service.liefern(b.getId(), erste, true).lieferstand()).isEqualTo("TEILWEISE");
        BestellungDto voll = service.alleGeliefert(b.getId());
        assertThat(voll.lieferstand()).isEqualTo("GELIEFERT");
        assertThat(voll.anzahlGeliefert()).isEqualTo(2);
        assertThat(voll.ueberfaellig()).isFalse();
        assertThat(service.liefern(b.getId(), erste, false).lieferstand()).isEqualTo("TEILWEISE");
        assertThatThrownBy(() -> service.liefern(b.getId(), 9999L, true)).hasMessageContaining("gehört nicht zur Bestellung");
    }

    @Test
    @DisplayName("Geliefert lässt sich nur bei einer bestellten Bestellung eintragen")
    void liefernNurBeiBestellt() {
        LieferantenBestellung b = entwurf();

        assertThatThrownBy(() -> service.liefern(b.getId(), b.getPositionen().get(0).getId(), true)).hasMessageContaining("nur bei einer bestellten");
        assertThatThrownBy(() -> service.alleGeliefert(b.getId())).hasMessageContaining("nur bei einer bestellten");
    }

    @Test
    @DisplayName("Überfällig: ab 7 Tagen ohne vollständige Lieferung, nur als Anzeige; geliefert oder jünger ist nicht überfällig")
    void ueberfaellig() {
        LieferantenBestellung b = bestellt();

        assertThat(service.lade(b.getId()).ueberfaellig()).isFalse();
        b.setBestelltAm(LocalDateTime.now().minusDays(7));
        BestellungDto d = service.lade(b.getId());
        assertThat(d.ueberfaellig()).isTrue();
        assertThat(d.tageSeitBestellung()).isEqualTo(7);
        assertThat(d.ueberfaelligNachTagen()).isEqualTo(7);
        b.setBestelltAm(LocalDateTime.now().minusDays(6));
        assertThat(service.lade(b.getId()).ueberfaellig()).isFalse();
        b.setBestelltAm(LocalDateTime.now().minusDays(30));
        service.alleGeliefert(b.getId());
        assertThat(service.lade(b.getId()).ueberfaellig()).isFalse();
    }

    // ---------------------------------------------------------------- Storno

    @Test
    @DisplayName("Storno braucht einen Grund, setzt nicht gelieferte Projektpositionen zurück und lässt die Nummer stehen")
    void stornieren() {
        LieferantenBestellung b = bestellt();
        b.getPositionen().get(0).setArtikelInProjektId(55L);
        b.getPositionen().get(1).setArtikelInProjektId(56L);
        b.getPositionen().get(1).setGeliefertAm(LocalDate.now());
        when(projektPositionen.existsById(55L)).thenReturn(true);
        String nummer = b.getBestellnummer();

        assertThatThrownBy(() -> service.stornieren(b.getId(), " x ")).hasMessageContaining("warum");
        BestellungDto d = service.stornieren(b.getId(), "Lieferant liefert nicht mehr");

        assertThat(d.status()).isEqualTo(BestellStatus.STORNIERT);
        assertThat(d.lieferstand()).isEqualTo("STORNIERT");
        assertThat(d.bestellnummer()).isEqualTo(nummer);
        assertThat(d.stornoGrund()).isEqualTo("Lieferant liefert nicht mehr");
        verify(projektBestellungen).setBestellt(55L, false);
        verify(projektBestellungen, never()).setBestellt(eq(56L), org.mockito.ArgumentMatchers.anyBoolean());
        assertThat(d.ueberfaellig()).isFalse();
        assertThatThrownBy(() -> service.stornieren(b.getId(), "Nochmal")).hasMessageContaining("schon storniert");
    }

    @Test
    @DisplayName("Ein Entwurf wird nicht storniert, sondern gelöscht")
    void entwurfNichtStornieren() {
        LieferantenBestellung b = entwurf();

        assertThatThrownBy(() -> service.stornieren(b.getId(), "Grund")).hasMessageContaining("gelöscht, nicht storniert");
    }

    // ---------------------------------------------------------------- Liste

    @Test
    @DisplayName("Liste: Filter nach Stand, Lieferstand, Lieferant, Projekt und überfällig")
    void listeFiltert() {
        LieferantenBestellung offen = bestellt();
        LieferantenBestellung alt = new LieferantenBestellung();
        alt.setId(300L);
        alt.setLieferant(lieferant);
        alt.setStatus(BestellStatus.BESTELLT);
        alt.setBestellnummer("B-2026-09-0001");
        alt.setErstelltAm(LocalDateTime.now().minusDays(20));
        alt.setBestelltAm(LocalDateTime.now().minusDays(20));
        BestellPosition p = new BestellPosition();
        p.setId(1L);
        p.setBestellung(alt);
        p.setBezeichnung("Rohr");
        p.setMenge(BigDecimal.ONE);
        p.setEinheit("m");
        p.setHerkunft(BestellHerkunft.PROJEKT);
        p.setProjekt(projekt);
        alt.getPositionen().add(p);
        LieferantenBestellung entwurf = new LieferantenBestellung();
        entwurf.setId(301L);
        entwurf.setLieferant(lieferant);
        entwurf.setErstelltAm(LocalDateTime.now());
        when(bestellungen.findAllByOrderByIdDesc()).thenReturn(List.of(entwurf, alt, offen));

        assertThat(service.liste(null, null, null, false)).hasSize(3);
        assertThat(service.liste("entwurf", null, null, false)).extracting(BestellungDto::id).containsExactly(301L);
        assertThat(service.liste("BESTELLT", null, null, false)).hasSize(2);
        assertThat(service.liste("TEILWEISE", null, null, false)).isEmpty();
        assertThat(service.liste(null, 3L, null, false)).hasSize(3);
        assertThat(service.liste(null, 4L, null, false)).isEmpty();
        assertThat(service.liste(null, null, 7L, false)).extracting(BestellungDto::id).containsExactly(300L);
        assertThat(service.liste(null, null, null, true)).extracting(BestellungDto::bestellnummer).containsExactly("B-2026-09-0001");
        assertThat(service.liste("  ", null, null, false)).hasSize(3);
    }

    // ---------------------------------------------------------------- Vorschläge

    private LagerBestandDto zeile(Long id, String name, String bestand, String mindest) {
        BigDecimal m = mindest == null ? null : new BigDecimal(mindest);
        BigDecimal b = new BigDecimal(bestand);
        return new LagerBestandDto(id, name, "NR-" + id, "Stk", b, m, m != null && b.compareTo(m) < 0, b.signum() < 0, List.of(), null, null, null, null);
    }

    @Test
    @DisplayName("Mindestbestand: Menge bis zum Mindestbestand, schon Unterwegs abgezogen, günstigster Lieferant vorgeschlagen, voll gedeckte weggelassen")
    void vorschlaegeMindestbestand() {
        LieferantenArtikelPreise teuer = new LieferantenArtikelPreise();
        teuer.setLieferant(lieferant);
        teuer.setPreis(new BigDecimal("0.15"));
        when(lager.bestand(null, true)).thenReturn(List.of(zeile(11L, "Schraube M8", "20", "100"), zeile(10L, "Rohr 40x40", "5", "10"),
                zeile(12L, "Dübel", "0", null), zeile(13L, "Mutter M8", "3", "50")));
        when(preise.findeAktuellePreise(11L)).thenReturn(List.of(teuer));
        when(preise.findeAktuellePreise(10L)).thenReturn(List.of());
        BestellPosition unterwegs = new BestellPosition();
        unterwegs.setArtikel(artikel(13L, "Mutter M8", "NR-13", Verrechnungseinheit.STUECK));
        unterwegs.setMenge(new BigDecimal("47"));
        BestellPosition unterwegs2 = new BestellPosition();
        unterwegs2.setArtikel(schraube);
        unterwegs2.setMenge(new BigDecimal("30"));
        when(positionen.findByBestellung_StatusInAndHerkunftAndGeliefertAmIsNull(any(), eq(BestellHerkunft.LAGER))).thenReturn(List.of(unterwegs, unterwegs2));

        List<BestellVorschlagDto> v = service.vorschlaegeMindestbestand();

        assertThat(v).extracting(BestellVorschlagDto::bezeichnung).containsExactly("Rohr 40x40", "Schraube M8");
        BestellVorschlagDto rohrV = v.get(0);
        assertThat(rohrV.menge()).isEqualByComparingTo("5");
        assertThat(rohrV.lieferantId()).isNull();
        assertThat(rohrV.herkunft()).isEqualTo(BestellHerkunft.LAGER);
        BestellVorschlagDto schraubeV = v.get(1);
        assertThat(schraubeV.menge()).isEqualByComparingTo("50");
        assertThat(schraubeV.lieferantName()).isEqualTo("Stahlhandel Muster");
        assertThat(schraubeV.einzelpreis()).isEqualByComparingTo("0.15");
        assertThat(schraubeV.hinweis()).contains("Bestand 20").contains("Mindestbestand 100").contains("schon unterwegs 30");
    }

    @Test
    @DisplayName("Projektmaterial: nur Positionen, die noch nicht bestellt sind und nicht aus dem Lager kommen; Meter vor Stückzahl; Lieferant der Position")
    void vorschlaegeProjekt() {
        ArtikelInProjekt offen = new ArtikelInProjekt();
        offen.setId(1L);
        offen.setArtikel(rohr);
        offen.setProjekt(projekt);
        offen.setMeter(new BigDecimal("6.50"));
        offen.setStueckzahl(2);
        offen.setLieferant(lieferant);
        offen.setKommentar("Zuschnitt 3,25 m");
        ArtikelInProjekt stk = new ArtikelInProjekt();
        stk.setId(2L);
        stk.setArtikel(schraube);
        stk.setProjekt(projekt);
        stk.setStueckzahl(40);
        ArtikelInProjekt bestellt = new ArtikelInProjekt();
        bestellt.setId(3L);
        bestellt.setArtikel(rohr);
        bestellt.setBestellt(true);
        bestellt.setStueckzahl(1);
        ArtikelInProjekt ausLager = new ArtikelInProjekt();
        ausLager.setId(4L);
        ausLager.setArtikel(rohr);
        ausLager.setAusLager(true);
        ausLager.setStueckzahl(1);
        ArtikelInProjekt leer = new ArtikelInProjekt();
        leer.setId(5L);
        leer.setArtikel(rohr);
        when(projektPositionen.findByProjekt_Id(7L)).thenReturn(List.of(stk, offen, bestellt, ausLager, leer));
        LieferantenArtikelPreise preis = new LieferantenArtikelPreise();
        preis.setPreis(new BigDecimal("8.9"));
        when(preise.findByArtikel_IdAndLieferant_IdAndAktuellTrue(10L, 3L)).thenReturn(Optional.of(preis));

        List<BestellVorschlagDto> v = service.vorschlaegeProjekt(7L);

        assertThat(v).extracting(BestellVorschlagDto::artikelInProjektId).containsExactly(1L, 2L);
        assertThat(v.get(0).menge()).isEqualByComparingTo("6.5");
        assertThat(v.get(0).einheit()).isEqualTo("m");
        assertThat(v.get(0).lieferantName()).isEqualTo("Stahlhandel Muster");
        assertThat(v.get(0).einzelpreis()).isEqualByComparingTo("8.9");
        assertThat(v.get(0).projektName()).isEqualTo("2026/014 Anbau Halle");
        assertThat(v.get(0).herkunft()).isEqualTo(BestellHerkunft.PROJEKT);
        assertThat(v.get(1).menge()).isEqualByComparingTo("40");
        assertThat(v.get(1).einheit()).isEqualTo("Stk");
        assertThat(v.get(0).einheit()).isEqualTo("m");
        assertThat(v.get(1).lieferantId()).isNull();
        assertThatThrownBy(() -> service.vorschlaegeProjekt(99L)).hasMessageContaining("Projekt gibt es nicht");
    }
}
