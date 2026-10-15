package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.service.wartung.WartungModulSchalterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class WartungModulSperreTest {

    @SuppressWarnings("unchecked")
    private static ObjectProvider<WartungModulSchalterService> bereitstellung(WartungModulSchalterService dienst) {
        ObjectProvider<WartungModulSchalterService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(dienst);
        return provider;
    }

    @Test
    @DisplayName("Eingeschaltet: Anfragen gehen durch")
    void eingeschaltet() throws Exception {
        WartungModulSchalterService dienst = mock(WartungModulSchalterService.class);
        when(dienst.istAktiv()).thenReturn(true);
        MockHttpServletResponse antwort = new MockHttpServletResponse();

        boolean weiter = new WartungModulSperre(bereitstellung(dienst)).preHandle(new MockHttpServletRequest("GET", "/api/wartung/module"), antwort, new Object());

        assertThat(weiter).isTrue();
        assertThat(antwort.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Ausgeschaltet: 403 mit Meldung als JSON, die Anfrage kommt nicht beim Controller an")
    void ausgeschaltet() throws Exception {
        WartungModulSchalterService dienst = mock(WartungModulSchalterService.class);
        when(dienst.istAktiv()).thenReturn(false);
        MockHttpServletResponse antwort = new MockHttpServletResponse();

        boolean weiter = new WartungModulSperre(bereitstellung(dienst)).preHandle(new MockHttpServletRequest("GET", "/api/wartung/protokolle"), antwort, new Object());

        assertThat(weiter).isFalse();
        assertThat(antwort.getStatus()).isEqualTo(403);
        assertThat(antwort.getContentType()).startsWith("application/json");
        assertThat(antwort.getContentAsString()).contains("Das Wartungsmodul ist ausgeschaltet.");
    }

    @Test
    @DisplayName("Ohne Schalter-Dienst (schlanke Tests) gilt das Modul als eingeschaltet")
    void ohneDienst() throws Exception {
        boolean weiter = new WartungModulSperre(bereitstellung(null)).preHandle(
                new MockHttpServletRequest("GET", "/api/wartung/module"), new MockHttpServletResponse(), new Object());

        assertThat(weiter).isTrue();
    }
}
