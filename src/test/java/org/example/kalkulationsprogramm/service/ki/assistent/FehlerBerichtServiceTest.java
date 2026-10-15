package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.service.ki.assistent.Fehlerkontext.BrowserFehler;
import org.example.kalkulationsprogramm.service.log.ServerFehlerPuffer;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

/** Der automatische Anhang eines Fehlerberichts (#255): vollständig, begrenzt und bereinigt. Nur Dummy-Daten. */
class FehlerBerichtServiceTest {

    private final ServerFehlerPuffer puffer = mock(ServerFehlerPuffer.class);
    private final Environment umgebung = mock(Environment.class);
    private final FehlerBerichtService bericht = new FehlerBerichtService(puffer, umgebung);

    private static BrowserFehler b(String pfad, Integer status, String meldung) {
        return new BrowserFehler("22:13:58", "POST", pfad, status, meldung);
    }

    @Test
    void ohneKontextGibtEsKeinenAnhang() {
        assertThat(bericht.anhang(null)).isEmpty();
    }

    @Test
    void derAnhangNenntSeiteBrowserFehlerUndServerFehler() {
        when(umgebung.getActiveProfiles()).thenReturn(new String[] { "docker" });
        when(puffer.letzte(org.mockito.ArgumentMatchers.eq(15), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new ServerFehlerPuffer.Eintrag(Instant.parse("2026-10-09T20:13:58Z"), "FrontendUserController", "Speichern fehlgeschlagen", "java.lang.IllegalStateException",
                        List.of("org.example.kalkulationsprogramm.service.X.y(X.java:12)"))));

        String a = bericht.anhang(new Fehlerkontext("/benutzer", "Benutzerverwaltung", List.of("Fehler beim Speichern."), List.of(b("/api/frontend-users", 400, "Mitarbeiter schon vergeben"))));

        assertThat(a).startsWith("<details>").endsWith("</details>")
                .contains("**Seite:** /benutzer (Benutzerverwaltung)").contains("Fehler beim Speichern.")
                .contains("POST /api/frontend-users → 400: Mitarbeiter schon vergeben")
                .contains("[FrontendUserController] Speichern fehlgeschlagen (java.lang.IllegalStateException) an org.example.kalkulationsprogramm.service.X.y(X.java:12)")
                .contains("Profil docker");
    }

    @Test
    void ohneBeobachtungenStehtKeineStattLeererZeilen() {
        when(umgebung.getActiveProfiles()).thenReturn(new String[0]);
        String a = bericht.anhang(new Fehlerkontext("/", null, null, null));
        assertThat(a).contains("Browser (0):** keine").contains("Server (0, höchstens 30 Minuten alt):** keine").contains("Profil (Standard)");
    }

    @Test
    void emailsBankdatenSchluesselUndZugangsdatenWerdenEntfernt() {
        when(umgebung.getActiveProfiles()).thenReturn(new String[0]);
        String a = bericht.anhang(new Fehlerkontext("/x", "Titel", List.of("Kunde max@mustermann-bau.example nicht gefunden"), List.of(
                b("/api/zahlung", 500, "IBAN DE89 3704 0044 0532 0130 00 abgelehnt"),
                b("/api/login", 401, "password=geheim123 und Bearer abc.def.ghi"),
                b("/api/export", 500, "Schlüssel: sk-ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcd"),
                b("/api/gitea", 500, "http://benutzer:pw@192.168.0.1/x"))));

        assertThat(a).doesNotContain("max@mustermann").contains("[E-Mail]")
                .doesNotContain("DE89").contains("[Bankdaten]")
                .doesNotContain("geheim123").doesNotContain("abc.def.ghi").contains("Bearer [entfernt]")
                .doesNotContain("sk-ABCDEFGHIJ").doesNotContain("benutzer:pw");
    }

    @Test
    void adressenOhneParameterNurEigeneAdressenUndLangeZahlenGekuerzt() {
        assertThat(FehlerBerichtService.pfad("/api/artikel?suche=Max+Mustermann&seite=2")).isEqualTo("/api/artikel");
        assertThat(FehlerBerichtService.pfad("https://fremd.example/x")).isEqualTo("[andere Adresse]");
        assertThat(FehlerBerichtService.pfad(null)).isEqualTo("[andere Adresse]");
        assertThat(FehlerBerichtService.pfad("/api/kunden/1234567890")).isEqualTo("/api/kunden/…");
    }

    @Test
    void listenUndFelderWerdenBegrenzt() {
        when(umgebung.getActiveProfiles()).thenReturn(new String[0]);
        List<BrowserFehler> viele = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            viele.add(b("/api/n" + i, 500, "m".repeat(2_000)));
        }
        List<String> seitenFehler = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            seitenFehler.add("Meldung " + i);
        }

        String a = bericht.anhang(new Fehlerkontext("/x", "T", seitenFehler, viele));

        assertThat(a).contains("Browser (10)").contains("Meldung 4").doesNotContain("Meldung 5").doesNotContain("/api/n10");
        assertThat(a.length()).isLessThan(8_000);
    }

    @Test
    void steuerzeichenUndMarkdownAusbruchsversucheBleibenHarmlos() {
        when(umgebung.getActiveProfiles()).thenReturn(new String[0]);
        String a = bericht.anhang(new Fehlerkontext("/x\u0000\n## Überschrift", "T", List.of("</details> <script>alert(1)</script>"), List.of()));
        assertThat(a).doesNotContain("\u0000").doesNotContain("\n## Überschrift");
    }
}
