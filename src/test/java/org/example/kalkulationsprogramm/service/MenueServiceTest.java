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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MenueServiceTest {

    @Mock
    private SystemSettingRepository repository;

    @InjectMocks
    private MenueService service;

    private static SystemSetting eintrag(String schluessel, String wert) {
        SystemSetting s = new SystemSetting();
        s.setKey(schluessel);
        s.setValue(wert);
        return s;
    }

    @Test
    void ohneEintragIstNichtsGespeichert() {
        when(repository.findById(MenueService.SCHLUESSEL_VORGABE)).thenReturn(Optional.empty());
        when(repository.findById("menue.benutzer.7")).thenReturn(Optional.empty());
        assertTrue(service.vorgabe().isEmpty());
        assertTrue(service.eigene(7).isEmpty());
    }

    @Test
    void leereAuswahlIstEtwasAnderesAlsNichtGespeichert() {
        when(repository.findById("menue.benutzer.7")).thenReturn(Optional.of(eintrag("menue.benutzer.7", "")));
        assertEquals(List.of(), service.eigene(7).orElseThrow());
    }

    @Test
    void liestGespeicherteAdressenUndIgnoriertKaputtes() {
        when(repository.findById(MenueService.SCHLUESSEL_VORGABE))
                .thenReturn(Optional.of(eintrag(MenueService.SCHLUESSEL_VORGABE, "/werkstoffe,<script>,/aufschlaege,/werkstoffe")));
        assertEquals(List.of("/werkstoffe", "/aufschlaege"), service.vorgabe().orElseThrow());
    }

    @Test
    void speichertDieEigeneAuswahlJeBenutzerOhneDoppelte() {
        when(repository.findById("menue.benutzer.7")).thenReturn(Optional.empty());

        List<String> ergebnis = service.setzeEigene(7, List.of("/lager", "/miete", "/lager"));

        assertEquals(List.of("/lager", "/miete"), ergebnis);
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(captor.capture());
        assertEquals("menue.benutzer.7", captor.getValue().getKey());
        assertEquals("/lager,/miete", captor.getValue().getValue());
    }

    @Test
    void speichertDieVorgabeDerFirma() {
        when(repository.findById(MenueService.SCHLUESSEL_VORGABE)).thenReturn(Optional.empty());
        service.setzeVorgabe(List.of("/werkstoffe"));
        ArgumentCaptor<SystemSetting> captor = ArgumentCaptor.forClass(SystemSetting.class);
        verify(repository).save(captor.capture());
        assertEquals(MenueService.SCHLUESSEL_VORGABE, captor.getValue().getKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {"werkstoffe", "/", "/Werkstoffe", "/a b", "/a,b", "//../etc/passwd?x", "'; DROP TABLE x; --",
            "<script>alert(1)</script>", "/ä", "javascript:alert(1)"})
    void ungueltigeAdressenWerdenAbgelehnt(String adresse) {
        assertThrows(IllegalArgumentException.class, () -> service.setzeEigene(7, List.of(adresse)));
        verify(repository, never()).save(any());
    }

    @Test
    void fehlendeUndNullEintraegeWerdenAbgelehnt() {
        assertThrows(IllegalArgumentException.class, () -> service.setzeEigene(7, null));
        List<String> mitNull = new ArrayList<>();
        mitNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> service.setzeEigene(7, mitNull));
        verify(repository, never()).save(any());
    }

    @Test
    void zuvieleEintraegeUndUeberlangeAdressenWerdenAbgelehnt() {
        List<String> viele = new ArrayList<>();
        for (int i = 0; i <= MenueService.MAX_EINTRAEGE; i++) {
            viele.add("/punkt" + i);
        }
        assertThrows(IllegalArgumentException.class, () -> service.setzeEigene(7, viele));
        assertThrows(IllegalArgumentException.class, () -> service.setzeEigene(7, List.of("/" + "a".repeat(10_001))));
        verify(repository, never()).save(any());
    }

    @Test
    void loeschenEntferntNurDenEigenenEintrag() {
        SystemSetting vorhanden = eintrag("menue.benutzer.7", "/lager");
        when(repository.findById("menue.benutzer.7")).thenReturn(Optional.of(vorhanden));
        service.loescheEigene(7);
        verify(repository).delete(vorhanden);
    }
}
