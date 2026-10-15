package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FunktionsrechtSperreTest {

    private final FunktionsRechteService service = mock(FunktionsRechteService.class);

    @AfterEach
    void aufraeumen() {
        SecurityContextHolder.clearContext();
    }

    @SuppressWarnings("unchecked")
    private FunktionsrechtSperre sperre(FunktionsRechteService dienst) {
        ObjectProvider<FunktionsRechteService> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(dienst);
        return new FunktionsrechtSperre(Funktion.LAGER, p);
    }

    private static void anmelden(Set<FrontendUserRole> rollen) {
        FrontendUserPrincipal principal = new FrontendUserPrincipal(7L, "anna", "Anna Büro", "x", true, rollen);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private boolean pruefe(FunktionsrechtSperre s, String methode, MockHttpServletResponse antwort) throws Exception {
        return s.preHandle(new MockHttpServletRequest(methode, "/api/lager/bestand"), antwort, new Object());
    }

    @Test
    void erlaubteAktionGehtDurch() throws Exception {
        anmelden(Set.of(FrontendUserRole.USER));
        when(service.darfProfil(7L, Funktion.LAGER, FunktionsAktion.ANSEHEN)).thenReturn(true);

        assertThat(pruefe(sperre(service), "GET", new MockHttpServletResponse())).isTrue();
    }

    @Test
    void verweigerteAktionGibt403MitKlartext() throws Exception {
        anmelden(Set.of(FrontendUserRole.USER));
        when(service.darfProfil(7L, Funktion.LAGER, FunktionsAktion.LOESCHEN)).thenReturn(false);
        MockHttpServletResponse antwort = new MockHttpServletResponse();

        assertThat(pruefe(sperre(service), "DELETE", antwort)).isFalse();
        assertThat(antwort.getStatus()).isEqualTo(403);
        assertThat(antwort.getContentAsString()).contains("Dafür fehlt die Berechtigung: Lager, Löschen");
    }

    @Test
    void dieHttpMethodeBestimmtDieAktion() throws Exception {
        anmelden(Set.of(FrontendUserRole.USER));
        when(service.darfProfil(7L, Funktion.LAGER, FunktionsAktion.ANSEHEN)).thenReturn(true);
        when(service.darfProfil(7L, Funktion.LAGER, FunktionsAktion.ANLEGEN)).thenReturn(false);

        assertThat(pruefe(sperre(service), "GET", new MockHttpServletResponse())).isTrue();
        assertThat(pruefe(sperre(service), "POST", new MockHttpServletResponse())).isFalse();
    }

    @Test
    void administratorenSindNichtEingeschraenkt() throws Exception {
        anmelden(Set.of(FrontendUserRole.ADMIN));
        when(service.darfProfil(7L, Funktion.LAGER, FunktionsAktion.LOESCHEN)).thenReturn(false);

        assertThat(pruefe(sperre(service), "DELETE", new MockHttpServletResponse())).isTrue();
    }

    @Test
    void ohneBenutzerAnmeldungPruefenDieEndpunkteSelbst() throws Exception {
        assertThat(pruefe(sperre(service), "POST", new MockHttpServletResponse())).isTrue();
    }

    @Test
    void ohneDienstUndBeiVorabfrageGiltAllesAlsErlaubt() throws Exception {
        anmelden(Set.of(FrontendUserRole.USER));

        assertThat(pruefe(sperre(null), "DELETE", new MockHttpServletResponse())).isTrue();
        assertThat(pruefe(sperre(service), "OPTIONS", new MockHttpServletResponse())).isTrue();
    }
}
