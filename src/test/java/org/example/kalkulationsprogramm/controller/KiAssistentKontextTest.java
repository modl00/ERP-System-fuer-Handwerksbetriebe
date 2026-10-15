package org.example.kalkulationsprogramm.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.config.FrontendUserPrincipal;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.service.ki.assistent.Fehlerkontext.BrowserFehler;
import org.example.kalkulationsprogramm.service.ki.assistent.KiAssistentService;
import org.example.kalkulationsprogramm.service.ki.assistent.KiKontext;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/** Aus der Chat-Anfrage wird der Fehlerkontext des Benutzers (#255). Nur Dummy-Daten. */
class KiAssistentKontextTest {

    private static UsernamePasswordAuthenticationToken admin() {
        FrontendUserPrincipal p = new FrontendUserPrincipal(7L, "admin", "Admin", "x", true, Set.of(FrontendUserRole.ADMIN));
        return new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities());
    }

    @Test
    void seiteUndBrowserFehlerWerdenZumFehlerkontext() {
        var anfrage = new KiAssistentController.ChatAnfrage(List.of(), new KiAssistentService.Seite("/projekte", "Projekte", "Speichern fehlgeschlagen", "…"), null,
                new KiAssistentController.BrowserKontext(List.of(new BrowserFehler("22:13:58", "POST", "/api/x", 500, "kaputt"))));

        KiKontext k = KiAssistentController.kontext(admin(), anfrage);

        assertThat(k.profilId()).isEqualTo(7L);
        assertThat(k.admin()).isTrue();
        assertThat(k.fehler().route()).isEqualTo("/projekte");
        assertThat(k.fehler().titel()).isEqualTo("Projekte");
        assertThat(k.fehler().seitenFehler()).containsExactly("Speichern fehlgeschlagen");
        assertThat(k.fehler().browserFehler()).hasSize(1);
    }

    @Test
    void derSichtbareSeiteninhaltGehtNichtInDenFehlerkontext() {
        var anfrage = new KiAssistentController.ChatAnfrage(List.of(), new KiAssistentService.Seite("/kunden", "Kunden", null, "GEHEIMER KUNDENTEXT"), null, null);
        assertThat(KiAssistentController.kontext(admin(), anfrage).fehler().toString()).doesNotContain("GEHEIMER KUNDENTEXT");
    }

    @Test
    void ohneSeiteUndBrowserDatenGibtEsKeinenFehlerkontext() {
        assertThat(KiAssistentController.kontext(admin(), null).fehler()).isNull();
        assertThat(KiAssistentController.kontext(admin(), new KiAssistentController.ChatAnfrage(List.of(), null, null, null)).fehler()).isNull();
        assertThat(KiAssistentController.kontext(admin(), new KiAssistentController.ChatAnfrage(List.of(), null, null, new KiAssistentController.BrowserKontext(null))).fehler()).isNull();
    }

    @Test
    void ohneBrowserFehlerBleibtDieSeiteImKontext() {
        var anfrage = new KiAssistentController.ChatAnfrage(List.of(), new KiAssistentService.Seite("/", "Start", "", null), null, null);
        KiKontext k = KiAssistentController.kontext(admin(), anfrage);
        assertThat(k.fehler().seitenFehler()).isEmpty();
        assertThat(k.fehler().browserFehler()).isEmpty();
    }
}
