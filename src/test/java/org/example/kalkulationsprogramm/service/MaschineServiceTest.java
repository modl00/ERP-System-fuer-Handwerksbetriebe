package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.dto.Maschine.MaschineDto;
import org.example.kalkulationsprogramm.dto.Maschine.MaschineEingabeDto;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

/** Maschinenliste gegen eine echte (H2-)Datenbank. Nur Dummy-Daten (DSGVO). */
@DataJpaTest
@Import(MaschineService.class)
class MaschineServiceTest {

    @Autowired MaschineService service;
    @Autowired ArbeitsgangRepository arbeitsgaenge;
    @Autowired AbteilungRepository abteilungen;

    Abteilung werkstatt;

    @BeforeEach
    void setUp() {
        Abteilung a = new Abteilung();
        a.setName("Werkstatt");
        werkstatt = abteilungen.saveAndFlush(a);
    }

    private Arbeitsgang arbeitsgang(String name, String alterSatz) {
        Arbeitsgang g = new Arbeitsgang();
        g.setBeschreibung(name);
        g.setAbteilung(werkstatt);
        if (alterSatz != null) {
            g.setMaschinenStundensatz(new BigDecimal(alterSatz));
            g.setMaschinenBezeichnung("Alte Maschine " + name);
        }
        return arbeitsgaenge.saveAndFlush(g);
    }

    private static BigDecimal d(String s) {
        return s == null ? null : new BigDecimal(s);
    }

    private static MaschineEingabeDto schweissanlage(String name, List<Long> ids) {
        return new MaschineEingabeDto(name, d("60000"), d("6000"), d("9"), d("5"), d("1500"), d("750"), d("300"), d("20"), d("0.30"), d("50"), d("1500"), null, true, null, ids);
    }

    private static MaschineEingabeDto vonHand(String name, String satz, List<Long> ids) {
        return new MaschineEingabeDto(name, null, null, null, null, null, null, null, null, null, null, null, d(satz), true, null, ids);
    }

    @Test
    void anlegenRechnetDenSatzUndHaeltIhnImVerlaufFest() {
        MaschineDto m = service.anlegen(schweissanlage("Schweißanlage", null), "Max Mustermann");

        assertThat(m.kostensatz()).isEqualByComparingTo("9.80");
        assertThat(m.vonHand()).isFalse();
        assertThat(m.aufstellung().abschreibung()).isEqualByComparingTo("4.00");
        var v = service.verlauf(m.id());
        assertThat(v).hasSize(1);
        assertThat(v.get(0).satz()).isEqualByComparingTo("9.80");
        assertThat(v.get(0).geaendertVon()).isEqualTo("Max Mustermann");
        assertThat(v.get(0).vonHand()).isFalse();
    }

    @Test
    void einHandsatzGehtVorDemErrechnetenUndEineAenderungSchreibtEinenNeuenVerlaufseintrag() {
        MaschineDto m = service.anlegen(schweissanlage("Schweißanlage", null), "Max Mustermann");

        var mitHand = new MaschineEingabeDto("Schweißanlage", d("60000"), d("6000"), d("9"), d("5"), d("1500"), d("750"), d("300"), d("20"), d("0.30"), d("50"), d("1500"), d("12.50"), true, null, null);
        MaschineDto geaendert = service.aendern(m.id(), mitHand, "Erika Mustermann");

        assertThat(geaendert.kostensatz()).isEqualByComparingTo("12.50");
        assertThat(geaendert.vonHand()).isTrue();
        assertThat(geaendert.aufstellung().summe()).as("der errechnete Satz bleibt sichtbar").isEqualByComparingTo("9.80");
        var v = service.verlauf(m.id());
        assertThat(v).hasSize(2);
        assertThat(v.get(0).satz()).isEqualByComparingTo("12.50");
        assertThat(v.get(0).geaendertVon()).isEqualTo("Erika Mustermann");

        // Eine Änderung ohne neuen Satz (nur Bemerkung) schreibt keinen weiteren Eintrag
        service.aendern(m.id(), new MaschineEingabeDto("Schweißanlage", d("60000"), d("6000"), d("9"), d("5"), d("1500"), d("750"), d("300"), d("20"), d("0.30"), d("50"), d("1500"), d("12.50"), true, "Halle 2", null), "Erika Mustermann");
        assertThat(service.verlauf(m.id())).hasSize(2);
    }

    @Test
    void ohneBerechenbarenSatzOderMitUnmoeglichemSatzWirdNichtGespeichert() {
        var ohneStunden = new MaschineEingabeDto("Presse", d("10000"), null, d("5"), null, null, null, null, null, null, null, null, null, true, null, null);
        assertThatThrownBy(() -> service.anlegen(ohneStunden, "x")).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getReason()).contains("Laufstunden").contains("von Hand"));
        assertThatThrownBy(() -> service.anlegen(vonHand("Null", "0", null), "x")).hasMessageContaining("größer als 0");
        var teuer = new MaschineEingabeDto("Teuer", d("100000"), null, d("1"), null, null, null, null, null, null, null, d("50"), null, true, null, null);
        assertThatThrownBy(() -> service.anlegen(teuer, "x")).hasMessageContaining("höchstens 1.000");
        assertThat(service.alle()).isEmpty();
    }

    @Test
    void derNameIstEindeutigAuchBeiAndererSchreibweise() {
        service.anlegen(vonHand("Kappsäge", "40", null), "x");
        assertThatThrownBy(() -> service.anlegen(vonHand("kappsäge", "30", null), "x")).hasMessageContaining("gibt es schon");
    }

    @Test
    void arbeitsgaengeLassenSichZuordnenUmhaengenUndEntfernen() {
        Arbeitsgang saegen = arbeitsgang("Sägen", null);
        Arbeitsgang bohren = arbeitsgang("Bohren", null);
        MaschineDto a = service.anlegen(vonHand("Kappsäge", "40", List.of(saegen.getId(), bohren.getId())), "x");
        assertThat(a.arbeitsgaenge()).extracting(MaschineDto.ArbeitsgangKurz::beschreibung).containsExactly("Bohren", "Sägen");
        assertThat(arbeitsgaenge.findById(saegen.getId()).orElseThrow().effektiverMaschinenSatz()).isEqualByComparingTo("40.00");

        // Eine zweite Maschine übernimmt „Sägen“; die erste behält nur „Bohren“
        MaschineDto b = service.anlegen(vonHand("Bandsäge", "55", List.of(saegen.getId())), "x");
        assertThat(b.arbeitsgaenge()).extracting(MaschineDto.ArbeitsgangKurz::beschreibung).containsExactly("Sägen");
        assertThat(service.alle().stream().filter(m -> m.name().equals("Kappsäge")).findFirst().orElseThrow().arbeitsgaenge())
                .extracting(MaschineDto.ArbeitsgangKurz::beschreibung).containsExactly("Bohren");
        assertThat(arbeitsgaenge.findById(saegen.getId()).orElseThrow().effektiverMaschinenSatz()).isEqualByComparingTo("55.00");

        // Ohne Angabe bleiben die Zuordnungen, mit leerer Liste entfallen sie
        service.aendern(a.id(), vonHand("Kappsäge", "41", null), "x");
        assertThat(arbeitsgaenge.findById(bohren.getId()).orElseThrow().effektiverMaschinenSatz()).isEqualByComparingTo("41.00");
        service.aendern(a.id(), vonHand("Kappsäge", "41", List.of()), "x");
        Arbeitsgang ohne = arbeitsgaenge.findById(bohren.getId()).orElseThrow();
        assertThat(ohne.getMaschine()).isNull();
        assertThat(ohne.effektiverMaschinenSatz()).as("auch der alte Wert ist weg: der Arbeitsgang nutzt keine Maschine mehr").isNull();
    }

    @Test
    void eineGenutzteMaschineLaesstSichNichtLoeschenEineFreieSchon() {
        Arbeitsgang saegen = arbeitsgang("Sägen", null);
        MaschineDto genutzt = service.anlegen(vonHand("Kappsäge", "40", List.of(saegen.getId())), "x");
        MaschineDto frei = service.anlegen(vonHand("Bohrer", "20", null), "x");

        assertThatThrownBy(() -> service.loeschen(genutzt.id())).hasMessageContaining("wird von 1 Arbeitsgang genutzt");
        service.loeschen(frei.id());
        assertThat(service.alle()).extracting(MaschineDto::name).containsExactly("Kappsäge");
        assertThatThrownBy(() -> service.loeschen(frei.id())).hasMessageContaining("nicht gefunden");
    }

    @Test
    void dieUebernahmeDesAltenStandsMachtAusJedemMaschinensatzEineMaschineMitGleichemSatz() {
        Arbeitsgang saegen = arbeitsgang("Sägen", "40.00");
        Arbeitsgang bohren = arbeitsgang("Bohren", "22.50");
        Arbeitsgang ohne = arbeitsgang("Planen", null);
        arbeitsgang("Schweißen", "0");

        assertThat(service.uebernehmeAltbestand()).isEqualTo(2);

        Arbeitsgang s = arbeitsgaenge.findById(saegen.getId()).orElseThrow();
        assertThat(s.getMaschine().getName()).isEqualTo("Alte Maschine Sägen");
        assertThat(s.effektiverMaschinenSatz()).as("derselbe Satz wie vorher: die Preise bleiben gleich").isEqualByComparingTo("40.00");
        assertThat(s.getMaschine().getSatzVonHand()).isEqualByComparingTo("40.00");
        assertThat(arbeitsgaenge.findById(bohren.getId()).orElseThrow().effektiverMaschinenSatz()).isEqualByComparingTo("22.50");
        assertThat(arbeitsgaenge.findById(ohne.getId()).orElseThrow().getMaschine()).isNull();

        // Mehrfach ausführbar: nichts wird doppelt angelegt
        assertThat(service.uebernehmeAltbestand()).isZero();
        assertThat(service.alle()).hasSize(2);
    }

    @Test
    void gleicheNamenBeiDerUebernahmeBekommenEineNummer() {
        for (String n : List.of("A", "B")) {
            Arbeitsgang g = arbeitsgang(n, "10.00");
            g.setMaschinenBezeichnung("Kappsäge");
            arbeitsgaenge.saveAndFlush(g);
        }
        service.uebernehmeAltbestand();
        assertThat(service.alle()).extracting(MaschineDto::name).containsExactlyInAnyOrder("Kappsäge", "Kappsäge (2)");
    }
}
