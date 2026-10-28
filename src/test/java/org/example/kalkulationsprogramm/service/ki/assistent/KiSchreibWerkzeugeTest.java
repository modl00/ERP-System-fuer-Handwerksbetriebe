package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelCreateDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.dto.Lager.LagerortDto;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.service.ArtikelService;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.LagerModulSchalterService;
import org.example.kalkulationsprogramm.service.LagerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Nur Dummy-Daten. Die Werkzeuge ändern nur als Vorschlag mit Bestätigung; geprüft werden Eingaben, Vorschau, Rechte und der richtige Aufruf der Dienste. */
class KiSchreibWerkzeugeTest {

    private final ObjectMapper json = new ObjectMapper();
    private final KiKontext anna = new KiKontext(1L, "anna", false);
    private final KiKontext chef = new KiKontext(2L, "chef", true);

    private ObjectNode angaben() {
        return json.createObjectNode();
    }

    // ------------------------------------------------------------------ Artikel anlegen

    private ArtikelService artikelService;
    private ArtikelRepository artikelRepo;
    private LieferantenRepository lieferantenRepo;
    private ArtikelAnlegenWerkzeug artikelWerkzeug;
    private Lieferanten stahl;

    @BeforeEach
    void setUp() {
        artikelService = mock(ArtikelService.class);
        artikelRepo = mock(ArtikelRepository.class);
        lieferantenRepo = mock(LieferantenRepository.class);
        artikelWerkzeug = new ArtikelAnlegenWerkzeug(artikelService, artikelRepo, lieferantenRepo);
        stahl = new Lieferanten();
        stahl.setId(3L);
        stahl.setLieferantenname("Stahlhandel Muster");
        when(lieferantenRepo.findById(3L)).thenReturn(Optional.of(stahl));
        when(artikelRepo.sucheFuerAssistent(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(List.of());
    }

    private ObjectNode rohr() {
        return angaben().put("bezeichnung", "Rundrohr 42,4 x 2,0 S235").put("einheit", "LAUFENDE_METER");
    }

    @Test
    @DisplayName("Artikel anlegen ist eine Änderung (nur mit Bestätigung), für jeden Benutzer da, das Schema verlangt Bezeichnung und Einheit")
    void artikelGrundlagen() {
        assertThat(artikelWerkzeug.aendert()).isTrue();
        assertThat(artikelWerkzeug.verfuegbar(anna)).isTrue();
        assertThat(artikelWerkzeug.name()).isEqualTo("artikel_anlegen");
        JsonNode s = artikelWerkzeug.parameter();
        assertThat(s.path("required")).extracting(JsonNode::asText).containsExactly("bezeichnung", "einheit");
        assertThat(s.path("properties").path("einheit").path("enum")).extracting(JsonNode::asText).contains("STUECK", "LAUFENDE_METER", "KILOGRAMM");
    }

    @Test
    @DisplayName("Vorschau nennt alle Werte in verständlicher Sprache, nichts wird angelegt")
    void artikelVorschau() {
        String v = artikelWerkzeug.vorschau(rohr().put("lieferantId", 3).put("artikelnummerLieferant", "R-4224").put("preis", "4,5").put("ean", "4006381333931"));

        assertThat(v).isEqualTo("Artikel anlegen: \"Rundrohr 42,4 x 2,0 S235\", Einheit Laufende Meter, EAN 4006381333931, Lieferant Stahlhandel Muster (dessen Nummer R-4224), Preis 4,5 € netto je Laufende Meter");
        verify(artikelService, never()).erstelleArtikel(any());
    }

    @Test
    @DisplayName("Ähnliche Artikel werden in der Vorschau genannt, damit nichts doppelt angelegt wird")
    void artikelAehnliche() {
        Artikel a = new Artikel();
        a.setId(5L);
        a.setProduktname("Rundrohr 42,4 x 2,6");
        when(artikelRepo.sucheFuerAssistent(anyString(), anyString(), anyString(), anyString(), anyString(), any())).thenReturn(List.of(a));

        assertThat(artikelWerkzeug.vorschau(rohr())).contains("ACHTUNG, ähnliche Artikel gibt es schon: ID 5 Rundrohr 42,4 x 2,6");
    }

    @Test
    @DisplayName("Einheiten: Namen der Aufzählung und gebräuchliche Wörter werden erkannt, Unbekanntes wird mit der Liste der erlaubten abgelehnt")
    void einheiten() {
        assertThat(ArtikelAnlegenWerkzeug.einheit("Stück")).isEqualTo(Verrechnungseinheit.STUECK);
        assertThat(ArtikelAnlegenWerkzeug.einheit("stk")).isEqualTo(Verrechnungseinheit.STUECK);
        assertThat(ArtikelAnlegenWerkzeug.einheit("Meter")).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
        assertThat(ArtikelAnlegenWerkzeug.einheit("m")).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
        assertThat(ArtikelAnlegenWerkzeug.einheit("laufende Meter")).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
        assertThat(ArtikelAnlegenWerkzeug.einheit("m²")).isEqualTo(Verrechnungseinheit.QUADRATMETER);
        assertThat(ArtikelAnlegenWerkzeug.einheit("kg")).isEqualTo(Verrechnungseinheit.KILOGRAMM);
        assertThat(ArtikelAnlegenWerkzeug.einheit("l")).isEqualTo(Verrechnungseinheit.LITER);
        assertThatThrownBy(() -> ArtikelAnlegenWerkzeug.einheit("Fuhre")).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("Erlaubt: Stück");
    }

    @Test
    @DisplayName("Falsche Angaben werden mit Klartext abgelehnt: fehlender oder zu langer Name, falsche EAN, Preis, Lieferant, Preis ohne Lieferant")
    void artikelAblehnungen() {
        assertThatThrownBy(() -> artikelWerkzeug.vorschau(angaben().put("einheit", "STUECK"))).hasMessageContaining("Bezeichnung");
        assertThatThrownBy(() -> artikelWerkzeug.vorschau(angaben().put("bezeichnung", "x").put("einheit", "STUECK"))).hasMessageContaining("zu kurz");
        assertThatThrownBy(() -> artikelWerkzeug.vorschau(angaben().put("bezeichnung", "a".repeat(201)).put("einheit", "STUECK"))).hasMessageContaining("200");
        assertThatThrownBy(() -> artikelWerkzeug.vorschau(angaben().put("bezeichnung", "Rohr"))).hasMessageContaining("Einheit");
        assertThatThrownBy(() -> artikelWerkzeug.vorschau(rohr().put("ean", "123"))).isInstanceOf(KiWerkzeugFehler.class);
        assertThatThrownBy(() -> artikelWerkzeug.vorschau(rohr().put("lieferantId", 3).put("preis", -1))).hasMessageContaining("Preis");
        assertThatThrownBy(() -> artikelWerkzeug.vorschau(rohr().put("lieferantId", 3).put("preis", "viel"))).hasMessageContaining("keine Zahl");
        assertThatThrownBy(() -> artikelWerkzeug.vorschau(rohr().put("lieferantId", 99))).hasMessageContaining("Lieferanten");
        assertThatThrownBy(() -> artikelWerkzeug.vorschau(rohr().put("preis", 4))).hasMessageContaining("Lieferanten angeben");
        assertThatThrownBy(() -> artikelWerkzeug.vorschau(rohr().put("lieferantId", 3.5))).hasMessageContaining("ganze Zahl");
    }

    @Test
    @DisplayName("Ausführen legt den Artikel über den Artikeldienst an (Name, Einheit, EAN, Lieferantennummer, Preis, Lieferant) und meldet die ID")
    void artikelAusfuehren() {
        Artikel neu = new Artikel();
        neu.setId(42L);
        neu.setProduktname("Rundrohr 42,4 x 2,0 S235");
        when(artikelService.erstelleArtikel(any())).thenReturn(neu);

        String text = artikelWerkzeug.ausfuehren(rohr().put("lieferantId", 3).put("artikelnummerLieferant", "R-4224").put("preis", 4.5), anna);

        assertThat(text).isEqualTo("Der Artikel \"Rundrohr 42,4 x 2,0 S235\" wurde angelegt (ID 42, Laufende Meter).");
        ArgumentCaptor<ArtikelCreateDto> c = ArgumentCaptor.forClass(ArtikelCreateDto.class);
        verify(artikelService).erstelleArtikel(c.capture());
        ArtikelCreateDto d = c.getValue();
        assertThat(d.getProduktname()).isEqualTo("Rundrohr 42,4 x 2,0 S235");
        assertThat(d.getVerrechnungseinheit()).isEqualTo(Verrechnungseinheit.LAUFENDE_METER);
        assertThat(d.getExterneArtikelnummer()).isEqualTo("R-4224");
        assertThat(d.getPreis()).isEqualByComparingTo("4.5");
        assertThat(d.getLieferantId()).isEqualTo(3L);
    }

    @Test
    @DisplayName("Lehnt der Artikeldienst ab (zum Beispiel EAN schon vergeben), erscheint dessen Satz als Fehler des Werkzeugs")
    void artikelDienstLehntAb() {
        when(artikelService.erstelleArtikel(any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Die EAN 4006381333931 gehört schon zu einem anderen Artikel."));

        assertThatThrownBy(() -> artikelWerkzeug.ausfuehren(rohr().put("ean", "4006381333931"), anna)).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("gehört schon zu einem anderen Artikel");
    }

    // ------------------------------------------------------------------ Lager buchen

    private LagerService lagerService;
    private FunktionsRechteService rechte;
    private LagerModulSchalterService modulSchalter;
    private LagerBuchenWerkzeug lagerWerkzeug;

    @SuppressWarnings("unchecked")
    private void lagerAufbauen() {
        lagerService = mock(LagerService.class);
        rechte = mock(FunktionsRechteService.class);
        modulSchalter = mock(LagerModulSchalterService.class);
        ObjectProvider<LagerModulSchalterService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(modulSchalter);
        when(modulSchalter.istAktiv()).thenReturn(true);
        when(rechte.darfProfil(1L, Funktion.LAGER, FunktionsAktion.ANLEGEN)).thenReturn(true);
        lagerWerkzeug = new LagerBuchenWerkzeug(lagerService, rechte, provider);
        when(lagerService.orte()).thenReturn(List.of(new LagerortDto(1L, "Hauptlager", null, true, 0, false, null), new LagerortDto(2L, "Montagefahrzeug", null, true, 1, false, null),
                new LagerortDto(3L, "Altlager", null, false, 2, false, null)));
        when(lagerService.bestandVon(5L)).thenReturn(bestand("20"));
    }

    private LagerBestandDto bestand(String imHauptlager) {
        BigDecimal b = new BigDecimal(imHauptlager);
        return new LagerBestandDto(5L, "Rundrohr 42,4", "R-1", "m", b, null, false, false, List.of(new LagerBestandDto.OrtBestand(1L, "Hauptlager", b)), null, null, null);
    }

    private ObjectNode zugang() {
        return angaben().put("artikelId", 5).put("art", "ZUGANG").put("menge", 12.5).put("lagerort", "hauptlager");
    }

    @Test
    @DisplayName("Lager buchen ist eine Änderung; verfügbar nur mit Recht „Lager anlegen“ und eingeschaltetem Lagermodul, Administratoren immer (bei Modul an)")
    void lagerVerfuegbarkeit() {
        lagerAufbauen();
        assertThat(lagerWerkzeug.aendert()).isTrue();
        assertThat(lagerWerkzeug.verfuegbar(anna)).isTrue();
        assertThat(lagerWerkzeug.verfuegbar(chef)).isTrue();
        when(rechte.darfProfil(1L, Funktion.LAGER, FunktionsAktion.ANLEGEN)).thenReturn(false);
        assertThat(lagerWerkzeug.verfuegbar(anna)).isFalse();
        assertThat(lagerWerkzeug.verfuegbar(chef)).isTrue();
        when(modulSchalter.istAktiv()).thenReturn(false);
        assertThat(lagerWerkzeug.verfuegbar(chef)).isFalse();
        assertThat(lagerWerkzeug.parameter().path("required")).extracting(JsonNode::asText).containsExactly("artikelId", "art", "menge", "lagerort");
    }

    @Test
    @DisplayName("Vorschau Zugang und Entnahme in verständlicher Sprache; die Entnahme warnt, wenn dort weniger vorhanden ist")
    void lagerVorschau() {
        lagerAufbauen();

        assertThat(lagerWerkzeug.vorschau(zugang())).isEqualTo("Lagerzugang 12,5 m \"Rundrohr 42,4\" ins Lager \"Hauptlager\"");
        assertThat(lagerWerkzeug.vorschau(angaben().put("artikelId", 5).put("art", "abgang").put("menge", 5).put("lagerort", "Hauptlager").put("projektId", 7).put("notiz", "Geländer")))
                .isEqualTo("Entnahme 5 m \"Rundrohr 42,4\" aus dem Lager \"Hauptlager\" für Projekt 7 (Notiz: Geländer)");
        assertThat(lagerWerkzeug.vorschau(angaben().put("artikelId", 5).put("art", "ABGANG").put("menge", 30).put("lagerort", "Hauptlager")))
                .contains("ACHTUNG: dort sind nur 20 m vorhanden, der Bestand würde negativ");
        assertThat(lagerWerkzeug.vorschau(angaben().put("artikelId", 5).put("art", "ABGANG").put("menge", 1).put("lagerort", "Montagefahrzeug"))).contains("dort sind nur 0 m vorhanden");
        verify(lagerService, never()).buche(any());
    }

    @Test
    @DisplayName("Falsche Angaben: Art, Menge, Lagerort (unbekannt, inaktiv, mit Liste der vorhandenen), Projekt beim Zugang, unbekannter Artikel")
    void lagerAblehnungen() {
        lagerAufbauen();
        when(lagerService.bestandVon(99L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Artikel nicht gefunden"));

        assertThatThrownBy(() -> lagerWerkzeug.vorschau(zugang().put("art", "KORREKTUR"))).hasMessageContaining("ZUGANG oder ABGANG");
        assertThatThrownBy(() -> lagerWerkzeug.vorschau(zugang().put("menge", 0))).hasMessageContaining("Menge größer als 0");
        assertThatThrownBy(() -> lagerWerkzeug.vorschau(zugang().put("menge", "viel"))).hasMessageContaining("keine Zahl");
        assertThatThrownBy(() -> lagerWerkzeug.vorschau(zugang().put("lagerort", "Keller"))).hasMessageContaining("gibt es nicht").hasMessageContaining("Vorhanden: Hauptlager, Montagefahrzeug");
        assertThatThrownBy(() -> lagerWerkzeug.vorschau(zugang().put("lagerort", "Altlager"))).hasMessageContaining("gibt es nicht");
        assertThatThrownBy(() -> lagerWerkzeug.vorschau(zugang().put("projektId", 7))).hasMessageContaining("nur bei einer Entnahme");
        assertThatThrownBy(() -> lagerWerkzeug.vorschau(zugang().put("artikelId", 99))).hasMessageContaining("Artikel mit dieser ID gibt es nicht");
        assertThatThrownBy(() -> lagerWerkzeug.vorschau(angaben().put("art", "ZUGANG").put("menge", 1).put("lagerort", "Hauptlager"))).hasMessageContaining("Artikel-ID");
    }

    @Test
    @DisplayName("Zwei gleich benannte Lagerorte ergeben eine Rückfrage statt einer Vermutung")
    void lagerOrtMehrfach() {
        lagerAufbauen();
        when(lagerService.orte()).thenReturn(List.of(new LagerortDto(1L, "Lager", null, true, 0, false, null), new LagerortDto(2L, "lager", null, true, 1, false, null)));

        assertThatThrownBy(() -> lagerWerkzeug.vorschau(zugang().put("lagerort", "Lager"))).hasMessageContaining("mehrfach");
    }

    @Test
    @DisplayName("Ausführen bucht über den Lagerdienst mit Artikel, Lagerort, Menge, Projekt und Notiz und meldet den neuen Bestand")
    void lagerAusfuehren() {
        lagerAufbauen();
        when(lagerService.buche(any())).thenReturn(new LagerBestandDto(5L, "Rundrohr 42,4", "R-1", "m", new BigDecimal("32.5"), null, false, false, List.of(), null, null, null));

        String text = lagerWerkzeug.ausfuehren(zugang().put("notiz", "Lieferung RE-77"), anna);

        assertThat(text).isEqualTo("Gebucht: Lagerzugang 12,5 m \"Rundrohr 42,4\" ins Lager \"Hauptlager\". Neuer Bestand von \"Rundrohr 42,4\": 32,5 m.");
        ArgumentCaptor<LagerBuchungEingabe> c = ArgumentCaptor.forClass(LagerBuchungEingabe.class);
        verify(lagerService).buche(c.capture());
        assertThat(c.getValue()).isEqualTo(new LagerBuchungEingabe("ZUGANG", 5L, 1L, new BigDecimal("12.5"), null, "Lieferung RE-77"));
    }

    @Test
    @DisplayName("Ausführen prüft das Recht noch einmal und bucht ohne Recht nichts; Ablehnungen des Lagerdienstes werden Fehler des Werkzeugs")
    void lagerRechteUndFehler() {
        lagerAufbauen();
        when(rechte.darfProfil(1L, Funktion.LAGER, FunktionsAktion.ANLEGEN)).thenReturn(false);

        assertThatThrownBy(() -> lagerWerkzeug.ausfuehren(zugang(), anna)).isInstanceOf(KiWerkzeugFehler.class).hasMessageContaining("Dafür fehlt die Berechtigung: Lager, Anlegen");
        verify(lagerService, never()).buche(any());

        when(rechte.darfProfil(1L, Funktion.LAGER, FunktionsAktion.ANLEGEN)).thenReturn(true);
        when(lagerService.buche(any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Menge ist zu groß."));
        assertThatThrownBy(() -> lagerWerkzeug.ausfuehren(zugang(), anna)).hasMessage("Die Menge ist zu groß.");
    }
}
