package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.KrankenkasseRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterDokumentRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterNotizRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterStundenlohnRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Mitarbeiter löschen: Das Programm nennt, was ihn festhält, und löscht dann nichts (#284). Nur Dummy-Daten. */
class MitarbeiterLoeschenMeldungTest {

    private final MitarbeiterRepository repo = mock(MitarbeiterRepository.class);
    private final MitarbeiterBezuege bezuege = mock(MitarbeiterBezuege.class);
    private final MitarbeiterService service = new MitarbeiterService(repo, mock(MitarbeiterDokumentRepository.class), mock(MitarbeiterNotizRepository.class),
            mock(AbteilungRepository.class), mock(KrankenkasseRepository.class), mock(MitarbeiterStundenlohnRepository.class));

    @Test
    void dieMeldungNenntDieEintraegeUndDenAusweg_undNichtsWirdGeloescht() {
        ReflectionTestUtils.setField(service, "bezuege", bezuege);
        when(repo.findById(7L)).thenReturn(Optional.of(new Mitarbeiter()));
        when(bezuege.beschreibe(7L)).thenReturn(List.of("12 Zeitbuchungen", "1 Lohnabrechnung"));

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12 Zeitbuchungen, 1 Lohnabrechnung")
                .hasMessageContaining("bei „Aktiv“ aus");
        verify(repo, never()).deleteById(7L);
    }
}
