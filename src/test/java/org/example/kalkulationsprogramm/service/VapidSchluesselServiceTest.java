package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.KeyFactory;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VapidSchluesselServiceTest {

    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final Map<String, String> gespeichert = new HashMap<>();
    private VapidSchluesselService service;

    @BeforeEach
    void setUp() {
        when(einstellungen.get(anyString(), any())).thenAnswer(a -> gespeichert.getOrDefault(a.<String>getArgument(0), a.getArgument(1)));
        org.mockito.Mockito.doAnswer(a -> { gespeichert.put(a.getArgument(0), a.getArgument(1)); return null; })
                .when(einstellungen).save(anyString(), any(), any());
        service = new VapidSchluesselService(einstellungen);
    }

    /** Für andere Tests: ein frisch erzeugtes, gültiges Paar. */
    static VapidSchluesselService.Schluessel neuesPaar() {
        return VapidSchluesselService.erzeuge();
    }

    @Test
    @DisplayName("beim ersten Mal wird ein Paar erzeugt und gespeichert, danach immer dasselbe geliefert")
    void erzeugtEinmalUndMerktEs() {
        VapidSchluesselService.Schluessel erster = service.ladeOderErzeuge();
        VapidSchluesselService.Schluessel zweiter = service.ladeOderErzeuge();

        assertThat(erster.oeffentlich()).isNotBlank();
        assertThat(erster.privat()).isNotBlank().isNotEqualTo(erster.oeffentlich());
        assertThat(zweiter).isEqualTo(erster);
        assertThat(gespeichert).containsEntry("push.vapid.public-key", erster.oeffentlich()).containsEntry("push.vapid.private-key", erster.privat());
        verify(einstellungen, org.mockito.Mockito.times(1)).save(eq("push.vapid.private-key"), anyString(), any());
    }

    @Test
    @DisplayName("ein vorhandenes Paar wird nie überschrieben")
    void vorhandenesBleibt() {
        gespeichert.put("push.vapid.public-key", "oeffentlich-alt");
        gespeichert.put("push.vapid.private-key", "privat-alt");

        assertThat(service.ladeOderErzeuge()).isEqualTo(new VapidSchluesselService.Schluessel("oeffentlich-alt", "privat-alt"));
        verify(einstellungen, never()).save(anyString(), any(), any());
    }

    @Test
    @DisplayName("nur die Hälfte vorhanden: es wird ein vollständig neues Paar gespeichert, damit beide zusammenpassen")
    void halbesPaarWirdErsetzt() {
        gespeichert.put("push.vapid.public-key", "nur-oeffentlich");

        VapidSchluesselService.Schluessel s = service.ladeOderErzeuge();

        assertThat(s.oeffentlich()).isNotEqualTo("nur-oeffentlich");
        assertThat(gespeichert.get("push.vapid.private-key")).isEqualTo(s.privat());
    }

    @Test
    @DisplayName("das erzeugte Paar lässt sich so lesen, wie der Push-Dienst es braucht (EC, X.509 und PKCS#8, Base64-URL)")
    void paarIstFuerDenPushDienstLesbar() throws Exception {
        VapidSchluesselService.Schluessel s = VapidSchluesselService.erzeuge();
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyFactory kf = KeyFactory.getInstance("EC", "BC");

        assertThat(kf.generatePublic(new X509EncodedKeySpec(Base64.getUrlDecoder().decode(s.oeffentlich())))).isNotNull();
        assertThat(kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getUrlDecoder().decode(s.privat())))).isNotNull();
        assertThat(VapidSchluesselService.erzeuge().privat()).as("jedes Paar ist neu").isNotEqualTo(s.privat());
    }
}
