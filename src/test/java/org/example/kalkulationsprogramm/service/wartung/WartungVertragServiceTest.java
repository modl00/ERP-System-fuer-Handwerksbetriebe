package org.example.kalkulationsprogramm.service.wartung;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungVertrag;
import org.example.kalkulationsprogramm.dto.wartung.WartungVertragDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungVertragSpeichernDto;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungVertragRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Nur Dummy-Daten (DSGVO). */
class WartungVertragServiceTest {

    WartungVertragRepository vertraege = mock(WartungVertragRepository.class);
    ProjektRepository projekte = mock(ProjektRepository.class);
    Clock uhr = Clock.fixed(Instant.parse("2026-10-03T08:00:00Z"), ZoneId.of("Europe/Berlin"));
    WartungVertragService service;
    Projekt projekt;

    @BeforeEach
    void setUp() {
        service = new WartungVertragService(vertraege, projekte, uhr);
        projekt = new Projekt();
        projekt.setId(3L);
        when(projekte.findById(3L)).thenReturn(Optional.of(projekt));
        when(vertraege.findByProjektId(3L)).thenReturn(Optional.empty());
        when(vertraege.save(any(WartungVertrag.class))).thenAnswer(a -> a.getArgument(0));
    }

    private static WartungVertragSpeichernDto eingabe(String beginn, String ende, Integer intervall, String preis) {
        return new WartungVertragSpeichernDto(" WV-2026-1 ", LocalDate.parse(beginn), ende == null ? null : LocalDate.parse(ende), intervall,
                preis == null ? null : new BigDecimal(preis), "  Jährliche Prüfung  ");
    }

    @Test
    void legtDenVertragAnUndBereinigtDieTexte() {
        WartungVertragDto d = service.speichern(3L, eingabe("2026-01-01", "2028-12-31", 12, "450.5"));

        assertEquals("WV-2026-1", d.vertragsnummer());
        assertEquals("Jährliche Prüfung", d.notiz());
        assertEquals(new BigDecimal("450.50"), d.preisNetto());
        assertEquals(12, d.intervallMonate());
        assertEquals(3L, d.projektId());
        assertEquals("LAEUFT", d.status());
        assertFalse(d.endetBald());
    }

    @Test
    void einBestehenderVertragWirdErsetztNichtDoppeltAngelegt() {
        WartungVertrag da = new WartungVertrag();
        da.setProjekt(projekt);
        da.setBeginn(LocalDate.parse("2025-01-01"));
        when(vertraege.findByProjektId(3L)).thenReturn(Optional.of(da));

        service.speichern(3L, eingabe("2026-01-01", null, 6, null));

        assertEquals(LocalDate.parse("2026-01-01"), da.getBeginn());
        assertEquals(6, da.getIntervallMonate());
        assertEquals(null, da.getEnde(), "leeres Ende = unbefristet");
        assertEquals(null, da.getPreisNetto());
        verify(vertraege).save(da);
    }

    @Test
    void leitetDenStatusAusDemHeutigenTagAb() {
        assertEquals("NOCH_NICHT_GESTARTET", service.speichern(3L, eingabe("2026-11-01", null, 12, null)).status());
        assertEquals("ABGELAUFEN", service.speichern(3L, eingabe("2024-01-01", "2026-10-02", 12, null)).status());
        WartungVertragDto bald = service.speichern(3L, eingabe("2024-01-01", "2026-12-15", 12, null));
        assertEquals("LAEUFT", bald.status());
        assertTrue(bald.endetBald(), "endet in 73 Tagen");
        assertEquals("LAEUFT", service.speichern(3L, eingabe("2024-01-01", "2026-10-03", 12, null)).status(), "am letzten Tag läuft er noch");
    }

    @Test
    void weistFalscheAngabenAbOhneZuSpeichern() {
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(3L, new WartungVertragSpeichernDto(null, null, null, 12, null, null)))
                .getReason().contains("Beginn"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(3L, eingabe("2026-05-01", "2026-04-30", 12, null)))
                .getReason().contains("Ende"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(3L, eingabe("2026-01-01", null, 0, null)))
                .getReason().contains("Intervall"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(3L, eingabe("2026-01-01", null, 121, null)))
                .getReason().contains("Intervall"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(3L, eingabe("2026-01-01", null, 12, "-1")))
                .getReason().contains("Preis"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(3L, new WartungVertragSpeichernDto("x".repeat(61), LocalDate.parse("2026-01-01"), null, 12, null, null)))
                .getReason().contains("Vertragsnummer"));
        assertTrue(assertThrows(ResponseStatusException.class, () -> service.speichern(3L, new WartungVertragSpeichernDto(null, LocalDate.parse("2026-01-01"), null, 12, null, "y".repeat(1001))))
                .getReason().contains("Notiz"));
        verify(vertraege, never()).save(any());
    }

    @Test
    void ohneIntervallGiltJaehrlich() {
        assertEquals(12, service.speichern(3L, eingabe("2026-01-01", null, null, null)).intervallMonate());
    }

    @Test
    void ohneVertragGibtEsNichtsUndEinUnbekanntesProjektIstNichtGefunden() {
        assertTrue(service.finde(3L).isEmpty());

        when(projekte.findById(9L)).thenReturn(Optional.empty());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> service.finde(9L));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.speichern(9L, eingabe("2026-01-01", null, 12, null)));
        assertThrows(ResponseStatusException.class, () -> service.loeschen(null));
    }

    @Test
    void loeschenEntferntDenVertragUndIstBeiKeinemVertragHarmlos() {
        WartungVertrag da = new WartungVertrag();
        when(vertraege.findByProjektId(3L)).thenReturn(Optional.of(da));
        service.loeschen(3L);
        verify(vertraege).delete(da);

        when(vertraege.findByProjektId(3L)).thenReturn(Optional.empty());
        service.loeschen(3L);
        verify(vertraege, org.mockito.Mockito.times(1)).delete(any());
    }
}
