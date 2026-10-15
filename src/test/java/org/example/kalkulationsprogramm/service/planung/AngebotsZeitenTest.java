package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationDto;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungKalkulationDto.Position;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService.LeistungMengen;
import org.example.kalkulationsprogramm.service.LeistungKalkulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Die Arbeitszeit eines Auftrags nach Abteilung aus den Leistungen im Angebot (#184). Nur Dummy-Daten. */
class AngebotsZeitenTest {

    private final AusgangsGeschaeftsDokumentService dokumente = mock(AusgangsGeschaeftsDokumentService.class);
    private final LeistungKalkulationService kalkulation = mock(LeistungKalkulationService.class);
    private final ArbeitsgangRepository arbeitsgaenge = mock(ArbeitsgangRepository.class);
    private final AngebotsZeiten zeiten = new AngebotsZeiten(dokumente, kalkulation, arbeitsgaenge);

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static Position arbeit(long arbeitsgangId, String stundenJeEinheit) {
        return new Position(1L, "ARBEIT", null, arbeitsgangId, "Arbeit", d(stundenJeEinheit), "h", d("60"), d("0"), "OK", null, null, null, null, null, null, null);
    }

    private static Position baugruppe(long leistungId, String menge) {
        return new Position(2L, "BAUGRUPPE", null, null, "Baugruppe", d(menge), "Stk", d("0"), d("0"), "OK", null, null, null, leistungId, null, null, null);
    }

    private static LeistungKalkulationDto dto(Position... positionen) {
        return new LeistungKalkulationDto(1L, true, BigDecimal.ZERO, List.of(positionen), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, false, true);
    }

    private static Arbeitsgang gang(long id, long abteilungId) {
        Abteilung a = new Abteilung();
        a.setId(abteilungId);
        a.setName("Abteilung " + abteilungId);
        Arbeitsgang g = new Arbeitsgang();
        g.setId(id);
        g.setAbteilung(a);
        return g;
    }

    @BeforeEach
    void setUp() {
        when(arbeitsgaenge.findAllById(any())).thenAnswer(i -> {
            var ids = new java.util.HashSet<Long>();
            ((Iterable<Long>) i.getArgument(0)).forEach(ids::add);
            return List.of(gang(10, 1), gang(11, 1), gang(20, 2)).stream().filter(g -> ids.contains(g.getId())).toList();
        });
    }

    @Test
    void dieStundenJeEinheitWerdenMitDerMengeMultipliziertUndNachAbteilungSummiert() {
        when(dokumente.leistungMengenFuerProjekt(5L)).thenReturn(new LeistungMengen("ANGEBOT", Map.of(100L, d("4"))));
        // Leistung 100, Menge 4: Schweißen 1,5 h je Einheit (Abteilung 1), Zuschnitt 0,5 h (Abteilung 1), Montage 2 h (Abteilung 2)
        when(kalkulation.fuerAngebotsmenge(eq(100L), any())).thenReturn(dto(arbeit(10, "1.5"), arbeit(11, "0.5"), arbeit(20, "2")));

        var z = zeiten.fuerProjekt(5L);

        assertThat(z.quelle()).isEqualTo("ANGEBOT");
        assertThat(z.stundenJeAbteilung().get(1L)).isEqualByComparingTo("8.00");
        assertThat(z.stundenJeAbteilung().get(2L)).isEqualByComparingTo("8.00");
        assertThat(z.hinweise()).isEmpty();
    }

    @Test
    void eineBaugruppeWirdAufgeloestUndMitIhrerMengeMultipliziert() {
        when(dokumente.leistungMengenFuerProjekt(5L)).thenReturn(new LeistungMengen("AUFTRAGSBESTAETIGUNG", Map.of(100L, d("2"))));
        // Leistung 100 (Menge 2) enthält 3 Stück der Baugruppe 200; die Baugruppe hat 1 h je Einheit in Abteilung 1 -> 2 * 3 * 1 = 6 h
        when(kalkulation.fuerAngebotsmenge(eq(100L), any())).thenReturn(dto(baugruppe(200, "3")));
        when(kalkulation.fuerAngebotsmenge(eq(200L), any())).thenReturn(dto(arbeit(10, "1")));

        var z = zeiten.fuerProjekt(5L);

        assertThat(z.stundenJeAbteilung().get(1L)).isEqualByComparingTo("6.00");
    }

    @Test
    void eineBaugruppeImKreisWirdMitHinweisUebersprungen() {
        when(dokumente.leistungMengenFuerProjekt(5L)).thenReturn(new LeistungMengen("ANGEBOT", Map.of(100L, d("1"))));
        when(kalkulation.fuerAngebotsmenge(eq(100L), any())).thenReturn(dto(baugruppe(100, "1")));

        var z = zeiten.fuerProjekt(5L);

        assertThat(z.stundenJeAbteilung()).isEmpty();
        assertThat(z.hinweise()).anyMatch(h -> h.contains("verschachtelt"));
    }

    @Test
    void eineLeistungDieSichNichtBerechnenLaesstStehtAlsHinweisDaUndStoertDieAnderenNicht() {
        when(dokumente.leistungMengenFuerProjekt(5L)).thenReturn(new LeistungMengen("ANGEBOT", new java.util.LinkedHashMap<>(Map.of(100L, d("1")))));
        when(kalkulation.fuerAngebotsmenge(eq(100L), any())).thenThrow(new IllegalStateException("Es fehlt ein Stundensatz."));

        var z = zeiten.fuerProjekt(5L);

        assertThat(z.stundenJeAbteilung()).isEmpty();
        assertThat(z.hinweise()).singleElement().asString().contains("Nr. 100").contains("Es fehlt ein Stundensatz.");
    }

    @Test
    void ohneAngebotKommtNichtsUndKeineQuelle() {
        when(dokumente.leistungMengenFuerProjekt(5L)).thenReturn(new LeistungMengen(null, Map.of()));

        var z = zeiten.fuerProjekt(5L);

        assertThat(z.quelle()).isNull();
        assertThat(z.stundenJeAbteilung()).isEmpty();
    }
}
