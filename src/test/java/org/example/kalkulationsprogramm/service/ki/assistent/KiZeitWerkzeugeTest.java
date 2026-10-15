package org.example.kalkulationsprogramm.service.ki.assistent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.ErfassungsQuelle;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.FunktionsRechteService;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.Eingabe;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.Ergebnis;
import org.example.kalkulationsprogramm.service.ZeiterfassungNachtragService.HinweisNoetigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Nur Dummy-Daten. Zeit buchen ändert nur als Vorschlag mit Bestätigung und nur für den angemeldeten Benutzer. */
class KiZeitWerkzeugeTest {

    private final ObjectMapper json = new ObjectMapper();
    private final KiKontext anna = new KiKontext(1L, "anna", false);
    private final KiKontext chef = new KiKontext(2L, "chef", true);

    private ZeiterfassungNachtragService zeiten;
    private FunktionsRechteService rechte;
    private FrontendUserProfileRepository profile;
    private ProjektRepository projekte;
    private ArbeitsgangRepository arbeitsgaenge;
    private ZeitBuchenWerkzeug werkzeug;

    @BeforeEach
    void setUp() {
        zeiten = mock(ZeiterfassungNachtragService.class);
        rechte = mock(FunktionsRechteService.class);
        profile = mock(FrontendUserProfileRepository.class);
        projekte = mock(ProjektRepository.class);
        arbeitsgaenge = mock(ArbeitsgangRepository.class);
        werkzeug = new ZeitBuchenWerkzeug(zeiten, rechte, profile, projekte, arbeitsgaenge);

        Mitarbeiter m = new Mitarbeiter();
        m.setLoginToken("tok-anna");
        FrontendUserProfile p = new FrontendUserProfile();
        p.setMitarbeiter(m);
        when(profile.findById(1L)).thenReturn(Optional.of(p));
        when(rechte.darfProfil(1L, Funktion.ZEITERFASSUNG, FunktionsAktion.ANLEGEN)).thenReturn(true);

        Projekt projekt = new Projekt();
        projekt.setBauvorhaben("Geländer Musterstraße");
        when(projekte.findById(7L)).thenReturn(Optional.of(projekt));
        Arbeitsgang gang = new Arbeitsgang();
        gang.setBeschreibung("Montage");
        when(arbeitsgaenge.findById(3L)).thenReturn(Optional.of(gang));
    }

    private ObjectNode angaben() {
        return json.createObjectNode().put("datum", "2026-10-03").put("start", "08:00").put("ende", "10:30").put("projektId", 7).put("arbeitsgangId", 3);
    }

    @Test
    @DisplayName("Zeit buchen ändert Daten: nur als Vorschlag, und die Vorschau nennt alles in verständlicher Sprache")
    void vorschau() {
        assertThat(werkzeug.aendert()).isTrue();

        String text = werkzeug.vorschau(angaben().put("notiz", "Geländer montiert"));

        assertThat(text).isEqualTo("Eintragen: Arbeitszeit am 03.10.2026 von 08:00 bis 10:30, Projekt \"Geländer Musterstraße\", Arbeitsgang \"Montage\" (Notiz: Geländer montiert)");
    }

    @Test
    @DisplayName("Mit Dauer statt Ende wird die Dauer lesbar angezeigt; eine Pause braucht kein Projekt")
    void dauerUndPause() {
        ObjectNode a = json.createObjectNode().put("art", "PAUSE").put("datum", "2026-10-03").put("start", "12:00").put("dauerMinuten", 30);

        assertThat(werkzeug.vorschau(a)).isEqualTo("Eintragen: Pause am 03.10.2026 von 12:00 für 0 Std. 30 Min.");
    }

    @Test
    @DisplayName("Nach Bestätigung wird für den Mitarbeiter des angemeldeten Benutzers gebucht, mit der Quelle KI-Assistent")
    void ausfuehren() {
        when(zeiten.erfasse(any(), eq(ErfassungsQuelle.DESKTOP), eq("KI-Assistent am PC"))).thenReturn(new Ergebnis(Map.of("id", 1), List.of()));

        String ergebnis = werkzeug.ausfuehren(angaben(), anna);

        ArgumentCaptor<Eingabe> e = ArgumentCaptor.forClass(Eingabe.class);
        verify(zeiten).erfasse(e.capture(), eq(ErfassungsQuelle.DESKTOP), eq("KI-Assistent am PC"));
        assertThat(e.getValue().token()).isEqualTo("tok-anna");
        assertThat(e.getValue().typ()).isEqualTo("ARBEIT");
        assertThat(e.getValue().datum()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(e.getValue().start()).isEqualTo(LocalTime.of(8, 0));
        assertThat(e.getValue().ende()).isEqualTo(LocalTime.of(10, 30));
        assertThat(e.getValue().projektId()).isEqualTo(7L);
        assertThat(e.getValue().bestaetigt()).as("die KI bestätigt nie Hinweise des Programms").isFalse();
        assertThat(ergebnis).startsWith("Eingetragen: Arbeitszeit am 03.10.2026");
    }

    @Test
    @DisplayName("Verlangt das Programm eine Bestätigung (Urlaub am selben Tag, sehr langer Tag), bucht die KI nicht, sondern nennt den Grund")
    void hinweisNoetig() {
        when(zeiten.erfasse(any(), any(), any())).thenThrow(new HinweisNoetigException(List.of("Am 03.10.2026 ist bereits Urlaub eingetragen.")));

        assertThatThrownBy(() -> werkzeug.ausfuehren(angaben(), anna)).isInstanceOf(KiWerkzeugFehler.class)
                .hasMessageContaining("Nicht eingetragen").hasMessageContaining("Urlaub eingetragen").hasMessageContaining("selbst unter Zeiterfassung");
    }

    @Test
    @DisplayName("Ablehnungen des Programms (gesperrter Monat, Überschneidung) kommen im Klartext zurück")
    void ablehnung() {
        when(zeiten.erfasse(any(), any(), any())).thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Der Monat Oktober 2026 ist abgeschlossen."));

        assertThatThrownBy(() -> werkzeug.ausfuehren(angaben(), anna)).isInstanceOf(KiWerkzeugFehler.class).hasMessage("Der Monat Oktober 2026 ist abgeschlossen.");
    }

    @Test
    @DisplayName("Ohne Recht oder ohne zugeordneten Mitarbeiter sieht die KI das Werkzeug nicht und bucht nichts")
    void rechteUndMitarbeiter() {
        KiKontext ohneRecht = new KiKontext(5L, "bernd", false);
        when(rechte.darfProfil(5L, Funktion.ZEITERFASSUNG, FunktionsAktion.ANLEGEN)).thenReturn(false);
        KiKontext ohneMitarbeiter = new KiKontext(6L, "claudia", false);
        when(rechte.darfProfil(6L, Funktion.ZEITERFASSUNG, FunktionsAktion.ANLEGEN)).thenReturn(true);
        FrontendUserProfile leer = new FrontendUserProfile();
        when(profile.findById(6L)).thenReturn(Optional.of(leer));

        assertThat(werkzeug.verfuegbar(anna)).isTrue();
        assertThat(werkzeug.verfuegbar(ohneRecht)).isFalse();
        assertThat(werkzeug.verfuegbar(ohneMitarbeiter)).isFalse();
        assertThatThrownBy(() -> werkzeug.ausfuehren(angaben(), ohneRecht)).isInstanceOf(KiWerkzeugFehler.class);
        verify(zeiten, never()).erfasse(any(), any(), any());
        assertThat(werkzeug.verfuegbar(chef)).as("auch ein Administrator braucht einen zugeordneten Mitarbeiter").isFalse();
    }

    @Test
    @DisplayName("Falsche Eingaben werden mit Klartext abgewiesen, ohne etwas zu buchen")
    void eingaben() {
        assertThatThrownBy(() -> werkzeug.vorschau(angaben().put("datum", "03.10.2026"))).hasMessageContaining("JJJJ-MM-TT");
        assertThatThrownBy(() -> werkzeug.vorschau(angaben().put("start", "acht"))).hasMessageContaining("HH:MM");
        assertThatThrownBy(() -> werkzeug.vorschau(angaben().put("dauerMinuten", 60))).hasMessageContaining("nicht beides");
        assertThatThrownBy(() -> werkzeug.vorschau(angaben().put("art", "URLAUB"))).hasMessageContaining("ARBEIT oder PAUSE");
        ObjectNode ohneEnde = json.createObjectNode().put("datum", "2026-10-03").put("start", "08:00").put("projektId", 7).put("arbeitsgangId", 3);
        assertThatThrownBy(() -> werkzeug.vorschau(ohneEnde)).hasMessageContaining("Ende oder die Dauer");
        ObjectNode ohneProjekt = angaben();
        ohneProjekt.remove("projektId");
        assertThatThrownBy(() -> werkzeug.vorschau(ohneProjekt)).hasMessageContaining("Projekt angeben");
        assertThatThrownBy(() -> werkzeug.vorschau(angaben().put("projektId", 99))).hasMessageContaining("Projekt mit dieser ID gibt es nicht");
        assertThatThrownBy(() -> werkzeug.vorschau(angaben().put("dauerMinuten", 0).put("ende", (String) null))).isInstanceOf(KiWerkzeugFehler.class);
        verify(zeiten, never()).erfasse(any(), any(), any());
    }

    @Test
    @DisplayName("Projekte suchen: Treffer mit ID, Auftrag, Kunde und Stand; ohne Treffer ehrlich; begrenzt auf 10")
    void projekteSuchen() {
        Kunde kunde = new Kunde();
        kunde.setName("Max Mustermann");
        Projekt offen = new Projekt();
        offen.setId(7L);
        offen.setBauvorhaben("Geländer Musterstraße");
        offen.setAuftragsnummer("A-100");
        offen.setKundenId(kunde);
        ProjekteSuchenWerkzeug suche = new ProjekteSuchenWerkzeug(projekte);
        when(projekte.searchByBauvorhabenOrKundeOrEmail("gelaender")).thenReturn(List.of(offen));
        when(projekte.searchByBauvorhabenOrKundeOrEmail("nichts")).thenReturn(List.of());
        ObjectNode a = json.createObjectNode().put("suche", "gelaender");

        assertThat(suche.aendert()).isFalse();
        assertThat(suche.ausfuehren(a, anna)).isEqualTo("- ID 7 | Geländer Musterstraße | Auftrag A-100 | Kunde Max Mustermann | offen");
        assertThat(suche.ausfuehren(json.createObjectNode().put("suche", "nichts"), anna)).startsWith("Kein Projekt gefunden");
        List<Projekt> viele = java.util.stream.IntStream.range(0, 12).mapToObj(i -> {
            Projekt p = new Projekt();
            p.setId((long) i);
            p.setBauvorhaben("P" + i);
            return p;
        }).toList();
        when(projekte.searchByBauvorhabenOrKundeOrEmail("p")).thenReturn(viele);
        assertThat(suche.ausfuehren(json.createObjectNode().put("suche", "p"), anna)).contains("weitere Treffer");
    }

    @Test
    @DisplayName("Arbeitsgänge: alphabetisch mit ID; leer wird ehrlich gesagt")
    void arbeitsgaenge() {
        Arbeitsgang a = new Arbeitsgang();
        a.setId(3L);
        a.setBeschreibung("Montage");
        Arbeitsgang b = new Arbeitsgang();
        b.setId(1L);
        b.setBeschreibung("Fahrzeit");
        ArbeitsgaengeWerkzeug w = new ArbeitsgaengeWerkzeug(arbeitsgaenge);
        when(arbeitsgaenge.findAll()).thenReturn(List.of(a, b));

        assertThat(w.ausfuehren(json.createObjectNode(), anna)).isEqualTo("- ID 1 | Fahrzeit\n- ID 3 | Montage");
        assertThat(w.aendert()).isFalse();
        when(arbeitsgaenge.findAll()).thenReturn(List.of());
        assertThat(w.ausfuehren(json.createObjectNode(), anna)).contains("noch keine Arbeitsgänge");
    }
}
