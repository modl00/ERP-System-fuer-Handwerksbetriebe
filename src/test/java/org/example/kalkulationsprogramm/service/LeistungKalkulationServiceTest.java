package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ArbeitsgangStundensatz;
import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.LeistungPosition;
import org.example.kalkulationsprogramm.domain.LieferantenArtikelPreise;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationSpeichernDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationSpeichernDto.Position;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangStundensatzRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LeistungKalkulationServiceTest {

    LeistungRepository leistungen = mock(LeistungRepository.class);
    ArtikelRepository artikel = mock(ArtikelRepository.class);
    ArbeitsgangRepository arbeitsgaenge = mock(ArbeitsgangRepository.class);
    ArbeitsgangStundensatzRepository stundensaetze = mock(ArbeitsgangStundensatzRepository.class);
    org.example.kalkulationsprogramm.repository.FremdleistungRepository fremdleistungen = mock(org.example.kalkulationsprogramm.repository.FremdleistungRepository.class);
    Clock uhr = Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"), ZoneId.of("Europe/Berlin"));
    LeistungKalkulationService service;
    Leistung leistung;

    @BeforeEach
    void setUp() {
        service = new LeistungKalkulationService(leistungen, artikel, arbeitsgaenge, stundensaetze, new ArtikelPositionsPreisService(), fremdleistungen, uhr);
        leistung = new Leistung();
        leistung.setId(7L);
        leistung.setBezeichnung("Geländer montieren");
        leistung.setPreis(new BigDecimal("100.00"));
        when(leistungen.findById(7L)).thenReturn(Optional.of(leistung));
        when(leistungen.save(org.mockito.ArgumentMatchers.any(Leistung.class))).thenAnswer(a -> a.getArgument(0));
    }

    /** Artikel mit Einkaufspreis und Aufschlag in Prozent (null = kein Preis). */
    private Artikel artikel(long id, String name, String einkauf, String aufschlag) {
        Artikel a = new Artikel();
        a.setId(id);
        a.setKurzbeschreibung(name);
        a.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        a.setVerkaufsaufschlagProzent(aufschlag == null ? null : new BigDecimal(aufschlag));
        if (einkauf != null) {
            LieferantenArtikelPreise p = new LieferantenArtikelPreise();
            p.setArtikel(a);
            p.setPreis(new BigDecimal(einkauf));
            p.setAktuell(true);
            a.getArtikelpreis().add(p);
        }
        when(artikel.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    private Arbeitsgang arbeitsgang(long id, String name, String satz) {
        Arbeitsgang g = new Arbeitsgang();
        g.setId(id);
        g.setBeschreibung(name);
        when(arbeitsgaenge.findById(id)).thenReturn(Optional.of(g));
        if (satz != null) {
            ArbeitsgangStundensatz s = new ArbeitsgangStundensatz();
            s.setJahr(2026);
            s.setSatz(new BigDecimal(satz));
            when(stundensaetze.findTopByArbeitsgangIdAndJahrOrderByIdDesc(id, 2026)).thenReturn(Optional.of(s));
        } else {
            when(stundensaetze.findTopByArbeitsgangIdAndJahrOrderByIdDesc(anyLong(), anyInt())).thenReturn(Optional.empty());
            when(stundensaetze.findTopByArbeitsgangIdOrderByJahrDesc(id)).thenReturn(Optional.empty());
        }
        return g;
    }

    private static Position art(long id, String menge) {
        return new Position("ARTIKEL", id, null, new BigDecimal(menge), null);
    }

    private static Position arb(long id, String stunden) {
        return new Position("ARBEIT", null, id, new BigDecimal(stunden), null);
    }

    @Test
    void rechnetMaterialArbeitUndZuschlag() {
        artikel(1, "Handlauf", "10.00", "20");   // Verkauf 12,00 je Stück
        arbeitsgang(5, "Montage", "60.00");

        LeistungKalkulationDto d = service.speichern(7L,
                new LeistungKalkulationSpeichernDto(true, new BigDecimal("10"), List.of(art(1, "2.5"), arb(5, "1.5"))));

        assertEquals(new BigDecimal("30.00"), d.materialSumme());      // 12,00 x 2,5
        assertEquals(new BigDecimal("90.00"), d.arbeitSumme());        // 60,00 x 1,5
        assertEquals(new BigDecimal("120.00"), d.zwischensumme());
        assertEquals(new BigDecimal("12.00"), d.zuschlag());           // 10 %
        assertEquals(new BigDecimal("132.00"), d.preisBerechnet());
        assertTrue(d.kalkuliert());
        assertTrue(d.vollstaendig());
        assertEquals(new BigDecimal("132.00"), leistung.getPreis(), "der berechnete Preis wird an der Leistung gespeichert");
        assertFalse(d.preisVeraltet());
        assertEquals("Handlauf", d.positionen().get(0).bezeichnung());
        assertEquals("Std.", d.positionen().get(1).einheit());
    }

    @Test
    void ohneHaekchenBleibtDerHandPreisUndDieKalkulationIstNurVorschlag() {
        artikel(1, "Handlauf", "10.00", "20");

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "1"))));

        assertEquals(new BigDecimal("12.00"), d.preisBerechnet());
        assertEquals(new BigDecimal("100.00"), leistung.getPreis(), "der Handpreis bleibt unverändert");
        assertFalse(d.kalkuliert());
        assertTrue(d.preisVeraltet(), "Vorschlag weicht vom gespeicherten Preis ab");
    }

    @Test
    void teurerGewordenerArtikelAendertDenGespeichertenPreisNichtMarkiertIhnAberAlsVeraltet() {
        Artikel a = artikel(1, "Handlauf", "10.00", "0");
        service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(art(1, "1"))));
        assertEquals(new BigDecimal("10.00"), leistung.getPreis());

        a.getArtikelpreis().get(0).setPreis(new BigDecimal("14.00"));
        LeistungKalkulationDto d = service.laden(7L);

        assertEquals(new BigDecimal("10.00"), leistung.getPreis(), "Laden ändert nichts");
        assertEquals(new BigDecimal("14.00"), d.preisBerechnet());
        assertTrue(d.preisVeraltet());

        LeistungKalkulationDto neu = service.preisUebernehmen(7L);
        assertEquals(new BigDecimal("14.00"), leistung.getPreis());
        assertFalse(neu.preisVeraltet());
    }

    @Test
    void fehlenderPreisBlockiertDenBerechnetenPreis() {
        artikel(1, "Sonderteil ohne Preis", null, "20");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(art(1, "1")))));

        assertTrue(e.getReason().contains("Sonderteil ohne Preis"), e.getReason());
        verify(leistungen, never()).save(org.mockito.ArgumentMatchers.any(Leistung.class));
    }

    @Test
    void fehlenderStundensatzBlockiertUndWirdBenannt() {
        arbeitsgang(5, "Schweißen", null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(arb(5, "2")))));

        assertTrue(e.getReason().contains("Stundensatz"), e.getReason());
    }

    @Test
    void ohneHaekchenSindFehlendePreiseNurEinHinweis() {
        artikel(1, "Sonderteil ohne Preis", null, "20");

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "1"))));

        assertFalse(d.vollstaendig());
        assertEquals("KEIN_PREIS", d.positionen().get(0).hinweis());
        assertEquals(new BigDecimal("100.00"), leistung.getPreis());
    }

    @Test
    void berechnetOhneZeilenIstNichtMoeglich() {
        assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of())));
        assertThrows(ResponseStatusException.class, () -> service.preisUebernehmen(7L));
    }

    @Test
    void ungueltigeEingabenWerdenAbgelehnt() {
        artikel(1, "Handlauf", "10.00", "0");
        arbeitsgang(5, "Montage", "60.00");
        BigDecimal zuschlag = null;

        for (Position p : List.of(art(1, "0"), art(1, "-1"), art(1, "1000001"), art(99, "1"),
                new Position("ARTIKEL", null, null, BigDecimal.ONE, null),
                new Position("ARBEIT", null, null, BigDecimal.ONE, null),
                new Position("SONSTIGES", 1L, null, BigDecimal.ONE, null),
                new Position(null, 1L, null, BigDecimal.ONE, null),
                new Position("ARTIKEL", 1L, null, null, null),
                new Position("ARTIKEL", 1L, null, BigDecimal.ONE, "x".repeat(256)))) {
            assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(false, zuschlag, List.of(p))),
                    "muss abgelehnt werden: " + p);
        }
        assertThrows(ResponseStatusException.class,
                () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(false, new BigDecimal("-1"), List.of())));
        assertThrows(ResponseStatusException.class,
                () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(false, new BigDecimal("1000.01"), List.of())));
        List<Position> zuViele = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            zuViele.add(art(1, "1"));
        }
        assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, zuViele)));
        assertThrows(ResponseStatusException.class, () -> service.speichern(7L, null));
    }

    @Test
    void unbekannteLeistungIstNichtGefunden() {
        for (Long id : new Long[] { 0L, -1L, Long.MAX_VALUE, null }) {
            ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.laden(id));
            assertEquals(404, e.getStatusCode().value());
        }
    }

    @Test
    void vorschauRechnetOhneZuSpeichern() {
        artikel(1, "Handlauf", "10.00", "20");
        arbeitsgang(5, "Montage", "60.00");

        LeistungKalkulationDto d = service.vorschau(7L,
                new LeistungKalkulationSpeichernDto(true, new BigDecimal("10"), List.of(art(1, "2.5"), arb(5, "1.5"))));

        assertEquals(new BigDecimal("132.00"), d.preisBerechnet());
        assertEquals(new BigDecimal("100.00"), leistung.getPreis(), "die Vorschau ändert den Preis nicht");
        assertTrue(leistung.getPositionen().isEmpty(), "die Vorschau legt keine Zeilen an");
        assertFalse(leistung.isKalkuliert());
        verify(leistungen, never()).save(org.mockito.ArgumentMatchers.any(Leistung.class));
        assertThrows(ResponseStatusException.class, () -> service.vorschau(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "0")))));
    }

    @Test
    void ersetztDieZeilenKomplettUndBehaeltDieReihenfolge() {
        artikel(1, "Handlauf", "10.00", "0");
        artikel(2, "Pfosten", "5.00", "0");
        service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "1"), art(2, "2"))));
        assertEquals(2, leistung.getPositionen().size());

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(2, "4"))));

        assertEquals(1, leistung.getPositionen().size());
        assertEquals("Pfosten", d.positionen().get(0).bezeichnung());
        assertEquals(new BigDecimal("20.00"), d.materialSumme());
    }

    @Test
    void scriptInDerNotizWirdAlsTextGespeichertUndNichtAusgefuehrt() {
        artikel(1, "Handlauf", "10.00", "0");
        String schad = "<script>alert(1)</script>'; DROP TABLE leistung; --";

        LeistungKalkulationDto d = service.speichern(7L,
                new LeistungKalkulationSpeichernDto(false, null, List.of(new Position("ARTIKEL", 1L, null, BigDecimal.ONE, schad))));

        assertEquals(schad, d.positionen().get(0).notiz(), "Text bleibt Text, die Oberfläche gibt ihn nur als Text aus");
    }

    private static Position arbZeiten(long id, String ruest, String bearbeitung) {
        return new Position("ARBEIT", null, id, null, null, ruest == null ? null : new BigDecimal(ruest), bearbeitung == null ? null : new BigDecimal(bearbeitung));
    }

    private static LeistungKalkulationSpeichernDto mitLos(String los, Position... zeilen) {
        return new LeistungKalkulationSpeichernDto(true, null, los == null ? null : new BigDecimal(los), List.of(zeilen));
    }

    @Test
    void ruestzeitVerteiltSichAufDieLosgroesseUndBearbeitungszeitGiltJeEinheit() {
        arbeitsgang(5, "Schweißen", "60.00");

        // 30 min Rüsten auf 50 Einheiten = 0,6 min je Einheit, dazu 6 min Bearbeitung = 6,6 min = 0,11 Std. x 60 = 6,60
        LeistungKalkulationDto d = service.speichern(7L, mitLos("50", arbZeiten(5, "30", "6")));

        assertEquals(new BigDecimal("6.60"), d.arbeitSumme());
        assertEquals(new BigDecimal("6.60"), d.preisBerechnet());
        assertEquals(new BigDecimal("50.000"), d.losgroesse());
        LeistungKalkulationDto.Position z = d.positionen().get(0);
        assertEquals(new BigDecimal("0.110"), z.menge(), "die errechneten Stunden je Einheit");
        assertEquals(new BigDecimal("30.00"), z.ruestzeitMinuten());
        assertEquals(new BigDecimal("6.00"), z.bearbeitungszeitMinuten());
        assertEquals(new BigDecimal("50.000"), leistung.getLosgroesse(), "die Losgröße steht an der Leistung");
    }

    private Arbeitsgang bohren() {
        Arbeitsgang g = arbeitsgang(6, "Bohren", "60.00");
        g.setFormelFelderJson("[{\"name\":\"D\",\"bezeichnung\":\"Bohrerdurchmesser\",\"einheit\":\"mm\",\"standard\":8},{\"name\":\"Tiefe\",\"bezeichnung\":\"Bohrtiefe\",\"einheit\":\"mm\",\"standard\":20}]");
        g.setFormelBearbeitung("D*Tiefe/100");   // 8 mm x 20 mm = 1,6 min, 35 mm x 20 mm = 7 min
        g.setFormelRuest("wenn(D>20;10;5)");
        return g;
    }

    private static Position bohrung(java.util.Map<String, BigDecimal> werte) {
        return new Position("ARBEIT", null, 6L, null, null, null, null, null, null, null, null, null, null, null, werte);
    }

    @Test
    void einArbeitsgangMitFormelRechnetDieZeitenAusDenEingegebenenWerten() {
        bohren();

        LeistungKalkulationDto klein = service.speichern(7L, mitLos("1", bohrung(java.util.Map.of("D", new BigDecimal("8")))));
        assertEquals(new BigDecimal("1.60"), klein.positionen().get(0).bearbeitungszeitMinuten(), "Standardwert für die Tiefe, 8 mm");
        assertEquals(new BigDecimal("5.00"), klein.positionen().get(0).ruestzeitMinuten());

        LeistungKalkulationDto gross = service.speichern(7L, mitLos("1", bohrung(java.util.Map.of("D", new BigDecimal("35")))));
        assertEquals(new BigDecimal("7.00"), gross.positionen().get(0).bearbeitungszeitMinuten(), "größerer Durchmesser, längere Zeit");
        assertEquals(new BigDecimal("10.00"), gross.positionen().get(0).ruestzeitMinuten());
        assertTrue(gross.arbeitSumme().compareTo(klein.arbeitSumme()) > 0);
        assertEquals(new BigDecimal("35"), gross.positionen().get(0).variablen().get("d"), "die eingegebenen Werte kommen zurück");
        assertEquals(new BigDecimal("20"), gross.positionen().get(0).variablen().get("tiefe"), "fehlende Werte stehen mit dem Standardwert da");
    }

    @Test
    void dieZeitenAusDerAnfrageZaehlenNichtWennDieFormelGilt() {
        bohren();
        Position p = new Position("ARBEIT", null, 6L, null, null, new BigDecimal("999"), new BigDecimal("999"), null, null, null, null, null, null, null, java.util.Map.of("D", new BigDecimal("8")));
        LeistungKalkulationDto d = service.speichern(7L, mitLos("1", p));
        assertEquals(new BigDecimal("1.60"), d.positionen().get(0).bearbeitungszeitMinuten());
    }

    @Test
    void ohneEingabewerteBleibenBestehendeZeitenAnPositionenUnveraendert() {
        bohren();   // der Arbeitsgang bekommt eine Formel, die Position wurde früher mit Handzeiten gespeichert
        LeistungKalkulationDto d = service.speichern(7L, mitLos("1", arbZeiten(6, "30", "6")));
        assertEquals(new BigDecimal("6.00"), d.positionen().get(0).bearbeitungszeitMinuten());
        assertEquals(new BigDecimal("30.00"), d.positionen().get(0).ruestzeitMinuten());
        assertEquals(null, d.positionen().get(0).variablen());
    }

    @Test
    void einFehlerInDerFormelStehtImKlartextUndSpeichertNichts() {
        Arbeitsgang g = bohren();
        g.setFormelBearbeitung("Tiefe/(D-8)");
        org.springframework.web.server.ResponseStatusException e = org.junit.jupiter.api.Assertions.assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.speichern(7L, mitLos("1", bohrung(java.util.Map.of("D", new BigDecimal("8"))))));
        assertTrue(e.getReason().contains("Bohren") && e.getReason().contains("Division durch null"), e.getReason());
    }

    @Test
    void ohneLosgroesseGiltDieVolleRuestzeitJeEinheit() {
        arbeitsgang(5, "Schweißen", "60.00");

        LeistungKalkulationDto d = service.speichern(7L, mitLos(null, arbZeiten(5, "30", "6")));

        assertEquals(new BigDecimal("1.000"), d.losgroesse());
        assertEquals(new BigDecimal("36.00"), d.arbeitSumme());   // 36 min = 0,6 Std. x 60
    }

    @Test
    void nurEineDerBeidenZeitenGenuegtUndLeereWerdenZuNull() {
        arbeitsgang(5, "Schweißen", "60.00");

        LeistungKalkulationDto nurRuest = service.speichern(7L, mitLos("10", arbZeiten(5, "30", null)));
        assertEquals(new BigDecimal("3.00"), nurRuest.arbeitSumme());   // 3 min je Einheit
        assertEquals(new BigDecimal("0.00"), nurRuest.positionen().get(0).bearbeitungszeitMinuten());

        LeistungKalkulationDto nurBearbeitung = service.speichern(7L, mitLos("10", arbZeiten(5, null, "12")));
        assertEquals(new BigDecimal("12.00"), nurBearbeitung.arbeitSumme());
    }

    @Test
    void zeilenOhneZeitenRechnenWieBisherMitStunden() {
        arbeitsgang(5, "Montage", "60.00");

        LeistungKalkulationDto d = service.speichern(7L, mitLos("50", arb(5, "1.5")));

        assertEquals(new BigDecimal("90.00"), d.arbeitSumme());
        assertEquals(null, d.positionen().get(0).ruestzeitMinuten());
        assertEquals(null, d.positionen().get(0).bearbeitungszeitMinuten());
        assertEquals(new BigDecimal("1.500"), d.positionen().get(0).menge());
    }

    @Test
    void zeitenUndStundenKoennenInEinerLeistungGemischtWerden() {
        arbeitsgang(5, "Montage", "60.00");
        arbeitsgang(6, "Schweißen", "90.00");

        LeistungKalkulationDto d = service.speichern(7L, mitLos("1", arb(5, "1"), arbZeiten(6, "0", "20")));

        assertEquals(new BigDecimal("60.00"), d.positionen().get(0).summe());
        assertEquals(new BigDecimal("30.00"), d.positionen().get(1).summe());   // 20 min = 1/3 Std. x 90
        assertEquals(new BigDecimal("90.00"), d.arbeitSumme());
    }

    @Test
    void ohneStundensatzFehltDerPreisAuchBeiZeiten() {
        arbeitsgang(5, "Ohne Satz", null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.speichern(7L, mitLos("1", arbZeiten(5, "10", "5"))));

        assertTrue(e.getReason().contains("Stundensatz"), e.getReason());
    }

    @Test
    void falscheZeitenUndLosgroessenWerdenAbgelehnt() {
        arbeitsgang(5, "Schweißen", "60.00");

        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("1", arbZeiten(5, "0", "0")))).getReason().contains("größer als 0"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("1", arbZeiten(5, "-1", "5")))).getReason().contains("Rüstzeit"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("1", arbZeiten(5, "1", "100001")))).getReason().contains("Bearbeitungszeit"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("0", arbZeiten(5, "1", "1")))).getReason().contains("Losgröße"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("-5", arb(5, "1")))).getReason().contains("Losgröße"));
        verify(leistungen, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void dieVorschauRechnetMitZeitenUndLosgroesseOhneZuSpeichern() {
        arbeitsgang(5, "Schweißen", "60.00");

        LeistungKalkulationDto d = service.vorschau(7L, mitLos("20", arbZeiten(5, "60", "3")));

        assertEquals(new BigDecimal("6.00"), d.arbeitSumme());   // 60/20 = 3 min Rüsten + 3 min = 6 min = 0,1 Std. x 60
        assertEquals(0, BigDecimal.ONE.compareTo(leistung.getLosgroesse()), "die Vorschau ändert nichts");
        verify(leistungen, never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ------------------------------------------------------------------ Baugruppen (#135, Stufe 1)

    /** Eine Baugruppe (Leistung mit Zeilen), wie sie in der Datenbank stünde. */
    private Leistung baugruppe(long id, String name) {
        Leistung b = new Leistung();
        b.setId(id);
        b.setBezeichnung(name);
        b.setBaugruppe(true);
        when(leistungen.findById(id)).thenReturn(Optional.of(b));
        return b;
    }

    private static void enthaelt(Leistung b, LeistungPosition.Typ typ, Artikel a, Arbeitsgang g, Leistung unter, String menge) {
        LeistungPosition p = new LeistungPosition();
        p.setLeistung(b);
        p.setTyp(typ);
        p.setArtikel(a);
        p.setArbeitsgang(g);
        p.setBaugruppe(unter);
        p.setMenge(new BigDecimal(menge));
        b.getPositionen().add(p);
    }

    private static Position bg(long id, String menge) {
        return new Position("BAUGRUPPE", null, null, new BigDecimal(menge), null, null, null, id, null);
    }

    @Test
    void baugruppenRechnenUeberMehrereEbenenMitMengeJeEinheit() {
        Artikel rohr = artikel(1, "Rohr", "10.00", "20");        // Verkauf 12,00
        Artikel stab = artikel(2, "Füllstab", "1.00", "0");      // Verkauf 1,00
        Arbeitsgang schweissen = arbeitsgang(5, "Schweißen", "60.00");
        Leistung pfosten = baugruppe(11, "Pfosten");
        enthaelt(pfosten, LeistungPosition.Typ.ARTIKEL, rohr, null, null, "1");
        enthaelt(pfosten, LeistungPosition.Typ.ARBEIT, null, schweissen, null, "0.5");      // 30,00
        Leistung feld = baugruppe(12, "Feld");
        enthaelt(feld, LeistungPosition.Typ.BAUGRUPPE, null, null, pfosten, "2");           // 2 x (12 + 30)
        enthaelt(feld, LeistungPosition.Typ.ARTIKEL, stab, null, null, "8");                // 8,00

        // 0,5 Felder je laufendem Meter Geländer, 10 % Zuschlag einmal auf alles
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, new BigDecimal("10"), List.of(bg(12, "0.5"))));

        assertEquals(new BigDecimal("16.00"), d.materialSumme());      // 0,5 x (24 + 8)
        assertEquals(new BigDecimal("30.00"), d.arbeitSumme());        // 0,5 x 60
        assertEquals(new BigDecimal("4.60"), d.zuschlag());            // 10 % von 46, der Zuschlag der Baugruppen selbst zählt nicht
        assertEquals(new BigDecimal("50.60"), d.preisBerechnet());
        assertTrue(d.vollstaendig());
        var zeile = d.positionen().get(0);
        assertEquals("BAUGRUPPE", zeile.typ());
        assertEquals(12L, zeile.baugruppeId());
        assertEquals("Feld", zeile.bezeichnung());
        assertEquals(new BigDecimal("92.00"), zeile.einzelpreis(), "Preis einer Einheit der Baugruppe: 24 + 8 + 60");
        assertEquals(new BigDecimal("46.00"), zeile.summe());
        assertEquals(new BigDecimal("50.60"), leistung.getPreis());
    }

    /** Eine Arbeitszeile mit Rüstzeit in einer Baugruppe. */
    private static void enthaeltRuesten(Leistung b, Arbeitsgang g, String ruestMinuten) {
        LeistungPosition p = new LeistungPosition();
        p.setLeistung(b);
        p.setTyp(LeistungPosition.Typ.ARBEIT);
        p.setArbeitsgang(g);
        p.setMenge(BigDecimal.ONE);
        p.setRuestzeitMinuten(new BigDecimal(ruestMinuten));
        p.setBearbeitungszeitMinuten(BigDecimal.ZERO);
        b.getPositionen().add(p);
    }

    @Test
    void dieLosgroesseEinerUnterbaugruppeErgibtSichDurchAlleEbenenAusDerLosgroesseDarueberMalMengeJeEinheit() {
        // Geländer 50 lfm × 0,5 Felder je lfm × 2 Pfosten je Feld = 50 Pfosten; die Rüstzeit des Pfostens verteilt sich auf diese 50
        Arbeitsgang schweissen = arbeitsgang(5, "Schweißen", "60.00");
        Leistung pfosten = baugruppe(11, "Pfosten");
        pfosten.setLosgroesse(new BigDecimal("1000"));   // die eigene Losgröße gilt nur, wenn der Pfosten für sich allein kalkuliert wird
        enthaeltRuesten(pfosten, schweissen, "30");
        Leistung feld = baugruppe(12, "Feld");
        enthaelt(feld, LeistungPosition.Typ.BAUGRUPPE, null, null, pfosten, "2");

        LeistungKalkulationDto d = service.speichern(7L, mitLos("50", bg(12, "0.5")));

        // 30 min Rüsten / 50 Pfosten = 0,6 min = 0,01 Std. × 60 = 0,60 € je Pfosten; 2 Pfosten je Feld, 0,5 Felder je lfm: 0,60 € je lfm
        assertEquals(new BigDecimal("0.60"), d.arbeitSumme());
        assertEquals(new BigDecimal("0.60"), d.preisBerechnet());
    }

    @Test
    void mehrfachVorkommendeUnterbaugruppenVerteilenDieRuestzeitAufAlleExemplare() {
        // Ein Feld wird zehnmal ins Geländer gesetzt (Menge 10 je Einheit), Losgröße 1: 10 Felder × 2 Pfosten = 20 Pfosten teilen sich die 30 Minuten
        Arbeitsgang schweissen = arbeitsgang(5, "Schweißen", "60.00");
        Leistung pfosten = baugruppe(11, "Pfosten");
        enthaeltRuesten(pfosten, schweissen, "30");
        Leistung feld = baugruppe(12, "Feld");
        enthaelt(feld, LeistungPosition.Typ.BAUGRUPPE, null, null, pfosten, "2");

        LeistungKalkulationDto d = service.speichern(7L, mitLos("1", bg(12, "10")));

        // 30 / 20 = 1,5 min = 0,025 Std. × 60 = 1,50 € je Pfosten, 20 Pfosten = 30,00 € (die Rüstzeit wird einmal bezahlt, nicht zwanzigmal)
        assertEquals(new BigDecimal("30.00"), d.arbeitSumme());
    }

    @Test
    void eineBaugruppeFuerSichAlleinRechnetMitIhrerEigenenLosgroesse() {
        arbeitsgang(5, "Schweißen", "60.00");

        // Als eigene Leistung mit Losgröße 30: 30 min Rüsten / 30 = 1 min = 1,00 € je Einheit
        LeistungKalkulationDto d = service.speichern(7L, mitLos("30", arbZeiten(5, "30", "0")));

        assertEquals(new BigDecimal("1.00"), d.arbeitSumme());
        assertEquals(new BigDecimal("30.000"), d.losgroesse());
    }

    @Test
    void derZuschlagEinerBaugruppeWirdNichtDoppeltGerechnet() {
        Artikel rohr = artikel(1, "Rohr", "10.00", "20");
        Leistung pfosten = baugruppe(11, "Pfosten");
        pfosten.setZuschlagProzent(new BigDecimal("50"));
        enthaelt(pfosten, LeistungPosition.Typ.ARTIKEL, rohr, null, null, "1");

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, new BigDecimal("10"), List.of(bg(11, "1"))));

        assertEquals(new BigDecimal("13.20"), d.preisBerechnet(), "12,00 + 10 % der obersten Leistung, nicht + 50 %");
    }

    @Test
    void anzahlDerVorgaengeVervielfachtDieBearbeitungszeit() {
        arbeitsgang(5, "Bohren", "60.00");

        // 2 min je Bohrung, 4 Bohrungen je Einheit, 5 min Rüsten bei Losgröße 5
        var zeile = new Position("ARBEIT", null, 5L, null, null, new BigDecimal("5"), new BigDecimal("2"), null, new BigDecimal("4"));
        LeistungKalkulationDto d = service.speichern(7L, mitLos("5", zeile));

        assertEquals(new BigDecimal("9.00"), d.arbeitSumme(), "5/5 + 2 x 4 = 9 min = 0,15 Std. x 60");
        assertEquals(0, new BigDecimal("0.150").compareTo(d.positionen().get(0).menge()));
        assertEquals(0, new BigDecimal("4").compareTo(d.positionen().get(0).anzahlVorgaenge()));
    }

    @Test
    void ohneAnzahlDerVorgaengeRechnetEineZeileWieBisher() {
        arbeitsgang(5, "Bohren", "60.00");

        LeistungKalkulationDto d = service.speichern(7L, mitLos("1", arbZeiten(5, "0", "6")));

        assertEquals(new BigDecimal("6.00"), d.arbeitSumme());
        assertEquals(0, BigDecimal.ONE.compareTo(d.positionen().get(0).anzahlVorgaenge()));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L,
                mitLos("1", new Position("ARBEIT", null, 5L, null, null, null, new BigDecimal("2"), null, BigDecimal.ZERO)))).getReason().contains("Anzahl der Vorgänge"));
    }

    @Test
    void eineLeistungOhneBaugruppenKennzeichenUndEineUnbekannteWerdenAbgelehnt() {
        Leistung normal = new Leistung();
        normal.setId(20L);
        normal.setBezeichnung("Montage");
        when(leistungen.findById(20L)).thenReturn(Optional.of(normal));

        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("1", bg(20, "1")))).getReason().contains("keine Baugruppe"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("1", bg(99, "1")))).getReason().contains("existiert nicht"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L,
                mitLos("1", new Position("BAUGRUPPE", null, null, BigDecimal.ONE, null, null, null, null, null)))).getReason().contains("Baugruppe wählen"));
        verify(leistungen, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void eineBaugruppeKannSichNichtSelbstEnthaltenAuchNichtUeberUmwege() {
        leistung.setBaugruppe(true);
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("1", bg(7, "1")))).getReason().contains("nicht selbst enthalten"));

        // Feld enthält Pfosten, Pfosten enthält die Leistung 7: 7 → Feld wäre ein Kreis
        Leistung feld = baugruppe(12, "Feld");
        Leistung pfosten = baugruppe(11, "Pfosten");
        enthaelt(feld, LeistungPosition.Typ.BAUGRUPPE, null, null, pfosten, "2");
        enthaelt(pfosten, LeistungPosition.Typ.BAUGRUPPE, null, null, leistung, "1");

        var e = assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("1", bg(12, "1"))));
        assertTrue(e.getReason().contains("Kreis"), e.getReason());
        assertTrue(e.getReason().contains("Geländer montieren → Feld → Pfosten → Geländer montieren"), e.getReason());
        verify(leistungen, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mehrAlsAchtEbenenWerdenAbgelehntAchtSindErlaubt() {
        artikel(1, "Rohr", "10.00", "20");
        Leistung vorher = null;
        Leistung oberste = null;
        for (int ebene = 9; ebene >= 1; ebene--) {
            Leistung b = baugruppe(100 + ebene, "Ebene " + ebene);
            if (vorher != null) {
                enthaelt(b, LeistungPosition.Typ.BAUGRUPPE, null, null, vorher, "1");
            } else {
                enthaelt(b, LeistungPosition.Typ.ARTIKEL, artikel(1, "Rohr", "10.00", "20"), null, null, "1");
            }
            vorher = b;
            oberste = b;
        }
        // Ebene 1 enthält 2 ... 9: zusammen mit der Leistung selbst sind das 9 Ebenen darunter
        var e = assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("1", bg(101, "1"))));
        assertTrue(e.getReason().contains("zu tief"), e.getReason());
        assertTrue(e.getReason().contains("höchstens 8"), e.getReason());

        // Ohne die unterste Ebene sind es genau acht: erlaubt
        LeistungKalkulationDto d = service.speichern(7L, mitLos("1", bg(102, "1")));
        assertEquals(new BigDecimal("12.00"), d.materialSumme());
        assertEquals(101L, oberste.getId());
    }

    @Test
    void eineUnvollstaendigeBaugruppeBlockiertDenBerechnetenPreisMitKlartext() {
        Artikel ohnePreis = artikel(3, "Sonderteil", null, null);
        Leistung pfosten = baugruppe(11, "Pfosten");
        enthaelt(pfosten, LeistungPosition.Typ.ARTIKEL, ohnePreis, null, null, "1");
        baugruppe(13, "Leere Baugruppe");

        var e = assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("1", bg(11, "1"))));
        assertTrue(e.getReason().contains("Baugruppe \"Pfosten\" ist unvollständig"), e.getReason());
        var leer = assertThrows(ResponseStatusException.class, () -> service.speichern(7L, mitLos("1", bg(13, "1"))));
        assertTrue(leer.getReason().contains("Leere Baugruppe"), leer.getReason());

        // Als Vorschlag (ohne Haken) bleibt es möglich, mit dem Hinweis an der Zeile
        LeistungKalkulationDto d = service.vorschau(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(bg(11, "1"))));
        assertFalse(d.vollstaendig());
        assertEquals("BAUGRUPPE_UNVOLLSTAENDIG", d.positionen().get(0).hinweis());
        assertEquals(null, d.positionen().get(0).einzelpreis());
    }

    @Test
    void einBestehenderKreisLaesstDieBerechnungNichtEndlosLaufen() {
        Leistung a = baugruppe(11, "A");
        Leistung b = baugruppe(12, "B");
        enthaelt(a, LeistungPosition.Typ.BAUGRUPPE, null, null, b, "1");
        enthaelt(b, LeistungPosition.Typ.BAUGRUPPE, null, null, a, "1");   // Altlast, entstanden außerhalb dieser Prüfung

        LeistungKalkulationDto d = service.vorschau(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(bg(11, "1"))));

        assertFalse(d.vollstaendig(), "bricht nach der Tiefengrenze ab, statt zu hängen");
    }

    // ------------------------------------------------------------------ Verschnitt (#135)

    private static Position artMitVerschnitt(long id, String menge, String verschnitt) {
        return new Position("ARTIKEL", id, null, new BigDecimal(menge), null, null, null, null, null, verschnitt == null ? null : new BigDecimal(verschnitt));
    }

    @Test
    void verschnittVergroessertDieMengeImPreisUndWirdAngezeigt() {
        artikel(1, "Rohr", "10.00", "20");   // Verkauf 12,00 je Stück

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(artMitVerschnitt(1, "8", "8"))));

        // 8 + 8 % = 8,64 Stück
        assertEquals(new BigDecimal("103.68"), d.materialSumme());
        var zeile = d.positionen().get(0);
        assertEquals(0, new BigDecimal("8").compareTo(zeile.menge()), "die Menge bleibt die benötigte Menge");
        assertEquals(0, new BigDecimal("8.00").compareTo(zeile.verschnittProzent()));
        assertEquals(0, new BigDecimal("8.640").compareTo(zeile.mengeMitVerschnitt()));
        assertEquals(new BigDecimal("103.68"), zeile.summe());
    }

    @Test
    void ohneVerschnittOderMitNullRechnetEineZeileWieBisher() {
        artikel(1, "Rohr", "10.00", "20");

        LeistungKalkulationDto ohne = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(art(1, "2.5"))));
        LeistungKalkulationDto null0 = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(artMitVerschnitt(1, "2.5", "0"))));

        assertEquals(new BigDecimal("30.00"), ohne.materialSumme());
        assertEquals(new BigDecimal("30.00"), null0.materialSumme());
        assertEquals(null, ohne.positionen().get(0).verschnittProzent());
        assertEquals(null, null0.positionen().get(0).mengeMitVerschnitt());
    }

    @Test
    void unmoeglicherVerschnittWirdAbgelehntUndGiltNurFuerMaterial() {
        artikel(1, "Rohr", "10.00", "20");
        arbeitsgang(5, "Montage", "60.00");

        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L,
                new LeistungKalkulationSpeichernDto(true, null, List.of(artMitVerschnitt(1, "1", "100.5"))))).getReason().contains("Verschnitt"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L,
                new LeistungKalkulationSpeichernDto(true, null, List.of(artMitVerschnitt(1, "1", "-1"))))).getReason().contains("0 und 100"));

        // Bei Arbeit wird ein mitgeschickter Verschnitt ignoriert
        var arbeitMitVerschnitt = new Position("ARBEIT", null, 5L, new BigDecimal("1"), null, null, null, null, null, new BigDecimal("50"));
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(arbeitMitVerschnitt)));
        assertEquals(new BigDecimal("60.00"), d.arbeitSumme());
        assertEquals(null, d.positionen().get(0).verschnittProzent());
    }

    @Test
    void verschnittGiltAuchInEinerBaugruppeUndWirdMitDerMengeVervielfacht() {
        Artikel rohr = artikel(1, "Rohr", "10.00", "20");   // 12,00
        Leistung pfosten = baugruppe(11, "Pfosten");
        enthaelt(pfosten, LeistungPosition.Typ.ARTIKEL, rohr, null, null, "1");
        pfosten.getPositionen().get(0).setVerschnittProzent(new BigDecimal("25"));       // 15,00

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(bg(11, "2"))));

        assertEquals(new BigDecimal("30.00"), d.materialSumme());
    }

    // ------------------------------------------------------------------ Mengenstaffel (#135 Stufe 2)

    @Test
    void staffelVerteiltDieRuestzeitAufDieAngebotsmenge() {
        Arbeitsgang g = arbeitsgang(5, "Schweißen", "60.00");
        LeistungPosition p = new LeistungPosition();
        p.setLeistung(leistung);
        p.setTyp(LeistungPosition.Typ.ARBEIT);
        p.setArbeitsgang(g);
        p.setMenge(BigDecimal.ONE);
        p.setRuestzeitMinuten(new BigDecimal("60"));
        p.setBearbeitungszeitMinuten(new BigDecimal("6"));
        leistung.getPositionen().add(p);
        leistung.setLosgroesse(new BigDecimal("1000"));   // gilt in der Staffel nicht

        var z = service.staffel(7L, List.of(new BigDecimal("10"), new BigDecimal("50")));

        // 10 lfm: 60/10 + 6 = 12 min = 12,00 €; 50 lfm: 60/50 + 6 = 7,2 min = 7,20 €
        assertEquals(new BigDecimal("12.00"), z.get(0).preisJeEinheit());
        assertEquals(new BigDecimal("120.00"), z.get(0).gesamtpreis());
        assertEquals(new BigDecimal("7.20"), z.get(1).preisJeEinheit());
        assertEquals(new BigDecimal("360.00"), z.get(1).gesamtpreis());
        verify(leistungen, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void beiBaugruppenVerteiltSichDieRuestzeitAufDieMengeDerBaugruppe() {
        Arbeitsgang g = arbeitsgang(5, "Bohren", "60.00");
        Leistung pfosten = baugruppe(11, "Pfosten");
        LeistungPosition p = new LeistungPosition();
        p.setLeistung(pfosten);
        p.setTyp(LeistungPosition.Typ.ARBEIT);
        p.setArbeitsgang(g);
        p.setMenge(BigDecimal.ONE);
        p.setRuestzeitMinuten(new BigDecimal("30"));
        p.setBearbeitungszeitMinuten(new BigDecimal("0"));
        pfosten.getPositionen().add(p);
        enthaelt(leistung, LeistungPosition.Typ.BAUGRUPPE, null, null, pfosten, "0.5");   // 0,5 Pfosten je lfm

        var z = service.staffel(7L, List.of(new BigDecimal("10"), new BigDecimal("100")));

        // 10 lfm = 5 Pfosten: 30/5 = 6 min je Pfosten = 6,00 €, davon 0,5 je lfm = 3,00 €; 100 lfm = 50 Pfosten: 0,6 min = 0,60 €, je lfm 0,30 €
        assertEquals(new BigDecimal("3.00"), z.get(0).preisJeEinheit());
        assertEquals(new BigDecimal("0.30"), z.get(1).preisJeEinheit());
    }

    @Test
    void staffelPrueftDieMengen() {
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.staffel(7L, List.of())).getReason().contains("mindestens eine"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.staffel(7L, List.of(BigDecimal.ZERO))).getReason().contains("größer als 0"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.staffel(7L, java.util.Collections.nCopies(11, BigDecimal.ONE))).getReason().contains("höchstens 10"));
    }

    // ------------------------------------------------------------------ Maschinen (#63, Stufe 3 von #135)

    private Arbeitsgang mitMaschine(Arbeitsgang g, String satz) {
        g.setMaschinenStundensatz(satz == null ? null : new BigDecimal(satz));
        g.setMaschinenBezeichnung("Kappsäge");
        return g;
    }

    private static Position arbMaschine(long id, String stunden, String anteil) {
        return new Position("ARBEIT", null, id, new BigDecimal(stunden), null, null, null, null, null, null, anteil == null ? null : new BigDecimal(anteil));
    }

    @Test
    void eineMaschineAusDerMaschinenlisteGehtVorDemAltenWertAmArbeitsgangUndBeiGleichemSatzBleibtDerPreisGleich() {
        var maschine = new org.example.kalkulationsprogramm.domain.Maschine();
        maschine.setName("Kappsäge");
        maschine.setKostensatz(new BigDecimal("40.00"));
        // Alter Wert am Arbeitsgang weicht ab (30): Es gilt der Satz der Maschine aus der Liste
        Arbeitsgang g = mitMaschine(arbeitsgang(5, "Sägen", "60.00"), "30.00");
        g.setMaschine(maschine);

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, new BigDecimal("10"), List.of(arb(5, "0.5"))));

        // Genau wie mit 40 € am Arbeitsgang (siehe der Test davor): Maschine 20,00, Preis 55,00
        assertEquals(new BigDecimal("20.00"), d.maschineSumme());
        assertEquals(new BigDecimal("55.00"), d.preisBerechnet());
    }

    @Test
    void eineMaschineAmArbeitsgangKommtZumLohnDazuUndStehtGetrenntInDerSumme() {
        mitMaschine(arbeitsgang(5, "Sägen", "60.00"), "40.00");

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, new BigDecimal("10"), List.of(arb(5, "0.5"))));

        assertEquals(new BigDecimal("30.00"), d.arbeitSumme(), "Lohn: 0,5 Std. x 60");
        assertEquals(new BigDecimal("20.00"), d.maschineSumme(), "Maschine: 0,5 Std. x 40");
        assertEquals(new BigDecimal("50.00"), d.zwischensumme());
        assertEquals(new BigDecimal("5.00"), d.zuschlag(), "der Zuschlag gilt auf Material, Lohn und Maschine");
        assertEquals(new BigDecimal("55.00"), d.preisBerechnet());
        var z = d.positionen().get(0);
        assertEquals(new BigDecimal("50.00"), z.summe(), "die Zeile zeigt Lohn plus Maschine");
        assertEquals(new BigDecimal("20.00"), z.maschinenSumme());
        assertEquals(0, new BigDecimal("40").compareTo(z.maschinenSatz()));
        assertEquals(0, new BigDecimal("100").compareTo(z.maschinenanteilProzent()));
    }

    @Test
    void derMaschinenanteilVerkuerztOderVerlaengertDieMaschinenzeit_NullRechnetOhneMaschine() {
        mitMaschine(arbeitsgang(5, "Sägen", "60.00"), "40.00");

        var halb = service.vorschau(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(arbMaschine(5, "1", "50"))));
        var dreifach = service.vorschau(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(arbMaschine(5, "1", "300"))));
        var ohne = service.vorschau(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(arbMaschine(5, "1", "0"))));

        assertEquals(new BigDecimal("20.00"), halb.maschineSumme());
        assertEquals(new BigDecimal("120.00"), dreifach.maschineSumme(), "die Maschine läuft länger, als der Mitarbeiter dabei ist");
        assertEquals(new BigDecimal("60.00"), dreifach.arbeitSumme(), "der Lohn bleibt");
        assertEquals(new BigDecimal("0.00"), ohne.maschineSumme());
        assertEquals(null, ohne.positionen().get(0).maschinenSumme());
        assertEquals(0, new BigDecimal("0").compareTo(ohne.positionen().get(0).maschinenanteilProzent()), "der Satz bleibt sichtbar, damit sich der Anteil wieder ändern lässt");
    }

    @Test
    void beiZeitenRechnetDieMaschineMitDenselbenMinutenWieDerLohnInklusiveLosgroesseUndVorgaengen() {
        mitMaschine(arbeitsgang(5, "Bohren", "60.00"), "30.00");

        // Rüsten 30 min bei Losgröße 5 = 6 min, dazu 2 min x 4 Bohrungen = 8 min: 14 min = 14/60 Std.
        var zeile = new Position("ARBEIT", null, 5L, null, null, new BigDecimal("30"), new BigDecimal("2"), null, new BigDecimal("4"), null, null);
        LeistungKalkulationDto d = service.vorschau(7L, mitLos("5", zeile));

        assertEquals(new BigDecimal("14.00"), d.arbeitSumme());
        assertEquals(new BigDecimal("7.00"), d.maschineSumme(), "30 € je Std. x 14/60");
    }

    @Test
    void ohneMaschineAmArbeitsgangRechnetAllesWieBisher_MitAusdruecklichemAnteilIstSieNichtBerechenbar() {
        arbeitsgang(5, "Montage", "60.00");

        LeistungKalkulationDto wie = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(arb(5, "1"))));
        assertEquals(new BigDecimal("0.00"), wie.maschineSumme());
        assertEquals(new BigDecimal("60.00"), wie.preisBerechnet());
        assertEquals(null, wie.positionen().get(0).maschinenSatz());

        LeistungKalkulationDto vorschau = service.vorschau(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(arbMaschine(5, "1", "100"))));
        assertFalse(vorschau.vollstaendig());
        assertEquals("KEIN_MASCHINENSATZ", vorschau.positionen().get(0).hinweis());
        var e = assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(arbMaschine(5, "1", "100")))));
        assertTrue(e.getReason().contains("Maschinenstundensatz"), e.getReason());
    }

    @Test
    void maschinenanteilWirdGeprueft() {
        arbeitsgang(5, "Montage", "60.00");

        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(arbMaschine(5, "1", "-5")))))
                .getReason().contains("Maschinenanteil"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(arbMaschine(5, "1", "1001")))))
                .getReason().contains("0 und 1.000"));
    }

    @Test
    void dieMaschineEinerBaugruppeGehtMitIhrerMengeInDieLeistung() {
        Arbeitsgang saege = mitMaschine(arbeitsgang(5, "Sägen", "60.00"), "40.00");
        Leistung pfosten = baugruppe(11, "Pfosten");
        enthaelt(pfosten, LeistungPosition.Typ.ARBEIT, null, saege, null, "0.5");

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(bg(11, "2"))));

        assertEquals(new BigDecimal("60.00"), d.arbeitSumme());
        assertEquals(new BigDecimal("40.00"), d.maschineSumme());
        assertEquals(new BigDecimal("100.00"), d.preisBerechnet());
        assertEquals(new BigDecimal("50.00"), d.positionen().get(0).einzelpreis(), "eine Einheit der Baugruppe: Lohn 30 + Maschine 20");
    }

    @Test
    void dieStaffelRechnetDieMaschineMit() {
        Arbeitsgang g = mitMaschine(arbeitsgang(5, "Sägen", "60.00"), "40.00");
        LeistungPosition p = new LeistungPosition();
        p.setLeistung(leistung);
        p.setTyp(LeistungPosition.Typ.ARBEIT);
        p.setArbeitsgang(g);
        p.setMenge(BigDecimal.ONE);
        p.setRuestzeitMinuten(new BigDecimal("30"));
        p.setBearbeitungszeitMinuten(new BigDecimal("0"));
        leistung.getPositionen().add(p);

        var z = service.staffel(7L, List.of(new BigDecimal("5"), new BigDecimal("30")));

        // 5 Stück: 6 min = 0,1 Std.: Lohn 6,00 + Maschine 4,00; 30 Stück: 1 min: Lohn 1,00 + Maschine 0,67
        assertEquals(new BigDecimal("10.00"), z.get(0).preisJeEinheit());
        assertEquals(new BigDecimal("4.00"), z.get(0).maschineJeEinheit());
        assertEquals(new BigDecimal("1.67"), z.get(1).preisJeEinheit());
    }

    // ── Fremdleistung (Stufe 4 von #135) ──

    private org.example.kalkulationsprogramm.domain.Fremdleistung verzinkerei(long id, String preisJeKg, String mindest) {
        org.example.kalkulationsprogramm.domain.Lieferanten l = new org.example.kalkulationsprogramm.domain.Lieferanten();
        l.setId(90L + id);
        l.setLieferantenname("Verzinkerei Muster");
        org.example.kalkulationsprogramm.domain.Fremdleistung f = new org.example.kalkulationsprogramm.domain.Fremdleistung();
        f.setId(id);
        f.setLieferant(l);
        f.setBezeichnung("Feuerverzinken");
        f.setEinheit(org.example.kalkulationsprogramm.domain.Fremdleistung.Einheit.KG);
        f.setPreisJeEinheit(new BigDecimal(preisJeKg));
        f.setMindestpreis(mindest == null ? null : new BigDecimal(mindest));
        when(fremdleistungen.findById(id)).thenReturn(Optional.of(f));
        return f;
    }

    private Position fremd(long id, String kg) {
        return new Position("FREMDLEISTUNG", null, null, new BigDecimal(kg), null, null, null, null, null, null, null, id, null);
    }

    @Test
    void eineFremdleistungZaehltMitIhrerAufstellungInDenPreis() {
        verzinkerei(3, "1.20", null);

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(fremd(3, "12.5"))));

        // 12,5 kg je Einheit bei Losgröße 1 = 15,00
        assertEquals(new BigDecimal("15.00"), d.fremdleistungSumme());
        assertEquals(new BigDecimal("15.00"), d.preisBerechnet());
        var zeile = d.positionen().get(0);
        assertEquals("FREMDLEISTUNG", zeile.typ());
        assertEquals("Verzinkerei Muster", zeile.veredler());
        assertEquals("kg", zeile.einheit());
        assertEquals("EINHEITSPREIS", zeile.kostenarten().get(0).art());
    }

    @Test
    void derMindestpreisVerteiltSichAufDieLosgroesseUndDerZuschlagGiltAufAlles() {
        verzinkerei(3, "1.20", "60.00");

        // 10 Einheiten à 2 kg = 20 kg = 24 EUR, Mindestpreis 60 EUR: 6,00 je Einheit; Zuschlag 10 % = 6,60
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, new BigDecimal("10"), new BigDecimal("10"), List.of(fremd(3, "2"))));

        assertEquals(new BigDecimal("6.00"), d.fremdleistungSumme());
        assertEquals(new BigDecimal("6.60"), d.preisBerechnet());
    }

    @Test
    void dieStaffelRechnetDieFremdleistungAufDieAngebotsmengeUm() {
        verzinkerei(3, "1.20", "60.00");
        LeistungPosition p = new LeistungPosition();
        p.setLeistung(leistung);
        p.setTyp(LeistungPosition.Typ.FREMDLEISTUNG);
        p.setFremdleistung(fremdleistungen.findById(3L).orElseThrow());
        p.setMenge(new BigDecimal("2"));
        leistung.getPositionen().add(p);

        var z = service.staffel(7L, List.of(new BigDecimal("5"), new BigDecimal("50")));

        // 5 Einheiten = 10 kg = 12 EUR, Mindestpreis 60: 12,00 je Einheit; 50 Einheiten = 100 kg = 120 EUR: 2,40 je Einheit
        assertEquals(new BigDecimal("12.00"), z.get(0).fremdleistungJeEinheit());
        assertEquals(new BigDecimal("12.00"), z.get(0).preisJeEinheit());
        assertEquals(new BigDecimal("2.40"), z.get(1).fremdleistungJeEinheit());
        assertEquals(new BigDecimal("2.40"), z.get(1).preisJeEinheit());
    }

    @Test
    void eineFremdleistungInEinerBaugruppeRechnetMitDerMengeDerBaugruppe() {
        verzinkerei(3, "1.00", null);
        Leistung pfosten = baugruppe(11, "Pfosten");
        enthaelt(pfosten, LeistungPosition.Typ.FREMDLEISTUNG, null, null, null, "4");
        pfosten.getPositionen().get(0).setFremdleistung(fremdleistungen.findById(3L).orElseThrow());

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(bg(11, "2"))));

        // Ein Pfosten: 4 kg x 1,00 = 4,00; zwei Pfosten je Einheit der Leistung = 8,00
        assertEquals(new BigDecimal("8.00"), d.fremdleistungSumme());
        assertEquals(new BigDecimal("8.00"), d.preisBerechnet());
    }

    @Test
    void transportJeKgOhneGewichtMachtDieKalkulationUnvollstaendigUndDerPreisWirdNichtUebernommen() {
        org.example.kalkulationsprogramm.domain.Fremdleistung f = verzinkerei(3, "12", null);
        f.setEinheit(org.example.kalkulationsprogramm.domain.Fremdleistung.Einheit.QUADRATMETER);
        f.setTransportArt(org.example.kalkulationsprogramm.domain.Fremdleistung.TransportArt.JE_KG);
        f.setTransportBetrag(new BigDecimal("0.10"));

        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(fremd(3, "2")))))
                .getReason().contains("Gewicht"));

        LeistungKalkulationDto mit = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(new Position("FREMDLEISTUNG", null, null, new BigDecimal("2"), null, null, null, null, null, null, null, 3L, new BigDecimal("30")))));
        assertEquals(new BigDecimal("27.00"), mit.fremdleistungSumme(), "2 m² x 12 = 24 + 30 kg x 0,10 = 3");
    }

    @Test
    void fremdleistungZeilePruefungen() {
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(fremd(99, "1")))))
                .getReason().contains("existiert nicht"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null,
                List.of(new Position("FREMDLEISTUNG", null, null, BigDecimal.ONE, null, null, null, null, null, null, null, null, null)))))
                .getReason().contains("Fremdleistung wählen"));
    }

    // ── Gewicht und Oberfläche aus dem Artikel (Stufe 4 von #135) ──

    /** Ein Profil in lfm: Einkauf je Meter, Gewicht in kg je m und Mantelfläche in m² je m. */
    private org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe profil(long id, String name, String masse, String mantel) {
        org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe a = new org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe();
        a.setId(id);
        a.setKurzbeschreibung(name);
        a.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        a.setVerkaufsaufschlagProzent(BigDecimal.ZERO);
        a.setMasse(masse == null ? null : new BigDecimal(masse));
        a.setMantelflaeche(mantel == null ? null : new BigDecimal(mantel));
        LieferantenArtikelPreise p = new LieferantenArtikelPreise();
        p.setArtikel(a);
        p.setPreis(new BigDecimal("5.00"));
        p.setAktuell(true);
        a.getArtikelpreis().add(p);
        when(artikel.findById(id)).thenReturn(Optional.of(a));
        return a;
    }

    private Position fremdAus(long id, String herkunft) {
        return new Position("FREMDLEISTUNG", null, null, null, null, null, null, null, null, null, null, id, null, herkunft);
    }

    @Test
    void gewichtUndOberflaecheKommenAusDemArtikelOhneVerschnitt() {
        profil(1, "Rundrohr 40x2", "1.89", "0.1257");

        // 3 m Rohr mit 10 % Verschnitt: Preis rechnet mit 3,3 m, verzinkt wird aber nur, was gebaut wird (3 m)
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(artMitVerschnitt(1, "3", "10"))));

        assertEquals(new BigDecimal("5.670"), d.gewichtKg());
        assertEquals(new BigDecimal("0.377"), d.oberflaecheM2());
        assertTrue(d.gewichtVollstaendig());
        assertEquals(new BigDecimal("5.670"), d.positionen().get(0).gewichtKg());
    }

    @Test
    void dieFremdleistungKannIhreMengeAusDemGewichtNehmenMitBaugruppenUndLosgroesse() {
        profil(1, "Rundrohr 40x2", "2.00", "0.1");
        verzinkerei(3, "1.00", null);
        Leistung pfosten = baugruppe(11, "Pfosten");
        enthaelt(pfosten, LeistungPosition.Typ.ARTIKEL, artikel.findById(1L).orElseThrow(), null, null, "1.5");

        // Je Einheit: 2 Pfosten à 1,5 m = 3 m = 6 kg; Verzinken 1,00 je kg bei Losgröße 10 = 60 kg = 60 EUR = 6,00 je Einheit
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, new BigDecimal("10"), List.of(bg(11, "2"), fremdAus(3, "GEWICHT"))));

        assertEquals(new BigDecimal("6.000"), d.gewichtKg());
        assertEquals(new BigDecimal("6.00"), d.fremdleistungSumme());
        var zeile = d.positionen().get(1);
        assertEquals("GEWICHT", zeile.mengeAus());
        assertEquals(new BigDecimal("6.000"), zeile.menge());
        assertEquals("EINHEITSPREIS", zeile.kostenarten().get(0).art());
        assertTrue(zeile.kostenarten().get(0).text().startsWith("60 kg"));
    }

    @Test
    void auchOhneAngabeNimmtDerTransportJeKgDasBerechneteGewicht() {
        profil(1, "Rundrohr 40x2", "2.00", "0.1");
        org.example.kalkulationsprogramm.domain.Fremdleistung f = verzinkerei(3, "12", null);
        f.setEinheit(org.example.kalkulationsprogramm.domain.Fremdleistung.Einheit.QUADRATMETER);
        f.setTransportArt(org.example.kalkulationsprogramm.domain.Fremdleistung.TransportArt.JE_KG);
        f.setTransportBetrag(new BigDecimal("0.10"));

        // 5 m Rohr = 10 kg = 0,5 m²; Beschichten 0,5 m² x 12 = 6,00 plus Transport 10 kg x 0,10 = 1,00
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(art(1, "5"), fremdAus(3, "OBERFLAECHE"))));

        assertEquals(new BigDecimal("7.00"), d.fremdleistungSumme());
        assertTrue(d.vollstaendig());
    }

    @Test
    void unbekanntesGewichtMachtDieMengeAusGewichtUnvollstaendig() {
        // Ein Artikel in Stück ohne Profilmasse: Gewicht unbekannt
        artikel(2, "Schraubenset", "4.00", "10");
        verzinkerei(3, "1.00", null);

        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(true, null, List.of(art(2, "1"), fremdAus(3, "GEWICHT")))))
                .getReason().contains("Gewicht"));

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(2, "1"), fremdAus(3, "GEWICHT"))));
        assertFalse(d.gewichtVollstaendig());
        assertFalse(d.vollstaendig());
        assertEquals("KEIN_GEWICHT", d.positionen().get(1).hinweis());
    }

    @Test
    void dieHerkunftMussZurEinheitDerFremdleistungPassen() {
        verzinkerei(3, "1.00", null);
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(fremdAus(3, "OBERFLAECHE")))))
                .getReason().contains("nicht nach m²"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(fremdAus(3, "MASSE")))))
                .getReason().contains("unbekannt"));
        // "HAND" und leer rechnen mit der angegebenen Menge
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(
                new Position("FREMDLEISTUNG", null, null, new BigDecimal("2"), null, null, null, null, null, null, null, 3L, null, "HAND"))));
        assertEquals(new BigDecimal("2.00"), d.fremdleistungSumme());
        assertNull(d.positionen().get(0).mengeAus());
    }

    // ── Gewicht und Fläche eindeutig (#228): Verzinken immer nach Gewicht, Pulverbeschichten immer nach Fläche ──

    @Test
    void verzinkenBrauchtNurDasGewichtAuchWennDieFlaecheFehlt() {
        profil(1, "Flachstahl 40x8", "2.50", null);
        verzinkerei(3, "1.00", null);

        // 2 m × 2,5 kg/m = 5 kg × 1,00 je kg; die Fläche ist unbekannt, stört das Verzinken aber nicht
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "2"), fremdAus(3, "GEWICHT"))));

        assertEquals("OK", d.positionen().get(1).hinweis());
        assertEquals(new BigDecimal("5.00"), d.fremdleistungSumme());
        assertFalse(d.gewichtVollstaendig(), "beides zusammen ist nicht bekannt");
        assertEquals(new BigDecimal("5.000"), d.positionen().get(0).gewichtKg());
        assertNull(d.positionen().get(0).oberflaecheM2());
    }

    @Test
    void pulverbeschichtenBrauchtNurDieFlaecheAuchWennDasGewichtFehlt() {
        profil(1, "Rohr ohne Gewicht", null, "0.2");
        org.example.kalkulationsprogramm.domain.Fremdleistung f = verzinkerei(3, "12", null);
        f.setEinheit(org.example.kalkulationsprogramm.domain.Fremdleistung.Einheit.QUADRATMETER);

        // 5 m × 0,2 m²/m = 1 m² × 12,00
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "5"), fremdAus(3, "OBERFLAECHE"))));

        assertEquals("OK", d.positionen().get(1).hinweis());
        assertEquals(new BigDecimal("12.00"), d.fremdleistungSumme());
        assertEquals(new BigDecimal("1.000"), d.oberflaecheM2());
        assertNull(d.positionen().get(0).gewichtKg());
    }

    @Test
    void verzinkenOhneGewichtMeldetKeinGewichtAuchWennDieFlaecheBekanntIst() {
        profil(1, "Rohr ohne Gewicht", null, "0.2");
        verzinkerei(3, "1.00", null);

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "5"), fremdAus(3, "GEWICHT"))));

        assertEquals("KEIN_GEWICHT", d.positionen().get(1).hinweis());
        assertFalse(d.vollstaendig());
    }

    @Test
    void einBlechRechnetMitGewichtUndFlaecheJeQuadratmeter() {
        org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe blech = profil(1, "Blech 10 mm", null, null);
        blech.setVerrechnungseinheit(Verrechnungseinheit.QUADRATMETER);
        blech.setMassePerQm(new BigDecimal("78.5"));
        blech.setFlaecheJeQm(new BigDecimal("1.8"));

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "3"))));

        assertEquals(new BigDecimal("235.500"), d.gewichtKg());
        assertEquals(new BigDecimal("5.400"), d.oberflaecheM2(), "3 m² × 1,8 (Lochblech: weniger als beide Seiten)");
        assertTrue(d.gewichtVollstaendig());
    }

    @Test
    void einBlechOhneAngabeRechnetMitBeidenSeiten() {
        org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe blech = profil(1, "Blech 5 mm", null, null);
        blech.setVerrechnungseinheit(Verrechnungseinheit.QUADRATMETER);
        blech.setMassePerQm(new BigDecimal("39.25"));

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "2"))));

        assertEquals(new BigDecimal("78.500"), d.gewichtKg());
        assertEquals(new BigDecimal("4.000"), d.oberflaecheM2());
    }

    @Test
    void stueckwareRechnetMitGewichtUndFlaecheJeStueck() {
        org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe konsole = profil(1, "Konsole", null, null);
        konsole.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        konsole.setKgJeStueck(new BigDecimal("2.5"));
        konsole.setM2JeStueck(new BigDecimal("0.3"));

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "4"))));

        assertEquals(new BigDecimal("10.000"), d.gewichtKg());
        assertEquals(new BigDecimal("1.200"), d.oberflaecheM2());
        assertTrue(d.gewichtVollstaendig());
    }

    // ── Zuschnitt-Formel am Artikel (#228, Schritt 2): Maße an der Position, Gewicht, Fläche und Preis je Stück daraus ──

    private static final String ZUSCHNITT_GEWICHT = "(L/1000) * (B/1000) * T * dichte";
    private static final String ZUSCHNITT_FLAECHE = "(L/1000) * (B/1000) * 2";

    /** Ein Blechzuschnitt, eingekauft je kg (5,00 je kg), mit Formel für Gewicht und Fläche. */
    private org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe zuschnittBlech(long id) {
        org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe a = profil(id, "Blechzuschnitt", null, null);
        a.setVerrechnungseinheit(Verrechnungseinheit.KILOGRAMM);
        a.setFormelFelderJson("[{\"name\":\"L\",\"bezeichnung\":\"Länge\",\"einheit\":\"mm\",\"standard\":1000},{\"name\":\"B\",\"bezeichnung\":\"Breite\",\"einheit\":\"mm\",\"standard\":500},"
                + "{\"name\":\"T\",\"bezeichnung\":\"Dicke\",\"einheit\":\"mm\",\"standard\":10}]");
        a.setFormelGewicht(ZUSCHNITT_GEWICHT);
        a.setFormelFlaeche(ZUSCHNITT_FLAECHE);
        return a;
    }

    private static Position artMitMassen(long id, String menge, java.util.Map<String, BigDecimal> werte) {
        return new Position("ARTIKEL", id, null, new BigDecimal(menge), null, null, null, null, null, null, null, null, null, null, werte);
    }

    @Test
    void zuschnittMitFormelRechnetGewichtFlaecheUndPreisJeStueckAusDenMassen() {
        zuschnittBlech(1);

        // 1000 × 500 × 10 mm Stahl: 39,25 kg und 1 m² je Stück; 2 Stück; Einkauf 5,00 je kg = 196,25 je Stück
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(
                artMitMassen(1, "2", java.util.Map.of("l", new BigDecimal("1000"), "b", new BigDecimal("500"), "t", new BigDecimal("10"))))));

        var zeile = d.positionen().get(0);
        assertEquals("Stk", zeile.einheit());
        assertEquals(new BigDecimal("196.25"), zeile.einzelpreis());
        assertEquals(new BigDecimal("392.50"), zeile.summe());
        assertEquals(new BigDecimal("78.500"), d.gewichtKg());
        assertEquals(new BigDecimal("2.000"), d.oberflaecheM2());
        assertTrue(d.gewichtVollstaendig());
        assertEquals(3, zeile.formelFelder().size(), "die Oberfläche bekommt die Felder zum Abfragen");
        assertEquals(new BigDecimal("1000"), zeile.variablen().get("l"));
    }

    @Test
    void verzinkenUndPulvernRechnenMitDemZuschnitt() {
        zuschnittBlech(1);
        verzinkerei(3, "1.00", null);
        java.util.Map<String, BigDecimal> werte = java.util.Map.of("l", new BigDecimal("2000"), "b", new BigDecimal("1000"), "t", new BigDecimal("5"));

        // 2,0 × 1,0 m × 5 mm × 7,85 = 78,5 kg, 1 Stück, Verzinken 1,00 je kg
        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(artMitMassen(1, "1", werte), fremdAus(3, "GEWICHT"))));

        assertEquals("OK", d.positionen().get(1).hinweis());
        assertEquals(new BigDecimal("78.50"), d.fremdleistungSumme());
    }

    @Test
    void ohneAngabeGeltenDieStandardwerteDerFelder() {
        zuschnittBlech(1);

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(artMitMassen(1, "1", java.util.Map.of()))));

        assertEquals(new BigDecimal("39.250"), d.gewichtKg());
    }

    @Test
    void einMassOhneWertUndOhneStandardWirdBeimSpeichernInKlartextAbgelehnt() {
        org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe a = zuschnittBlech(1);
        a.setFormelFelderJson("[{\"name\":\"L\",\"bezeichnung\":\"Länge\",\"einheit\":\"mm\",\"standard\":null},{\"name\":\"B\",\"bezeichnung\":\"Breite\",\"einheit\":\"mm\",\"standard\":500},"
                + "{\"name\":\"T\",\"bezeichnung\":\"Dicke\",\"einheit\":\"mm\",\"standard\":10}]");

        String grund = assertThrows(ResponseStatusException.class, () -> service.speichern(7L,
                new LeistungKalkulationSpeichernDto(false, null, List.of(artMitMassen(1, "1", java.util.Map.of("b", new BigDecimal("500"))))))).getReason();

        assertTrue(grund.contains("Länge") && grund.contains("Blechzuschnitt"), grund);
    }

    @Test
    void einUnsinnigesMassWirdAbgelehnt() {
        zuschnittBlech(1);
        assertThrows(ResponseStatusException.class, () -> service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null,
                List.of(artMitMassen(1, "1", java.util.Map.of("l", new BigDecimal("-5")))))));
    }

    @Test
    void ohneFormelBleibtAllesWieBisher() {
        profil(1, "Rundrohr 40x2", "1.89", "0.1257");

        LeistungKalkulationDto d = service.speichern(7L, new LeistungKalkulationSpeichernDto(false, null, List.of(art(1, "3"))));

        assertEquals("lfm", d.positionen().get(0).einheit());
        assertNull(d.positionen().get(0).formelFelder());
        assertNull(d.positionen().get(0).variablen());
    }

    // ── Aufstellung für die Angebotsmenge (intern im Angebot, #55/#58) ──

    @Test
    void dieAufstellungFuerEineAngebotsmengeVerteiltDieRuestzeitAufDieMengeUndAendertNichts() {
        Arbeitsgang g = arbeitsgang(5, "Sägen", "60.00");
        LeistungPosition p = new LeistungPosition();
        p.setLeistung(leistung);
        p.setTyp(LeistungPosition.Typ.ARBEIT);
        p.setArbeitsgang(g);
        p.setMenge(BigDecimal.ONE);
        p.setRuestzeitMinuten(new BigDecimal("30"));
        p.setBearbeitungszeitMinuten(new BigDecimal("0"));
        leistung.getPositionen().add(p);
        leistung.setLosgroesse(new BigDecimal("1"));
        leistung.setPreis(new BigDecimal("30.00"));

        LeistungKalkulationDto fuenf = service.fuerAngebotsmenge(7L, new BigDecimal("5"));
        LeistungKalkulationDto dreissig = service.fuerAngebotsmenge(7L, new BigDecimal("30"));

        // 5 Stück: 6 min = 6,00 je Stück; 30 Stück: 1 min = 1,00 je Stück; die gespeicherte Losgröße (1) gilt nicht
        assertEquals(new BigDecimal("6.00"), fuenf.preisBerechnet());
        assertEquals(new BigDecimal("1.00"), dreissig.preisBerechnet());
        assertEquals(new BigDecimal("5.000"), fuenf.losgroesse().setScale(3));
        assertEquals(new BigDecimal("30.00"), fuenf.preisGespeichert());
        assertTrue(fuenf.preisVeraltet());
        assertEquals(new BigDecimal("1"), leistung.getLosgroesse(), "nichts wird gespeichert");
        verify(leistungen, never()).save(org.mockito.ArgumentMatchers.any(Leistung.class));
    }

    @Test
    void dieAufstellungFuerEineAngebotsmengeBrauchtEineGueltigeMenge() {
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.fuerAngebotsmenge(7L, BigDecimal.ZERO)).getReason().contains("größer als 0"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.fuerAngebotsmenge(7L, null)).getReason().contains("größer als 0"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.fuerAngebotsmenge(99L, BigDecimal.ONE)).getReason().contains("nicht gefunden"));
    }
}
