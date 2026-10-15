package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Startbildschirm (#290): Reihenfolge bleibt, Doppelte und Kaputtes fliegen raus, leer ist etwas anderes als nicht gespeichert. */
@ExtendWith(MockitoExtension.class)
class StartbildschirmServiceTest {

    @Mock
    private SystemSettingRepository repository;

    @InjectMocks
    private StartbildschirmService service;

    private static SystemSetting eintrag(String schluessel, String wert) {
        SystemSetting s = new SystemSetting();
        s.setKey(schluessel);
        s.setValue(wert);
        return s;
    }

    @Test
    void ohneEintragIstNichtsGespeichertUndEineLeereLeisteIstEtwasAnderes() {
        when(repository.findById(StartbildschirmService.SCHLUESSEL_VORGABE)).thenReturn(Optional.empty());
        when(repository.findById("start.benutzer.7")).thenReturn(Optional.of(eintrag("start.benutzer.7", "")));
        assertTrue(service.vorgabe().isEmpty());
        assertEquals(List.of(), service.eigene(7).orElseThrow());
    }

    @Test
    void dieReihenfolgeBleibtUndKaputtesWirdIgnoriert() {
        when(repository.findById("start.benutzer.7")).thenReturn(Optional.of(eintrag("start.benutzer.7", "lager,<script>,aufgaben,lager,plantafel")));
        assertEquals(List.of("lager", "aufgaben", "plantafel"), service.eigene(7).orElseThrow());
    }

    @Test
    void speichertInDerGegebenenReihenfolgeOhneDoppelte() {
        when(repository.findById("start.benutzer.7")).thenReturn(Optional.empty());
        List<String> antwort = service.setzeEigene(7, List.of("plantafel", "lager", "plantafel", "aufgaben"));
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(captor.capture());
        assertEquals("start.benutzer.7", captor.getValue().getKey());
        assertEquals("plantafel,lager,aufgaben", captor.getValue().getValue());
        assertEquals(List.of("plantafel", "lager", "aufgaben"), antwort);
    }

    @Test
    void zuvieleOderUngueltigeEintraegeWerdenAbgelehntUndNichtGespeichert() {
        List<String> zuviele = new ArrayList<>();
        for (int i = 0; i < StartbildschirmService.MAX_EINTRAEGE + 1; i++) zuviele.add("baustein-" + i);
        assertThrows(IllegalArgumentException.class, () -> service.setzeEigene(7, zuviele));
        assertThrows(IllegalArgumentException.class, () -> service.setzeEigene(7, List.of("Java Script!")));
        assertThrows(IllegalArgumentException.class, () -> service.setzeVorgabe(null));
        verify(repository, never()).save(any());
    }

    @Test
    void zurueckZurVorgabeLoeschtNurDieEigeneLeiste() {
        SystemSetting e = eintrag("start.benutzer.7", "lager");
        when(repository.findById("start.benutzer.7")).thenReturn(Optional.of(e));
        service.loescheEigene(7);
        verify(repository).delete(e);
        verify(repository, never()).findById(StartbildschirmService.SCHLUESSEL_VORGABE);
    }
}
