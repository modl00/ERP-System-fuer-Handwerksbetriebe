package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WebsiteZugangServiceTest {

    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final FirmeninformationService firma = mock(FirmeninformationService.class);
    private final Map<String, String> gespeichert = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(einstellungen.get(anyString(), any())).thenAnswer(a -> gespeichert.getOrDefault(a.<String>getArgument(0), a.getArgument(1)));
        org.mockito.Mockito.doAnswer(a -> { gespeichert.put(a.getArgument(0), a.getArgument(1)); return null; })
                .when(einstellungen).save(anyString(), any(), any());
        when(firma.getFirmeninformation()).thenReturn(new FirmeninformationDto());
    }

    private WebsiteZugangService dienst(String standardAdresse, String standardToken) {
        return new WebsiteZugangService(einstellungen, firma, standardAdresse, standardToken);
    }

    @Test
    void ohneAllesIstNichtsEingerichtet() {
        var k = dienst("", "").konfiguration();

        assertThat(k.eingerichtet()).isFalse();
        assertThat(k.tokenGesetzt()).isFalse();
        assertThat(k.adresse()).isEmpty();
    }

    @Test
    void eineEinstellungSchlaegtDieUmgebungsvariablen() {
        var d = dienst("https://alt.example.test", "alt-token");
        d.speichern("https://neu.example.test/", "neu-token");

        assertThat(d.adresse()).isEqualTo("https://neu.example.test/");
        assertThat(d.token()).isEqualTo("neu-token");
    }

    @Test
    void ohneEinstellungGiltDieUmgebungsvariable() {
        var k = dienst("https://alt.example.test", "alt-token").konfiguration();

        assertThat(k.adresse()).isEqualTo("https://alt.example.test");
        assertThat(k.tokenGesetzt()).isTrue();
        assertThat(k.eingerichtet()).isTrue();
    }

    @Test
    void ohneAdresseGiltDieFirmenwebsiteAberNurMitSchluessel() {
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setWebsite("https://www.muster-metallbau.example");
        when(firma.getFirmeninformation()).thenReturn(dto);
        var d = dienst("", "");

        assertThat(d.konfiguration().eingerichtet()).as("Adresse ja, Schlüssel nein").isFalse();
        var k = d.speichern("", "geheim");
        assertThat(k.adresseAusFirmendaten()).isTrue();
        assertThat(k.eingerichtet()).isTrue();
    }

    @Test
    void schluesselBleibtBeiNullUnveraendertWirdEntferntBeiLeerUndNieZurueckgegeben() {
        var d = dienst("", "");
        d.speichern("https://w.example.test", "geheim");

        var k = d.speichern("https://w.example.test", null);
        assertThat(k.tokenGesetzt()).isTrue();
        assertThat(k.toString()).doesNotContain("geheim");

        assertThat(d.speichern("https://w.example.test", "").tokenGesetzt()).isFalse();
    }

    @Test
    void ungueltigeAdressenWerdenAbgelehntOhneZuSpeichern() {
        var d = dienst("", "");
        for (String falsch : List.of("http://w.example.test", "ftp://w.example.test", "w.example.test", "https://", "https://user:pw@w.example.test",
                "https://w.example.test?x=1", "https://w.example.test#a", "javascript:alert(1)", "https://" + "a".repeat(250) + ".test")) {
            assertThatThrownBy(() -> d.speichern(falsch, "t")).as(falsch).isInstanceOf(ResponseStatusException.class);
        }
        assertThat(gespeichert).isEmpty();
    }

    @Test
    void httpIstNurFuerDenEigenenRechnerErlaubt() {
        var d = dienst("", "");
        assertThat(d.speichern("http://localhost:4321", "t").adresse()).isEqualTo("http://localhost:4321");
        assertThat(d.speichern("http://127.0.0.1:4321", "t").adresse()).isEqualTo("http://127.0.0.1:4321");
    }

    @Test
    void zuLangerSchluesselWirdAbgelehnt() {
        assertThatThrownBy(() -> dienst("", "").speichern("https://w.example.test", "x".repeat(501))).hasMessageContaining("zu lang");
    }
}
