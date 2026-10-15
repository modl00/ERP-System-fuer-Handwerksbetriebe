package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.service.AktivitaetKatalog;
import org.example.kalkulationsprogramm.service.AktivitaetService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerMapping;

class AktivitaetInterceptorTest {

    @SuppressWarnings("unchecked")
    ObjectProvider<AktivitaetService> provider = mock(ObjectProvider.class);
    AktivitaetService service = mock(AktivitaetService.class);
    AktivitaetInterceptor interceptor = new AktivitaetInterceptor(provider);
    MockHttpServletResponse antwort = new MockHttpServletResponse();

    @BeforeEach
    void setUp() {
        when(provider.getIfAvailable()).thenReturn(service);
        anmelden(Set.of(FrontendUserRole.USER));
    }

    @AfterEach
    void aufraeumen() {
        SecurityContextHolder.clearContext();
    }

    private void anmelden(Set<FrontendUserRole> rollen) {
        FrontendUserPrincipal p = new FrontendUserPrincipal(5L, "max", "Max Mustermann", "x", true, rollen);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private MockHttpServletRequest anfrage(String methode, String muster, Map<String, String> variablen) {
        MockHttpServletRequest r = new MockHttpServletRequest(methode, "/irgendwas");
        r.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, muster);
        r.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, variablen);
        return r;
    }

    @Test
    @DisplayName("Erfolgreiche Änderung aus dem Katalog: Benutzer, Regel und erste Nummer der Adresse gehen an den Dienst")
    void protokolliert() {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("projektId", "7");
        v.put("dokumentId", "50");

        interceptor.afterCompletion(anfrage("POST", "/api/projekte/{projektId}/material-abrechnung/{dokumentId}", v), antwort, null, null);

        verify(service).erfasse(eq(5L), eq("Max Mustermann"), eq(false), eq(AktivitaetKatalog.finde("POST", "/api/projekte/{}/material-abrechnung/{}").orElseThrow()), eq(7L));
    }

    @Test
    @DisplayName("Administratoren werden als solche gemeldet; Aktionen ohne Nummer in der Adresse haben kein Objekt")
    void adminUndOhneObjekt() {
        anmelden(Set.of(FrontendUserRole.ADMIN));

        interceptor.afterCompletion(anfrage("POST", "/api/kunden", Map.of()), antwort, null, null);

        verify(service).erfasse(eq(5L), eq("Max Mustermann"), eq(true), any(), eq(null));
    }

    @Test
    @DisplayName("Lesen, Fehler (400 und mehr), Ausnahmen, unbekannte Adressen und fehlende Anmeldung werden nicht protokolliert")
    void nichtProtokolliert() {
        interceptor.afterCompletion(anfrage("GET", "/api/projekte/{id}", Map.of("id", "7")), antwort, null, null);
        MockHttpServletResponse fehler = new MockHttpServletResponse();
        fehler.setStatus(400);
        interceptor.afterCompletion(anfrage("PUT", "/api/projekte/{id}", Map.of("id", "7")), fehler, null, null);
        interceptor.afterCompletion(anfrage("PUT", "/api/projekte/{id}", Map.of("id", "7")), antwort, null, new RuntimeException("x"));
        interceptor.afterCompletion(anfrage("POST", "/api/auth/login", Map.of()), antwort, null, null);
        interceptor.afterCompletion(new MockHttpServletRequest("POST", "/api/projekte"), antwort, null, null);
        SecurityContextHolder.clearContext();
        interceptor.afterCompletion(anfrage("PUT", "/api/projekte/{id}", Map.of("id", "7")), antwort, null, null);

        verify(service, never()).erfasse(any(), any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("Ein Fehler beim Protokollieren oder fehlender Dienst stören die Aktion nie")
    void fehlerSindHarmlos() {
        doThrow(new IllegalStateException("Datenbank weg")).when(service).erfasse(any(), any(), anyBoolean(), any(), any());

        interceptor.afterCompletion(anfrage("PUT", "/api/projekte/{id}", Map.of("id", "7")), antwort, null, null);

        when(provider.getIfAvailable()).thenReturn(null);
        interceptor.afterCompletion(anfrage("PUT", "/api/projekte/{id}", Map.of("id", "7")), antwort, null, null);
        assertThat(antwort.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Beim Anlegen gilt die Nummer aus der Antwort, auch wenn die Adresse keine Nummer enthält")
    void nummerAusAntwort() {
        MockHttpServletRequest r = anfrage("POST", "/api/projekte", Map.of());
        r.setAttribute(AktivitaetObjektAdvice.ATTRIBUT, 11L);

        interceptor.afterCompletion(r, antwort, null, null);

        verify(service).erfasse(eq(5L), any(), eq(false), eq(AktivitaetKatalog.finde("POST", "/api/projekte").orElseThrow()), eq(11L));
    }

    @Test
    @DisplayName("Eine erste Variable, die keine Zahl ist, ergibt kein Objekt")
    void keineZahl() {
        interceptor.afterCompletion(anfrage("PUT", "/api/projekte/{id}", Map.of("id", "abc")), antwort, null, null);

        verify(service).erfasse(eq(5L), any(), anyBoolean(), any(), eq(null));
    }
}
