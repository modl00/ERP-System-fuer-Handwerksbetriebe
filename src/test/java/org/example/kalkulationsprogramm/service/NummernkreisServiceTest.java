package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.Nummernkreis;
import org.example.kalkulationsprogramm.domain.Nummernkreis.Datumsteil;
import org.example.kalkulationsprogramm.domain.Nummernkreis.Ruecksetzung;
import org.example.kalkulationsprogramm.domain.NummernkreisZaehler;
import org.example.kalkulationsprogramm.repository.AusgangsGeschaeftsDokumentRepository;
import org.example.kalkulationsprogramm.repository.NummernkreisRepository;
import org.example.kalkulationsprogramm.repository.NummernkreisZaehlerRepository;
import org.example.kalkulationsprogramm.service.NummernkreisService.Eingabe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class NummernkreisServiceTest {

    NummernkreisRepository kreise = mock(NummernkreisRepository.class);
    NummernkreisZaehlerRepository zaehler = mock(NummernkreisZaehlerRepository.class);
    AusgangsGeschaeftsDokumentRepository dokumente = mock(AusgangsGeschaeftsDokumentRepository.class);
    NummernkreisService service = new NummernkreisService(kreise, zaehler, dokumente);
    final LocalDate heute = LocalDate.now();
    NummernkreisZaehler gespeicherterZaehler;

    private Nummernkreis kreis(String art, String praefix, Datumsteil d, Ruecksetzung r, int stellen, long start) {
        Nummernkreis k = new Nummernkreis();
        k.setArt(art);
        k.setPraefix(praefix);
        k.setDatumsteil(d);
        k.setRuecksetzung(r);
        k.setStellen(stellen);
        k.setStartnummer(start);
        when(kreise.findByArt(art)).thenReturn(Optional.of(k));
        return k;
    }

    @BeforeEach
    void setUp() {
        when(kreise.findByArt(any())).thenReturn(Optional.empty());
        when(kreise.save(any(Nummernkreis.class))).thenAnswer(a -> a.getArgument(0));
        when(zaehler.findeMitSperre(any(), any())).thenReturn(Optional.empty());
        when(zaehler.findByArtAndPeriode(any(), any())).thenReturn(Optional.empty());
        when(zaehler.save(any(NummernkreisZaehler.class))).thenAnswer(a -> { gespeicherterZaehler = a.getArgument(0); return gespeicherterZaehler; });
        when(dokumente.findeNummernDerArt(any())).thenReturn(List.of());
    }

    @Test
    void ohneEingestelltesFormatGiltDasBisherigeUndEsWirdNichtsGezaehlt() {
        assertThat(service.naechsteNummer("RECHNUNG", n -> false)).isEmpty();
        verify(zaehler, never()).save(any());
        var rechnung = service.alle().stream().filter(z -> z.art().equals("RECHNUNG")).findFirst().orElseThrow();
        assertThat(rechnung.eigenesFormat()).isFalse();
        assertThat(rechnung.naechsteNummer()).contains("RE-" + heute.getYear()).contains("(bisheriges Format)");
        assertThat(rechnung.ruecksetzung()).as("Vorschlag: Rechnungen laufen ohne Rückstellung").isEqualTo(Ruecksetzung.NIE);
        assertThat(service.alle()).hasSize(10);
    }

    @Test
    void formatiertMitPraefixDatumsteilUndStellen() {
        kreis("RECHNUNG", "RE", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 1);
        assertThat(service.naechsteNummer("RECHNUNG", n -> false)).contains("RE-" + heute.getYear() + "-0001");

        kreis("ANGEBOT", "AG", Datumsteil.JAHR_MONAT, Ruecksetzung.MONATLICH, 5, 1);
        assertThat(service.naechsteNummer("ANGEBOT", n -> false)).contains(String.format("AG-%d-%02d-00001", heute.getYear(), heute.getMonthValue()));

        kreis("GUTSCHRIFT", "GU", Datumsteil.KEINER, Ruecksetzung.NIE, 3, 1);
        assertThat(service.naechsteNummer("GUTSCHRIFT", n -> false)).contains("GU-001");
    }

    @Test
    void zaehltLueckenlosHochUndBeginntBeiDerStartnummer() {
        kreis("RECHNUNG", "RE", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 1000);
        NummernkreisZaehler z = new NummernkreisZaehler();
        z.setLetzte(1004);
        when(zaehler.findeMitSperre("RECHNUNG", "alle")).thenReturn(Optional.of(z));

        assertThat(service.naechsteNummer("RECHNUNG", n -> false)).contains("RE-" + heute.getYear() + "-1005");
        assertThat(z.getLetzte()).isEqualTo(1005);
        assertThat(service.naechsteNummer("RECHNUNG", n -> false)).contains("RE-" + heute.getYear() + "-1006");

        // Die Startnummer ist die kleinste: später erhöht, springt der Zähler dorthin
        kreis("RECHNUNG", "RE", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 2000);
        assertThat(service.naechsteNummer("RECHNUNG", n -> false)).contains("RE-" + heute.getYear() + "-2000");
    }

    @Test
    void dieRueckstellungBestimmtDenZeitraumDesZaehlers() {
        kreis("RECHNUNG", "RE", Datumsteil.JAHR, Ruecksetzung.JAEHRLICH, 4, 1);
        kreis("ANGEBOT", "AG", Datumsteil.JAHR, Ruecksetzung.MONATLICH, 4, 1);
        kreis("STORNO", "ST", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 1);

        service.naechsteNummer("RECHNUNG", n -> false);
        service.naechsteNummer("ANGEBOT", n -> false);
        service.naechsteNummer("STORNO", n -> false);

        verify(zaehler).findeMitSperre("RECHNUNG", String.valueOf(heute.getYear()));
        verify(zaehler).findeMitSperre("ANGEBOT", String.format("%d-%02d", heute.getYear(), heute.getMonthValue()));
        verify(zaehler).findeMitSperre("STORNO", "alle");
    }

    @Test
    void schonVergebeneNummernWerdenUebersprungen() {
        kreis("RECHNUNG", "RE", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 1);
        String eins = "RE-" + heute.getYear() + "-0001";
        String zwei = "RE-" + heute.getYear() + "-0002";

        var nummer = service.naechsteNummer("RECHNUNG", n -> n.equals(eins) || n.equals(zwei));

        assertThat(nummer).contains("RE-" + heute.getYear() + "-0003");
        assertThat(gespeicherterZaehler.getLetzte()).isEqualTo(3);
    }

    @Test
    void speichernPrueftPraefixStellenUndStartnummer() {
        assertThatThrownBy(() -> service.speichern("RECHNUNG", new Eingabe("R E", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 1L))).hasMessageContaining("Präfix");
        assertThatThrownBy(() -> service.speichern("RECHNUNG", new Eingabe("", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 1L))).hasMessageContaining("Präfix");
        assertThatThrownBy(() -> service.speichern("RECHNUNG", new Eingabe("RE", Datumsteil.JAHR, Ruecksetzung.NIE, 2, 1L))).hasMessageContaining("3 und 8");
        assertThatThrownBy(() -> service.speichern("RECHNUNG", new Eingabe("RE", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 10000L))).hasMessageContaining("4 Stellen");
        assertThatThrownBy(() -> service.speichern("RECHNUNG", new Eingabe("RE", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 0L))).hasMessageContaining("mindestens 1");
        assertThatThrownBy(() -> service.speichern("RECHNUNG", new Eingabe("RE", null, Ruecksetzung.NIE, 4, 1L))).hasMessageContaining("Datumsteil");
        assertThatThrownBy(() -> service.speichern("QUATSCH", new Eingabe("RE", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 1L))).isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(kreise, never()).save(any());
    }

    @Test
    void speichernLegtAnUndWarntWennNummernDesPraefixesSchonWeiterSind() {
        when(dokumente.findeNummernDerArt(AusgangsGeschaeftsDokumentTyp.RECHNUNG)).thenReturn(List.of("RE-2026/10/00001", "RE-2026-0042", "XX-2026-0099"));

        var z = service.speichern("RECHNUNG", new Eingabe(" RE ", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 10L));

        assertThat(z.eigenesFormat()).isTrue();
        assertThat(z.praefix()).isEqualTo("RE");
        assertThat(z.warnung()).contains("bis 42").contains("überspringt");
        assertThat(z.naechsteNummer()).isEqualTo("RE-" + heute.getYear() + "-0010");

        var ohneWarnung = service.speichern("RECHNUNG", new Eingabe("RE", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 100L));
        assertThat(ohneWarnung.warnung()).isNull();
    }

    @Test
    void zuruecksetzenLoeschtNurDasFormat() {
        Nummernkreis k = kreis("RECHNUNG", "RE", Datumsteil.JAHR, Ruecksetzung.NIE, 4, 1);

        service.zuruecksetzen("RECHNUNG");

        verify(kreise).delete(k);
        verify(zaehler, never()).deleteAll();
    }
}
