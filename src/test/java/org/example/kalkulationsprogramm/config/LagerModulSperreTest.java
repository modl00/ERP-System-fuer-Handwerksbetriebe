package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.service.LagerModulSchalterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LagerModulSperreTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<LagerModulSchalterService> bereit(LagerModulSchalterService dienst) {
        ObjectProvider<LagerModulSchalterService> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(dienst);
        return p;
    }

    @Test
    @DisplayName("Eingeschaltet: Anfragen gehen durch")
    void eingeschaltet() throws Exception {
        LagerModulSchalterService dienst = mock(LagerModulSchalterService.class);
        when(dienst.istAktiv()).thenReturn(true);
        MockHttpServletResponse antwort = new MockHttpServletResponse();
        assertThat(new LagerModulSperre(bereit(dienst)).preHandle(new MockHttpServletRequest("GET", "/api/lager/artikel"), antwort, new Object())).isTrue();
        assertThat(antwort.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Ausgeschaltet: 403 mit Meldung")
    void ausgeschaltet() throws Exception {
        LagerModulSchalterService dienst = mock(LagerModulSchalterService.class);
        when(dienst.istAktiv()).thenReturn(false);
        MockHttpServletResponse antwort = new MockHttpServletResponse();
        assertThat(new LagerModulSperre(bereit(dienst)).preHandle(new MockHttpServletRequest("GET", "/api/lager/artikel"), antwort, new Object())).isFalse();
        assertThat(antwort.getStatus()).isEqualTo(403);
        assertThat(antwort.getContentAsString()).contains("Das Lagermodul ist ausgeschaltet.");
    }

    @Test
    @DisplayName("Ohne Dienst gilt das Modul als eingeschaltet")
    void ohneDienst() throws Exception {
        assertThat(new LagerModulSperre(bereit(null)).preHandle(new MockHttpServletRequest("GET", "/api/lager/artikel"), new MockHttpServletResponse(), new Object())).isTrue();
    }
}
