package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AktivitaetEreignis;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.dto.Aktivitaet.AktivitaetDto;
import org.example.kalkulationsprogramm.repository.AktivitaetEreignisRepository;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

/** Nur Dummy-Daten (Max Mustermann, Muster GmbH). */
@ExtendWith(MockitoExtension.class)
class AktivitaetServiceTest {

    @Mock AktivitaetEreignisRepository ereignisse;
    @Mock SystemSettingsService einstellungen;
    @Mock ProjektRepository projekte;
    @Mock KundeRepository kunden;
    @Mock LieferantenRepository lieferanten;
    @Mock ArtikelRepository artikel;
    @Mock AnfrageRepository anfragen;
    @Mock MitarbeiterRepository mitarbeiter;

    AktivitaetService service;
    String mitarbeiterAktiv = "false";
    String tage = "365";

    @BeforeEach
    void setUp() {
        service = new AktivitaetService(ereignisse, einstellungen, projekte, kunden, lieferanten, artikel, anfragen, mitarbeiter);
        lenient().when(einstellungen.get(eq(AktivitaetService.SCHLUESSEL_MITARBEITER), anyString())).thenAnswer(a -> mitarbeiterAktiv);
        lenient().when(einstellungen.get(eq(AktivitaetService.SCHLUESSEL_TAGE), anyString())).thenAnswer(a -> tage);
    }

    private AktivitaetEreignis gespeichert() {
        ArgumentCaptor<AktivitaetEreignis> c = ArgumentCaptor.forClass(AktivitaetEreignis.class);
        verify(ereignisse).save(c.capture());
        return c.getValue();
    }

    private static AktivitaetKatalog.Regel regel(String methode, String muster) {
        return AktivitaetKatalog.finde(methode, muster).orElseThrow();
    }

    // ---------------------------------------------------------------- Schreiben

    @Test
    @DisplayName("Administrator wird immer protokolliert: Art, Satz, Objekt mit Name, Uhrzeit und Benutzer; kein Inhalt")
    void adminWirdProtokolliert() {
        Projekt p = new Projekt();
        p.setAuftragsnummer("2026/014");
        p.setBauvorhaben("Anbau Halle");
        when(projekte.findById(7L)).thenReturn(Optional.of(p));

        service.erfasse(1L, "Max Mustermann", true, regel("PUT", "/api/projekte/{id}"), 7L);

        AktivitaetEreignis e = gespeichert();
        assertThat(e.getProfilId()).isEqualTo(1L);
        assertThat(e.getBenutzer()).isEqualTo("Max Mustermann");
        assertThat(e.getArt()).isEqualTo("GEAENDERT");
        assertThat(e.getBeschreibung()).isEqualTo("Projekt geändert");
        assertThat(e.getObjektTyp()).isEqualTo("Projekt");
        assertThat(e.getObjektId()).isEqualTo(7L);
        assertThat(e.getObjektName()).isEqualTo("2026/014 Anbau Halle");
        assertThat(e.getZeitpunkt()).isBetween(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1));
    }

    @Test
    @DisplayName("Mitarbeiter werden nur bei eingeschaltetem Schalter protokolliert (Vorgabe aus)")
    void mitarbeiterNurMitSchalter() {
        service.erfasse(2L, "Erika Musterfrau", false, regel("POST", "/api/kunden"), null);
        verify(ereignisse, never()).save(any());
        assertThat(service.protokolliertFuer(false)).isFalse();

        mitarbeiterAktiv = "true";
        service.erfasse(2L, "Erika Musterfrau", false, regel("POST", "/api/kunden"), null);

        assertThat(gespeichert().getArt()).isEqualTo("ANGELEGT");
        assertThat(service.protokolliertFuer(false)).isTrue();
    }

    @Test
    @DisplayName("Ohne Objektnummer oder ohne Objektart steht kein Name; ein unbekanntes Objekt ergibt keinen Namen, aber das Ereignis")
    void ohneObjekt() {
        service.erfasse(1L, "Admin", true, regel("POST", "/api/projekte"), null);
        assertThat(gespeichert().getObjektId()).isNull();
        assertThat(gespeichert().getObjektName()).isNull();
    }

    @Test
    @DisplayName("Namen für Kunde und Projekt unbekannt oder Lesefehler: das Ereignis wird trotzdem festgehalten")
    void namenFehlen() {
        when(kunden.findById(5L)).thenReturn(Optional.empty());
        service.erfasse(1L, "Admin", true, regel("PUT", "/api/kunden/{id}"), 5L);

        AktivitaetEreignis e = gespeichert();
        assertThat(e.getObjektId()).isEqualTo(5L);
        assertThat(e.getObjektName()).isNull();
    }

    @Test
    @DisplayName("Lesefehler bei der Namenssuche stören das Protokollieren nicht")
    void lesefehler() {
        when(kunden.findById(5L)).thenThrow(new IllegalStateException("kaputt"));
        service.erfasse(1L, "Admin", true, regel("PUT", "/api/kunden/{id}"), 5L);

        assertThat(gespeichert().getObjektName()).isNull();
    }

    @Test
    @DisplayName("Kundenname wird als Objektname übernommen")
    void kundenname() {
        Kunde k = new Kunde();
        k.setName("Muster GmbH");
        when(kunden.findById(5L)).thenReturn(Optional.of(k));
        service.erfasse(1L, "Admin", true, regel("PUT", "/api/kunden/{id}"), 5L);

        assertThat(gespeichert().getObjektName()).isEqualTo("Muster GmbH");
    }

    // ---------------------------------------------------------------- Lesen

    private AktivitaetEreignis ereignis(Long id, String benutzer, LocalDateTime zeit) {
        AktivitaetEreignis e = new AktivitaetEreignis();
        e.setId(id);
        e.setBenutzer(benutzer);
        e.setZeitpunkt(zeit);
        e.setArt("GEAENDERT");
        e.setBeschreibung("Projekt geändert");
        e.setObjektTyp("Projekt");
        e.setObjektId(7L);
        return e;
    }

    @Test
    @DisplayName("Mein Tagesverlauf: Ereignisse des gewählten Tages, ohne Benutzername; sagt, ob für diese Person protokolliert wird")
    void meinTag() {
        LocalDate tag = LocalDate.of(2026, 10, 3);
        when(ereignisse.findByProfilIdAndZeitpunktGreaterThanEqualAndZeitpunktLessThanOrderByZeitpunktAsc(2L, tag.atStartOfDay(), tag.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(ereignis(1L, "Erika Musterfrau", tag.atTime(8, 15))));

        AktivitaetDto.MeinTag d = service.mein(2L, false, tag);

        assertThat(d.datum()).isEqualTo(tag);
        assertThat(d.aktiv()).isFalse();
        assertThat(d.aufbewahrungTage()).isEqualTo(365);
        assertThat(d.ereignisse()).hasSize(1);
        assertThat(d.ereignisse().get(0).benutzer()).isNull();
        assertThat(d.ereignisse().get(0).text()).isEqualTo("Projekt geändert");
        assertThat(service.mein(1L, true, tag).aktiv()).isTrue();
    }

    @Test
    @DisplayName("Ohne Datum gilt heute; ohne Profil eine leere Liste")
    void meinTagVorgaben() {
        assertThat(service.mein(null, true, null).ereignisse()).isEmpty();
        assertThat(service.mein(null, true, null).datum()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("Aktivitäten im System: Filter werden weitergereicht (Art großgeschrieben, leere Filter fallen weg), Tage und Anzahl sind begrenzt, mit Benutzername")
    void imSystem() {
        when(ereignisse.suche(any(), any(), any(), any(), any())).thenReturn(List.of(ereignis(1L, "Max Mustermann", LocalDateTime.now())));

        List<AktivitaetDto.Ereignis> liste = service.imSystem(" Projekt ", 7L, " versendet ", 99_999, 100_000);

        assertThat(liste.get(0).benutzer()).isEqualTo("Max Mustermann");
        ArgumentCaptor<LocalDateTime> ab = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<Pageable> seite = ArgumentCaptor.forClass(Pageable.class);
        verify(ereignisse).suche(ab.capture(), eq("Projekt"), eq(7L), eq("VERSENDET"), seite.capture());
        assertThat(ab.getValue()).isBefore(LocalDateTime.now().minusDays(36_000));
        assertThat(seite.getValue().getPageSize()).isEqualTo(500);

        service.imSystem("", null, " ", null, null);
        verify(ereignisse).suche(any(), eq(null), eq(null), eq(null), org.mockito.ArgumentMatchers.argThat(p -> p.getPageSize() == 200));
    }

    // ---------------------------------------------------------------- Einstellungen

    @Test
    @DisplayName("Aufbewahrung: Vorgabe 365, 0 = unbegrenzt, ungültige oder zu große Werte fallen auf 365 zurück")
    void aufbewahrung() {
        assertThat(service.aufbewahrungTage()).isEqualTo(365);
        tage = "0";
        assertThat(service.aufbewahrungTage()).isZero();
        tage = "90";
        assertThat(service.aufbewahrungTage()).isEqualTo(90);
        tage = "kaputt";
        assertThat(service.aufbewahrungTage()).isEqualTo(365);
        tage = "-5";
        assertThat(service.aufbewahrungTage()).isEqualTo(365);
        tage = "99999";
        assertThat(service.aufbewahrungTage()).isEqualTo(365);
        tage = null;
        assertThat(service.aufbewahrungTage()).isEqualTo(365);
    }

    @Test
    @DisplayName("Einstellungen speichern: nur die gesetzten Werte, 0 bis 36500 Tage erlaubt")
    void einstellungenSpeichern() {
        when(ereignisse.count()).thenReturn(12L);

        AktivitaetDto.Einstellungen e = service.speichereEinstellungen(true, 30);

        verify(einstellungen).save(eq(AktivitaetService.SCHLUESSEL_TAGE), eq("30"), anyString());
        verify(einstellungen).save(eq(AktivitaetService.SCHLUESSEL_MITARBEITER), eq("true"), anyString());
        assertThat(e.ereignisseGesamt()).isEqualTo(12L);
        service.speichereEinstellungen(null, 0);
        verify(einstellungen).save(eq(AktivitaetService.SCHLUESSEL_TAGE), eq("0"), anyString());
        assertThatThrownBy(() -> service.speichereEinstellungen(null, -1)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("unbegrenzt");
        assertThatThrownBy(() -> service.speichereEinstellungen(null, 36_501)).isInstanceOf(ResponseStatusException.class);
    }

    // ---------------------------------------------------------------- Aufbewahrung, Auskunft, Löschung

    @Test
    @DisplayName("Die nächtliche Bereinigung löscht alles, was älter als die Aufbewahrung ist; bei „unbegrenzt“ nichts")
    void bereinigung() {
        tage = "30";
        service.bereinige();
        ArgumentCaptor<LocalDateTime> grenze = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(ereignisse).loescheAelterAls(grenze.capture());
        assertThat(grenze.getValue()).isBetween(LocalDateTime.now().minusDays(30).minusMinutes(1), LocalDateTime.now().minusDays(30).plusMinutes(1));

        org.mockito.Mockito.clearInvocations(ereignisse);
        tage = "0";
        service.bereinige();
        verify(ereignisse, never()).loescheAelterAls(any());
    }

    @Test
    @DisplayName("Auskunft und Löschung einer Person")
    void auskunftUndLoeschung() {
        when(ereignisse.findByProfilIdOrderByZeitpunktAsc(2L)).thenReturn(List.of(ereignis(1L, "Erika Musterfrau", LocalDateTime.now())));
        when(ereignisse.loescheVon(2L)).thenReturn(5);

        assertThat(service.auskunft(2L)).hasSize(1);
        assertThat(service.loescheVon(2L)).isEqualTo(5);
    }
}
