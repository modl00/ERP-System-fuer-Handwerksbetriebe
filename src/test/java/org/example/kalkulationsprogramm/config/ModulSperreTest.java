package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.service.Modul;
import org.example.kalkulationsprogramm.service.ModulSchalterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ModulSperreTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ModulSchalterService> bereit(ModulSchalterService dienst) {
        ObjectProvider<ModulSchalterService> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(dienst);
        return p;
    }

    @Test
    @DisplayName("Eingeschaltet: Anfragen gehen durch")
    void eingeschaltet() throws Exception {
        ModulSchalterService dienst = mock(ModulSchalterService.class);
        when(dienst.istAktiv(Modul.MIETE)).thenReturn(true);
        MockHttpServletResponse antwort = new MockHttpServletResponse();
        assertThat(new ModulSperre(Modul.MIETE, bereit(dienst)).preHandle(new MockHttpServletRequest("GET", "/api/miete/mietobjekte"), antwort, new Object())).isTrue();
        assertThat(antwort.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Ausgeschaltet: 403 mit der Meldung des Moduls")
    void ausgeschaltet() throws Exception {
        ModulSchalterService dienst = mock(ModulSchalterService.class);
        when(dienst.istAktiv(Modul.MIETE)).thenReturn(false);
        MockHttpServletResponse antwort = new MockHttpServletResponse();
        assertThat(new ModulSperre(Modul.MIETE, bereit(dienst)).preHandle(new MockHttpServletRequest("GET", "/api/miete/mietobjekte"), antwort, new Object())).isFalse();
        assertThat(antwort.getStatus()).isEqualTo(403);
        assertThat(antwort.getContentAsString()).contains("Das Mietabrechnungsmodul ist ausgeschaltet.");
    }

    @Test
    @DisplayName("Ein anderes ausgeschaltetes Modul sperrt dieses nicht")
    void jedesModulFuerSich() throws Exception {
        ModulSchalterService dienst = mock(ModulSchalterService.class);
        when(dienst.istAktiv(Modul.MIETE)).thenReturn(true);
        when(dienst.istAktiv(Modul.WEBSITE)).thenReturn(false);
        assertThat(new ModulSperre(Modul.MIETE, bereit(dienst)).preHandle(new MockHttpServletRequest("GET", "/api/miete/x"), new MockHttpServletResponse(), new Object())).isTrue();
        assertThat(new ModulSperre(Modul.WEBSITE, bereit(dienst)).preHandle(new MockHttpServletRequest("GET", "/api/beitraege"), new MockHttpServletResponse(), new Object())).isFalse();
    }

    @Test
    @DisplayName("Ohne Dienst gilt das Modul als eingeschaltet")
    void ohneDienst() throws Exception {
        assertThat(new ModulSperre(Modul.WEBSITE, bereit(null)).preHandle(new MockHttpServletRequest("GET", "/api/beitraege"), new MockHttpServletResponse(), new Object())).isTrue();
    }
}
