package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** Woher der Kalkulationssatz am Arbeitsgang kommt: nur gültige Werte, ändert selbst keinen Satz. Nur Dummy-Daten. */
class ArbeitsgangSatzQuelleTest {

    private final ArbeitsgangRepository repo = mock(ArbeitsgangRepository.class);
    private final ArbeitsgangManagementService service = org.mockito.Mockito.mock(ArbeitsgangManagementService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    private Arbeitsgang gang;

    @BeforeEach
    void setUp() throws Exception {
        gang = new Arbeitsgang();
        gang.setId(1L);
        var feld = ArbeitsgangManagementService.class.getDeclaredField("arbeitsgangRepository");
        feld.setAccessible(true);
        feld.set(service, repo);
        when(repo.findById(1L)).thenReturn(Optional.of(gang));
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void esFolgtDemVerrechnungssatzEinerQualifikation() {
        var g = service.aktualisiereSatzQuelle(1L, "verrechnung", "facharbeiter");
        assertThat(g.folgtVerrechnungssatz()).isTrue();
        assertThat(g.getSatzGruppe()).isEqualTo("FACHARBEITER");
    }

    @Test
    void ohneQualifikationGiltDerSatzDesGanzenBetriebs() {
        var g = service.aktualisiereSatzQuelle(1L, "VERRECHNUNG", " ");
        assertThat(g.folgtVerrechnungssatz()).isTrue();
        assertThat(g.getSatzGruppe()).isNull();
    }

    @Test
    void vonHandLoeschtDieQualifikation() {
        gang.setSatzQuelle(Arbeitsgang.SATZ_VERRECHNUNG);
        gang.setSatzGruppe("MEISTER");
        var g = service.aktualisiereSatzQuelle(1L, "EIGEN", "MEISTER");
        assertThat(g.folgtVerrechnungssatz()).isFalse();
        assertThat(g.getSatzGruppe()).isNull();
    }

    @Test
    void ungueltigeWerteGebenEinen400MitKlartext() {
        assertThatThrownBy(() -> service.aktualisiereSatzQuelle(1L, "ZAUBER", null)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Von Hand");
        assertThatThrownBy(() -> service.aktualisiereSatzQuelle(1L, "VERRECHNUNG", "CHEF")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Qualifikation");
    }
}
