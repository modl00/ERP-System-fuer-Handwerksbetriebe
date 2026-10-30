package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto;
import org.example.kalkulationsprogramm.dto.Lager.UmbuchungEingabe;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Nur Dummy-Daten (Max Mustermann, Dummy-Strichcodes). */
@ExtendWith(MockitoExtension.class)
class LagerAppServiceTest {

    private static final String EAN = "4006381333931";   // gültige Prüfziffer

    @Mock private FunktionsRechteService rechte;
    @Mock private LagerService lager;
    @Mock private ArtikelRepository artikel;
    @Mock private ObjectProvider<LagerModulSchalterService> schalter;
    @Mock private LagerModulSchalterService modul;
    private LagerAppService service;
    private Mitarbeiter max;

    @BeforeEach
    void setUp() {
        service = new LagerAppService(rechte, lager, artikel, schalter);
        max = new Mitarbeiter();
        max.setId(5L);
        max.setVorname("Max");
        max.setNachname("Mustermann");
        org.mockito.Mockito.lenient().when(schalter.getIfAvailable()).thenReturn(modul);
        org.mockito.Mockito.lenient().when(modul.istAktiv()).thenReturn(true);
        org.mockito.Mockito.lenient().when(rechte.verlangeFuerToken(any(), eq(Funktion.LAGER), any())).thenReturn(max);
    }

    private static LagerBestandDto zeile(long id) {
        return new LagerBestandDto(id, "Schraube M8", "S-8", "Stk", new BigDecimal("40"), null, false, false, List.of(), null, null, null, null);
    }

    @Test
    @DisplayName("Häufig benutzt: die meistgebuchten Artikel des Mitarbeiters, mit dessen Namen wie im Lagerjournal")
    void haeufig() {
        given(lager.haeufigeArtikelIds("Max Mustermann (App)", LagerAppService.MAX_HAEUFIG)).willReturn(List.of(3L, 9L));
        given(lager.bestandFuer(List.of(3L, 9L))).willReturn(List.of(zeile(3), zeile(9)));

        var r = service.haeufig("t");

        assertThat(r).extracting(LagerBestandDto::artikelId).containsExactly(3L, 9L);
        org.mockito.Mockito.verify(rechte).verlangeFuerToken("t", Funktion.LAGER, FunktionsAktion.ANSEHEN);
    }

    @Test
    @DisplayName("Häufig benutzt ohne Recht „Lager ansehen“: abgelehnt, ohne das Lager zu fragen")
    void haeufigOhneRecht() {
        given(rechte.verlangeFuerToken(any(), eq(Funktion.LAGER), any())).willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Kein Recht"));

        assertThatThrownBy(() -> service.haeufig("t")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(lager);
    }

    @Test
    @DisplayName("Ausgeschaltetes Lager: 403 mit Klartext, ohne dass Rechte oder Lager gefragt werden")
    void lagerAus() {
        given(modul.istAktiv()).willReturn(false);

        assertThatThrownBy(() -> service.suche("t", "schraube")).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> { assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN); assertThat(e.getReason()).contains("ausgeschaltet"); });
        verifyNoInteractions(rechte, lager);
    }

    @Test
    @DisplayName("Ungültiger Zugang und fehlendes Recht kommen unverändert von der Rechteprüfung (401, 403)")
    void rechtePruefungGiltFuerJedenWeg() {
        given(rechte.verlangeFuerToken("falsch", Funktion.LAGER, FunktionsAktion.ANSEHEN))
                .willThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Der Zugang ist nicht gültig."));
        given(rechte.verlangeFuerToken("t", Funktion.LAGER, FunktionsAktion.ANLEGEN))
                .willThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Dafür fehlt die Berechtigung: Lager, Anlegen."));

        assertThatThrownBy(() -> service.orte("falsch")).hasMessageContaining("Zugang");
        assertThatThrownBy(() -> service.buche("t", new LagerBuchungEingabe("ABGANG", 1L, 1L, BigDecimal.ONE, null, null, null, null))).hasMessageContaining("Berechtigung");
        verify(lager, never()).buche(any(), any());
    }

    @Test
    @DisplayName("Orte: nur aktive Lagerorte")
    void orteNurAktive() {
        given(lager.orte()).willReturn(List.of(new LagerortDto(1L, "Werkstatt", null, true, 1, false, null), new LagerortDto(2L, "Alt", null, false, 2, false, null)));

        assertThat(service.orte("t")).extracting(LagerortDto::name).containsExactly("Werkstatt");
    }

    @Test
    @DisplayName("Suche: unter zwei Zeichen nichts; sonst im ganzen Artikelstamm (auch Artikel ohne Buchung) mit Bestand, Wörter und Strichcode werden verwendet")
    void suche() {
        assertThat(service.suche("t", " a ")).isEmpty();
        verify(artikel, never()).sucheFuerAssistent(any(), any(), any(), any(), any(), any());

        org.example.kalkulationsprogramm.domain.Artikel a1 = new ArtikelWerkstoffe();
        a1.setId(9L);
        org.example.kalkulationsprogramm.domain.Artikel a2 = new ArtikelWerkstoffe();
        a2.setId(10L);
        given(artikel.sucheFuerAssistent(eq("%schraube%"), eq("%m8%"), eq("%"), eq("%schraube m8%"), eq("SchraubeM8"), any())).willReturn(List.of(a1, a2));
        given(lager.bestandFuer(List.of(9L, 10L))).willReturn(List.of(zeile(9), zeile(10)));

        assertThat(service.suche("t", "  Schraube M8 ")).extracting(LagerBestandDto::artikelId).containsExactly(9L, 10L);
    }

    @Test
    @DisplayName("Suche ohne Treffer fragt das Lager gar nicht erst")
    void sucheOhneTreffer() {
        given(artikel.sucheFuerAssistent(any(), any(), any(), any(), any(), any())).willReturn(List.of());

        assertThat(service.suche("t", "gibtesnicht")).isEmpty();
        verify(lager, never()).bestandFuer(any());
    }

    @Test
    @DisplayName("Strichcode: bekannt liefert den Artikel mit Bestand, unbekannt meldet gefunden=false mit der bereinigten Nummer")
    void nachEan() {
        org.example.kalkulationsprogramm.domain.Artikel a = new ArtikelWerkstoffe();
        a.setId(9L);
        given(artikel.findByEan(EAN)).willReturn(Optional.of(a));
        given(artikel.findByEan("4012345678901")).willReturn(Optional.empty());
        given(lager.bestandVon(9L)).willReturn(zeile(9));

        LagerAppService.EanTreffer da = service.nachEan("t", " 4006381 333931 ");
        LagerAppService.EanTreffer weg = service.nachEan("t", "4012345678901");

        assertThat(da.gefunden()).isTrue();
        assertThat(da.artikel().artikelId()).isEqualTo(9L);
        assertThat(weg.gefunden()).isFalse();
        assertThat(weg.ean()).isEqualTo("4012345678901");
        assertThatThrownBy(() -> service.nachEan("t", "  ")).hasMessageContaining("Strichcode");
    }

    @Test
    @DisplayName("Entnahme und Zugang brauchen 'anlegen' und buchen unter dem Namen des Mitarbeiters mit dem Zusatz App")
    void entnahme() {
        LagerBuchungEingabe e = new LagerBuchungEingabe("ABGANG", 9L, 1L, new BigDecimal("3"), 77L, null, null, null);
        given(lager.buche(e, "Max Mustermann (App)")).willReturn(zeile(9));

        assertThat(service.buche("t", e).artikelId()).isEqualTo(9L);
        verify(rechte).verlangeFuerToken("t", Funktion.LAGER, FunktionsAktion.ANLEGEN);
    }

    @Test
    @DisplayName("Inventur (Korrektur) und Umbuchung brauchen 'ändern'")
    void inventurUndUmbuchung() {
        LagerBuchungEingabe zaehlung = new LagerBuchungEingabe("korrektur", 9L, 1L, new BigDecimal("12"), null, null, null, null);
        UmbuchungEingabe um = new UmbuchungEingabe(9L, 1L, 2L, new BigDecimal("2"), null);
        given(lager.buche(zaehlung, "Max Mustermann (App)")).willReturn(zeile(9));
        given(lager.umbuche(um, "Max Mustermann (App)")).willReturn(zeile(9));

        service.buche("t", zaehlung);
        service.umbuche("t", um);

        verify(rechte, org.mockito.Mockito.times(2)).verlangeFuerToken("t", Funktion.LAGER, FunktionsAktion.AENDERN);
    }

    @Test
    @DisplayName("Mitarbeiter ohne Namen werden mit Nummer gebucht")
    void ohneName() {
        Mitarbeiter leer = new Mitarbeiter();
        leer.setId(8L);
        given(rechte.verlangeFuerToken("t", Funktion.LAGER, FunktionsAktion.ANLEGEN)).willReturn(leer);
        LagerBuchungEingabe e = new LagerBuchungEingabe("ZUGANG", 9L, 1L, BigDecimal.TEN, null, null, null, null);
        given(lager.buche(e, "Mitarbeiter 8 (App)")).willReturn(zeile(9));

        assertThat(service.buche("t", e)).isNotNull();
    }

    @Test
    @DisplayName("Strichcode zuordnen: gültige, freie Nummer wird gespeichert und der Suchtext neu gebaut")
    void eanZuordnen() {
        org.example.kalkulationsprogramm.domain.Artikel a = new ArtikelWerkstoffe();
        a.setId(9L);
        a.setProduktname("Schraube M8");
        given(artikel.findById(9L)).willReturn(Optional.of(a));
        given(artikel.findByEan(EAN)).willReturn(Optional.empty());
        given(lager.bestandVon(9L)).willReturn(zeile(9));

        service.eanZuordnen("t", 9L, " " + EAN + " ");

        assertThat(a.getEan()).isEqualTo(EAN);
        assertThat(a.getSuchtext()).contains(EAN);
        verify(artikel).save(a);
        verify(rechte).verlangeFuerToken("t", Funktion.LAGER, FunktionsAktion.AENDERN);
    }

    @Test
    @DisplayName("Strichcode zuordnen: falsche Prüfziffer 400, schon vergeben 409, unbekannter Artikel 404, nichts wird gespeichert")
    void eanZuordnenFehler() {
        assertThatThrownBy(() -> service.eanZuordnen("t", 9L, "4006381333930")).hasMessageContaining("Prüfziffer");
        assertThatThrownBy(() -> service.eanZuordnen("t", 9L, "ABC")).hasMessageContaining("Ziffern");

        org.example.kalkulationsprogramm.domain.Artikel anderer = new ArtikelWerkstoffe();
        anderer.setId(4L);
        org.example.kalkulationsprogramm.domain.Artikel a = new ArtikelWerkstoffe();
        a.setId(9L);
        given(artikel.findById(9L)).willReturn(Optional.of(a));
        given(artikel.findByEan(EAN)).willReturn(Optional.of(anderer));
        assertThatThrownBy(() -> service.eanZuordnen("t", 9L, EAN)).hasMessageContaining("schon zu einem anderen Artikel");

        given(artikel.findById(404L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.eanZuordnen("t", 404L, EAN)).hasMessageContaining("gibt es nicht");
        verify(artikel, never()).save(any());
    }

    @Test
    @DisplayName("Derselbe Artikel darf seine eigene Nummer noch einmal bekommen")
    void eigeneNummerIstErlaubt() {
        org.example.kalkulationsprogramm.domain.Artikel a = new ArtikelWerkstoffe();
        a.setId(9L);
        given(artikel.findById(9L)).willReturn(Optional.of(a));
        given(artikel.findByEan(EAN)).willReturn(Optional.of(a));
        given(lager.bestandVon(9L)).willReturn(zeile(9));

        assertThat(service.eanZuordnen("t", 9L, EAN)).isNotNull();
    }
}
