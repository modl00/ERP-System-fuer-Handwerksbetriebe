package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.LagerModulSchalterService;
import org.example.kalkulationsprogramm.service.LagerService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class KiWerkzeugeTest {

    private final ObjectMapper json = new ObjectMapper();
    private final KiKontext anna = new KiKontext(1L, "anna", false);

    private JsonNode suche(String s) {
        return json.createObjectNode().put("suche", s);
    }

    // ------------------------------------------------------------------ Hilfe

    @Test
    void suchbegriffWirdGeprueftUndBegrenzt() {
        assertThatThrownBy(() -> WerkzeugHilfe.suchbegriff(json.createObjectNode())).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("Suchbegriff");
        assertThatThrownBy(() -> WerkzeugHilfe.suchbegriff(suche("   "))).isInstanceOf(KiWerkzeugFehler.class);
        assertThatThrownBy(() -> WerkzeugHilfe.suchbegriff(null)).isInstanceOf(KiWerkzeugFehler.class);
        assertThat(WerkzeugHilfe.suchbegriff(suche("a".repeat(500)))).hasSize(WerkzeugHilfe.MAX_SUCHBEGRIFF);
        assertThat(WerkzeugHilfe.suchbegriff(suche("rund\nrohr\t42"))).isEqualTo("rund rohr 42");
    }

    @Test
    void dasSchemaVerlangtDieSuche() {
        JsonNode s = new KundenSuchenWerkzeug(mock(KundeRepository.class)).parameter();
        assertThat(s.path("type").asText()).isEqualTo("object");
        assertThat(s.path("required").get(0).asText()).isEqualTo("suche");
        assertThat(s.path("properties").path("suche").path("type").asText()).isEqualTo("string");
    }

    // ------------------------------------------------------------------ Artikel

    private Artikel artikel(long id, String name, String nummer, String ean) {
        Artikel a = new Artikel();
        ReflectionTestUtils.setField(a, "id", id);
        a.setProduktname(name);
        a.setArtikelnummer(nummer);
        a.setEan(ean);
        a.setVerrechnungseinheit(Verrechnungseinheit.LAUFENDE_METER);
        return a;
    }

    @Test
    void artikelSucheBautDieSuchwoerterUndFormatiertTreffer() {
        ArtikelRepository repo = mock(ArtikelRepository.class);
        when(repo.sucheFuerAssistent(any(), any(), any(), any(), any(), any())).thenReturn(List.of(artikel(7, "Rundrohr 42,4 x 2", "ST-RR-042", "4006381333931")));
        var w = new ArtikelSuchenWerkzeug(repo);

        String text = w.ausfuehren(suche("Rundrohr 42,4 x 2"), anna);

        assertThat(text).contains("ID 7").contains("Rundrohr 42,4 x 2").contains("Nr. ST-RR-042").contains("EAN 4006381333931").contains("Laufende Meter");
        ArgumentCaptor<String> w1 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> w2 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> w3 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ganz = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ean = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Pageable> seite = ArgumentCaptor.forClass(Pageable.class);
        verify(repo).sucheFuerAssistent(w1.capture(), w2.capture(), w3.capture(), ganz.capture(), ean.capture(), seite.capture());
        assertThat(w1.getValue()).isEqualTo("%rundrohr%");
        assertThat(w2.getValue()).isEqualTo("%42,4%");
        assertThat(w3.getValue()).isEqualTo("%2%");
        assertThat(ganz.getValue()).isEqualTo("%rundrohr 42,4 x 2%");
        assertThat(ean.getValue()).isEqualTo("Rundrohr42,4x2");
        assertThat(seite.getValue().getPageSize()).isEqualTo(ArtikelSuchenWerkzeug.MAX_TREFFER);
    }

    @Test
    void artikelSucheOhneTrefferSagtEsEhrlich() {
        ArtikelRepository repo = mock(ArtikelRepository.class);
        when(repo.sucheFuerAssistent(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        assertThat(new ArtikelSuchenWerkzeug(repo).ausfuehren(suche("gibtesnicht"), anna)).isEqualTo("Kein Artikel gefunden für \"gibtesnicht\".");
    }

    @Test
    void sonderzeichenInDerSucheWerdenNurAlsTextBehandelt() {
        ArtikelRepository repo = mock(ArtikelRepository.class);
        when(repo.sucheFuerAssistent(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        String text = new ArtikelSuchenWerkzeug(repo).ausfuehren(suche("'; DROP TABLE artikel; --"), anna);

        assertThat(text).startsWith("Kein Artikel gefunden");
    }

    // ------------------------------------------------------------------ Kunden

    @Test
    void kundenSucheZeigtNichtMehrAlsNoetig() {
        KundeRepository repo = mock(KundeRepository.class);
        Kunde k = new Kunde();
        ReflectionTestUtils.setField(k, "id", 3L);
        k.setName("Max Mustermann GmbH");
        k.setKundennummer("K-100");
        k.setOrt("Musterstadt");
        k.setTelefon("0931 123456");
        k.setStrasse("Musterweg 1");
        when(repo.searchByNameOrAnsprechpartnerOrEmail("muster")).thenReturn(List.of(k));

        String text = new KundenSuchenWerkzeug(repo).ausfuehren(suche("muster"), anna);

        assertThat(text).contains("ID 3").contains("Max Mustermann GmbH").contains("Kundennr. K-100").contains("Musterstadt").contains("Tel. 0931 123456");
        assertThat(text).doesNotContain("Musterweg");
    }

    @Test
    void kundenSucheBegrenztDieTreffer() {
        KundeRepository repo = mock(KundeRepository.class);
        List<Kunde> viele = new java.util.ArrayList<>();
        for (int i = 0; i < 15; i++) {
            Kunde k = new Kunde();
            ReflectionTestUtils.setField(k, "id", (long) i);
            k.setName("Kunde " + i);
            viele.add(k);
        }
        when(repo.searchByNameOrAnsprechpartnerOrEmail("kunde")).thenReturn(viele);

        String text = new KundenSuchenWerkzeug(repo).ausfuehren(suche("kunde"), anna);

        assertThat(text).contains("Kunde 9").doesNotContain("Kunde 10").contains("bitte genauer suchen");
    }

    @Test
    void kundenSucheOhneTreffer() {
        KundeRepository repo = mock(KundeRepository.class);
        when(repo.searchByNameOrAnsprechpartnerOrEmail(any())).thenReturn(List.of());
        assertThat(new KundenSuchenWerkzeug(repo).ausfuehren(suche("x"), anna)).startsWith("Kein Kunde gefunden");
    }

    // ------------------------------------------------------------------ Lager

    @SuppressWarnings("unchecked")
    private LagerbestandWerkzeug lager(boolean modulAn, boolean darf, LagerService service) {
        FunktionsRechteService rechte = mock(FunktionsRechteService.class);
        when(rechte.darfProfil(1L, Funktion.LAGER, FunktionsAktion.ANSEHEN)).thenReturn(darf);
        ObjectProvider<LagerModulSchalterService> modul = mock(ObjectProvider.class);
        LagerModulSchalterService m = mock(LagerModulSchalterService.class);
        when(m.istAktiv()).thenReturn(modulAn);
        when(modul.getIfAvailable()).thenReturn(m);
        return new LagerbestandWerkzeug(service, rechte, modul);
    }

    @Test
    void lagerWerkzeugIstNurMitRechtUndEingeschaltetemModulVerfuegbar() {
        LagerService s = mock(LagerService.class);
        assertThat(lager(true, true, s).verfuegbar(anna)).isTrue();
        assertThat(lager(true, false, s).verfuegbar(anna)).as("ohne Recht Sehen").isFalse();
        assertThat(lager(false, true, s).verfuegbar(anna)).as("Modul aus").isFalse();
        assertThat(lager(true, false, s).verfuegbar(new KiKontext(9L, "chef", true))).as("Admin").isTrue();
        assertThatThrownBy(() -> lager(true, false, s).ausfuehren(suche("x"), anna)).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("Berechtigung");
        verify(s, org.mockito.Mockito.never()).bestand(any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void lagerWerkzeugZeigtBestandOrteUndWarnungen() {
        LagerService s = mock(LagerService.class);
        when(s.bestand(eq("rohr"), eq(false))).thenReturn(List.of(
                new LagerBestandDto(7L, "Rundrohr 42,4", "ST-RR-042", "m", new BigDecimal("12.500"), new BigDecimal("20"), true, false,
                        List.of(new LagerBestandDto.OrtBestand(1L, "Hauptlager", new BigDecimal("12.500"))), null, null, null, null),
                new LagerBestandDto(8L, "Flachstahl", null, "m", new BigDecimal("-2"), null, false, true, List.of(), null, null, null, null)));

        String text = lager(true, true, s).ausfuehren(suche("rohr"), anna);

        assertThat(text).contains("ID 7").contains("Bestand 12.5 m").contains("Mindestbestand 20").contains("unter dem Mindestbestand").contains("Hauptlager 12.5");
        assertThat(text).contains("ID 8").contains("ACHTUNG: negativ");
    }

    @Test
    void lagerWerkzeugOhneTrefferErklaertWarum() {
        LagerService s = mock(LagerService.class);
        when(s.bestand(any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(List.of());

        assertThat(lager(true, true, s).ausfuehren(suche("x"), anna)).contains("keinen Artikel").contains("Nur Artikel mit Buchung");
    }

    @Test
    void alleDreiWerkzeugeSindAuskuenfteOhneAenderung() {
        assertThat(new ArtikelSuchenWerkzeug(mock(ArtikelRepository.class)).aendert()).isFalse();
        assertThat(new KundenSuchenWerkzeug(mock(KundeRepository.class)).aendert()).isFalse();
        assertThat(lager(true, true, mock(LagerService.class)).aendert()).isFalse();
        assertThat(Optional.of(new ArtikelSuchenWerkzeug(mock(ArtikelRepository.class)).name())).contains("artikel_suchen");
    }
}
