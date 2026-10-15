package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/** Nur Dummy-Adressen (example.org). */
class AngebotsLinkServiceTest {

    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final Map<String, String> gespeichert = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(einstellungen.get(anyString(), any())).thenAnswer(a -> gespeichert.getOrDefault(a.<String>getArgument(0), a.getArgument(1)));
        org.mockito.Mockito.doAnswer(a -> { gespeichert.put(a.getArgument(0), a.getArgument(1)); return null; })
                .when(einstellungen).save(anyString(), any(), any());
    }

    private AngebotsLinkService service(String konfigAdresse) {
        return new AngebotsLinkService(einstellungen, konfigAdresse);
    }

    @Test
    @DisplayName("neue Installation ohne Eintrag und ohne Konfiguration: Link aus, keine Adresse")
    void neueInstallationHatKeinenLink() {
        AngebotsLinkService s = service("");

        assertThat(s.aktiv()).isFalse();
        assertThat(s.adresse()).isEmpty();
        assertThat(s.linkMoeglich()).isFalse();
        assertThat(s.status().moeglich()).isFalse();
        assertThat(s.gueltigkeitTage()).isEqualTo(14);
    }

    @Test
    @DisplayName("Bestandsbetrieb mit Adresse in der Konfiguration behält den Link, solange nichts eingestellt wurde")
    void bestandsbetriebBehaeltDenLink() {
        AngebotsLinkService s = service("https://erp.example.org/");

        assertThat(s.linkMoeglich()).isTrue();
        assertThat(s.adresse()).isEqualTo("https://erp.example.org");
        assertThat(s.konfiguration().adresseAusKonfiguration()).isTrue();
    }

    @Test
    @DisplayName("eine Einstellung schlägt die Konfiguration, auch das Ausschalten")
    void einstellungSchlaegtKonfiguration() {
        AngebotsLinkService s = service("https://erp.example.org");
        s.speichern(false, "", null);

        assertThat(s.aktiv()).isFalse();
        assertThat(s.linkMoeglich()).isFalse();
    }

    @Test
    @DisplayName("Einschalten braucht eine Adresse, mit Adresse geht es und wird gemerkt")
    void einschaltenBrauchtAdresse() {
        AngebotsLinkService s = service("");

        assertThatThrownBy(() -> s.speichern(true, " ", null)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("öffentliche Adresse");
        verify(einstellungen, never()).save(anyString(), any(), any());

        AngebotsLinkService.Konfiguration k = s.speichern(true, " https://kunden.example.org/ ", 30);

        assertThat(k.moeglich()).isTrue();
        assertThat(k.adresse()).isEqualTo("https://kunden.example.org");
        assertThat(k.gueltigkeitTage()).isEqualTo(30);
        assertThat(k.adresseAusKonfiguration()).isFalse();
    }

    @Test
    @DisplayName("Adresse: nur https (http nur für localhost), ohne Zugangsdaten, Parameter und Unsinn")
    void adresseWirdGeprueft() {
        AngebotsLinkService s = service("");
        for (String schlecht : new String[] { "http://erp.example.org", "ftp://erp.example.org", "erp.example.org",
                "https://user:pw@erp.example.org", "https://erp.example.org?x=1", "https://erp.example.org#a", "https://", "https://a b.example.org" }) {
            assertThatThrownBy(() -> s.speichern(false, schlecht, null)).as(schlecht).isInstanceOf(ResponseStatusException.class);
        }
        assertThat(s.speichern(false, "http://localhost:8080", null).adresse()).isEqualTo("http://localhost:8080");
        assertThatThrownBy(() -> s.speichern(false, "https://" + "a".repeat(250) + ".org", null)).hasMessageContaining("zu lang");
    }

    @Test
    @DisplayName("Gültigkeit: 1 bis 365 Tage, ungültige Einträge fallen auf 14 zurück")
    void gueltigkeit() {
        AngebotsLinkService s = service("");

        assertThatThrownBy(() -> s.speichern(false, "", 0)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> s.speichern(false, "", 366)).isInstanceOf(ResponseStatusException.class);
        s.speichern(false, "", 365);
        assertThat(s.gueltigkeitTage()).isEqualTo(365);

        gespeichert.put("freigabe.link.gueltigkeit-tage", "kaputt");
        assertThat(s.gueltigkeitTage()).isEqualTo(14);
        gespeichert.put("freigabe.link.gueltigkeit-tage", "9999");
        assertThat(s.gueltigkeitTage()).isEqualTo(14);
    }

    @Test
    @DisplayName("ohne neue Gültigkeit bleibt die bisherige unverändert")
    void gueltigkeitBleibt() {
        AngebotsLinkService s = service("");
        s.speichern(false, "", 21);
        s.speichern(false, "", null);

        assertThat(s.gueltigkeitTage()).isEqualTo(21);
    }
}
