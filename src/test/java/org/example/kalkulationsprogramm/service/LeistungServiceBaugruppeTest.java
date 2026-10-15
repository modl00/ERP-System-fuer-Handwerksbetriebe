package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.dto.Leistung.LeistungCreateDto;
import org.example.kalkulationsprogramm.mapper.LeistungMapper;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Eine Baugruppe, die andere Leistungen benutzen, lässt sich nicht löschen und nicht zurückstufen (#135). */
class LeistungServiceBaugruppeTest {

    LeistungRepository repo = mock(LeistungRepository.class);
    LeistungMapper mapper = mock(LeistungMapper.class);
    LeistungService service = new LeistungService(repo, mapper);
    Leistung pfosten = new Leistung();

    @BeforeEach
    void setUp() {
        pfosten.setId(11L);
        pfosten.setBezeichnung("Pfosten");
        pfosten.setBaugruppe(true);
        when(repo.findById(11L)).thenReturn(Optional.of(pfosten));
        when(repo.save(any(Leistung.class))).thenAnswer(a -> a.getArgument(0));
    }

    @Test
    void einBenutzterPfostenLaesstSichNichtLoeschenUndNenntWo() {
        when(repo.findVerwenderDerBaugruppe(11L)).thenReturn(List.of("Geländer 1,0 m", "Geländer mit Glas"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.deleteLeistung(11L));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertTrue(e.getReason().contains("Geländer 1,0 m, Geländer mit Glas"), e.getReason());
        verify(repo, never()).deleteById(any());
    }

    @Test
    void ohneVerwenderLaesstSichJedeLeistungWieBisherLoeschen() {
        when(repo.findVerwenderDerBaugruppe(11L)).thenReturn(List.of());

        service.deleteLeistung(11L);

        verify(repo).deleteById(11L);
    }

    @Test
    void dasKennzeichenBaugruppeLaesstSichNichtEntfernenSolangeSieBenutztWird() {
        org.mockito.Mockito.doAnswer(a -> {
            ((Leistung) a.getArgument(0)).setBaugruppe(Boolean.TRUE.equals(((LeistungCreateDto) a.getArgument(1)).getBaugruppe()));
            return null;
        }).when(mapper).updateEntity(any(Leistung.class), any(LeistungCreateDto.class));
        LeistungCreateDto aus = new LeistungCreateDto();
        aus.setBaugruppe(false);
        when(repo.findVerwenderDerBaugruppe(11L)).thenReturn(List.of("Geländer 1,0 m"));

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.updateLeistung(11L, aus));

        assertEquals(HttpStatus.CONFLICT, e.getStatusCode());
        assertTrue(e.getReason().contains("Als Baugruppe nutzbar"), e.getReason());
        verify(repo, never()).save(any());

        // Wird sie nicht benutzt, geht es
        when(repo.findVerwenderDerBaugruppe(11L)).thenReturn(List.of());
        service.updateLeistung(11L, aus);
        assertFalse(pfosten.isBaugruppe());
    }
}
