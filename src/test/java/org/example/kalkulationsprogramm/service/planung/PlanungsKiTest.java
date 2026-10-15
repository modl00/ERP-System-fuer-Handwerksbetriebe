package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

import org.example.kalkulationsprogramm.domain.planung.PlanVorgangstyp;
import org.example.kalkulationsprogramm.repository.planung.PlanVorgangstypRepository;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService;
import org.example.kalkulationsprogramm.service.AusgangsGeschaeftsDokumentService.PositionsTexte;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTextWeiche;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AbhaengigkeitDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.VorgangDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** KI-Hilfe der Plantafel: Die KI wird nur gefragt und ihre Antwort geprüft; die Antworten sind erfunden. Nur Dummy-Daten. */
class PlanungsKiTest {

    private final KiTextWeiche weiche = mock(KiTextWeiche.class);
    private final PlanungsService planung = mock(PlanungsService.class);
    private final PlanVorgangstypRepository typen = mock(PlanVorgangstypRepository.class);
    private final AusgangsGeschaeftsDokumentService dokumente = mock(AusgangsGeschaeftsDokumentService.class);
    private final PlanungsKi ki = new PlanungsKi(weiche, planung, typen, dokumente);

    @BeforeEach
    void setUp() {
        when(typen.findAllByOrderBySortierungAscNameAsc()).thenReturn(List.of(art(1L, "Planung", 2), art(2L, "Fertigung", 5), art(3L, "Veredelung", 4)));
        when(planung.auftrag(7L)).thenReturn(new AuftragDto(7L, "Geländer Musterstraße", "A-1", null, null, null, "OHNE_ZIEL", List.of()));
        when(dokumente.positionsTexteFuerProjekt(7L)).thenReturn(new PositionsTexte("ANGEBOT", List.of("12 m Geländer", "2 Stk Handlauf")));
    }

    private static PlanVorgangstyp art(Long id, String name, int tage) {
        PlanVorgangstyp a = new PlanVorgangstyp();
        a.setId(id);
        a.setName(name);
        a.setStandardDauerTage(tage);
        return a;
    }

    private void kiAntwortet(String text) throws Exception {
        when(weiche.frage(eq(KiFunktion.PLANUNG), any(), any(), anyDouble(), anyInt(), anyBoolean())).thenReturn(text);
    }

    @Test
    void dieKiWaehltNurErlaubteArtenUndDieDauerWirdBegrenzt() throws Exception {
        kiAntwortet("{\"vorgaenge\":[{\"art\":\"planung\",\"dauerTage\":1,\"begruendung\":\"Zeichnung prüfen\"},"
                + "{\"art\":\"Fertigung\",\"dauerTage\":900,\"begruendung\":\"viel\"},{\"art\":\"Fertigung\",\"dauerTage\":2},{\"art\":\"Lackieren\",\"dauerTage\":3}]}");

        var v = ki.kettenVorschlag(7L);

        assertThat(v.quelle()).isEqualTo("ANGEBOT");
        assertThat(v.schritte()).extracting(s -> s.typId(), s -> s.dauerTage()).containsExactly(org.assertj.core.groups.Tuple.tuple(1L, 1), org.assertj.core.groups.Tuple.tuple(2L, 60));
        assertThat(v.schritte().get(0).begruendung()).isEqualTo("Zeichnung prüfen");
        assertThat(v.hinweise()).hasSize(2);
        assertThat(v.hinweise().get(0)).contains("mehrfach");
        assertThat(v.hinweise().get(1)).contains("Lackieren").contains("gibt es nicht");
    }

    @Test
    void dieKiBekommtDiePositionenUndNurDieAktivenArten() throws Exception {
        kiAntwortet("{\"vorgaenge\":[{\"art\":\"Planung\",\"dauerTage\":1}]}");

        ki.kettenVorschlag(7L);

        ArgumentCaptor<String> nutzer = ArgumentCaptor.forClass(String.class);
        verify(weiche).frage(eq(KiFunktion.PLANUNG), any(), nutzer.capture(), anyDouble(), anyInt(), eq(true));
        assertThat(nutzer.getValue()).contains("Geländer Musterstraße").contains("12 m Geländer").contains("Fertigung (5)");
    }

    @Test
    void ohnePositionenWirdDieKiGarNichtErstGefragt() throws Exception {
        when(dokumente.positionsTexteFuerProjekt(7L)).thenReturn(new PositionsTexte(null, List.of()));

        assertThatThrownBy(() -> ki.kettenVorschlag(7L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("kein Angebot");
        verify(weiche, org.mockito.Mockito.never()).frage(any(), any(), any(), anyDouble(), anyInt(), anyBoolean());
    }

    @Test
    void eineUnlesbareAntwortUndEinLeererVorschlagWerdenKlarGemeldet() throws Exception {
        kiAntwortet("Das kann ich leider nicht.");
        assertThatThrownBy(() -> ki.kettenVorschlag(7L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("nicht lesbar");

        kiAntwortet("{\"vorgaenge\":[{\"art\":\"Lackieren\",\"dauerTage\":3}]}");
        assertThatThrownBy(() -> ki.kettenVorschlag(7L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("keinen brauchbaren Vorschlag");
    }

    @Test
    void ProblemeDerKiKommenAlsKlartext() throws Exception {
        when(weiche.frage(any(), any(), any(), anyDouble(), anyInt(), anyBoolean())).thenThrow(new IOException("Für diese Funktion ist kein Schlüssel hinterlegt."));

        assertThatThrownBy(() -> ki.kettenVorschlag(7L)).isInstanceOf(IllegalArgumentException.class).hasMessage("Für diese Funktion ist kein Schlüssel hinterlegt.");
    }

    @Test
    void dieErklaerungBekommtDieVomProgrammGerechnetenTermine() throws Exception {
        LocalDate d = LocalDate.of(2026, 11, 2);
        var v = new VorgangDto(1L, 2L, "Fertigung", "#be123c", "Geländer fertigen", 5, "GESCHAETZT", d, d, LocalDate.of(2026, 11, 6), true, "GEPLANT", null,
                List.of(new AbhaengigkeitDto(9L, 0)), null, "NORMAL", List.of());
        when(planung.auftrag(7L)).thenReturn(new AuftragDto(7L, "Geländer", "A-1", null, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 6), "VERSPAETET", List.of(v)));
        kiAntwortet("  Der Termin wird durch die Fertigung bestimmt.  ");

        var e = ki.erklaeren(7L);

        assertThat(e.text()).isEqualTo("Der Termin wird durch die Fertigung bestimmt.");
        ArgumentCaptor<String> nutzer = ArgumentCaptor.forClass(String.class);
        verify(weiche).frage(eq(KiFunktion.PLANUNG), any(), nutzer.capture(), anyDouble(), anyInt(), eq(false));
        assertThat(nutzer.getValue()).contains("2026-11-06").contains("verspätet").contains("KONFLIKT").contains("fester Start 2026-11-02");
    }

    @Test
    void ohneVorgaengeGibtEsNichtsZuErklaeren() {
        assertThatThrownBy(() -> ki.erklaeren(7L)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("noch keine Vorgänge");
    }
}
