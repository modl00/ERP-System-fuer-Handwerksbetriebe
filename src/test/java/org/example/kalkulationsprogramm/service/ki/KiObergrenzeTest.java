package org.example.kalkulationsprogramm.service.ki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.KiAktionProtokoll;
import org.example.kalkulationsprogramm.repository.KiAktionProtokollRepository;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze.Datenschutzstufe;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze.Eingabe;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze.Eingriff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KiObergrenzeTest {

    private final Map<String, String> speicher = new HashMap<>();
    private SystemSettingsService einstellungen;
    private KiAktionProtokollRepository protokoll;
    private KiObergrenze ober;

    @BeforeEach
    void setUp() {
        einstellungen = mock(SystemSettingsService.class);
        protokoll = mock(KiAktionProtokollRepository.class);
        when(einstellungen.get(anyString(), anyString())).thenAnswer(a -> speicher.getOrDefault(a.<String>getArgument(0), a.getArgument(1)));
        org.mockito.Mockito.doAnswer(a -> speicher.put(a.getArgument(0), a.getArgument(1))).when(einstellungen).save(anyString(), anyString(), anyString());
        ober = new KiObergrenze(einstellungen, protokoll);
    }

    private void setze(String datenschutz, String eingriff) {
        speicher.put("ki.obergrenze.datenschutz", datenschutz);
        speicher.put("ki.obergrenze.eingriff", eingriff);
    }

    @Test
    @DisplayName("Ohne Eintrag gilt der bisherige Zustand (alles erlaubt, Änderungen mit Bestätigung); ungültige Werte fallen darauf zurück")
    void standard() {
        assertThat(ober.datenschutz()).isEqualTo(Datenschutzstufe.CLOUD);
        assertThat(ober.eingriff()).isEqualTo(Eingriff.MIT_BESTAETIGUNG);
        assertThat(ober.sperrgrund(AnbieterTyp.GEMINI)).isEmpty();
        assertThat(ober.sperrtProgrammdaten(AnbieterTyp.GEMINI)).isFalse();

        setze("unsinn", "");
        assertThat(ober.datenschutz()).isEqualTo(Datenschutzstufe.CLOUD);
        assertThat(ober.eingriff()).isEqualTo(Eingriff.MIT_BESTAETIGUNG);
    }

    @Test
    @DisplayName("„Nur im Haus“: Cloud-Anbieter sind gesperrt (mit Klartext), die lokale KI nicht")
    void nurImHaus() {
        setze("LOKAL", "MIT_BESTAETIGUNG");

        assertThat(ober.sperrgrund(AnbieterTyp.GEMINI)).hasValueSatisfying(s -> assertThat(s).contains("nur lokale KI-Anbieter"));
        assertThat(ober.sperrgrund(AnbieterTyp.CLAUDE)).isPresent();
        assertThat(ober.sperrgrund(AnbieterTyp.LOKAL)).isEmpty();
        assertThat(ober.sperrtProgrammdaten(AnbieterTyp.LOKAL)).as("die lokale KI darf Programmdaten sehen, sie bleiben im Haus").isFalse();
    }

    @Test
    @DisplayName("Ausgeschaltet: kein Anbieter, auch nicht die lokale KI")
    void aus() {
        setze("CLOUD", "AUS");

        assertThat(ober.sperrgrund(AnbieterTyp.LOKAL)).hasValueSatisfying(s -> assertThat(s).contains("ausgeschaltet"));
        assertThat(ober.sperrgrund(AnbieterTyp.GEMINI)).isPresent();
    }

    @Test
    @DisplayName("Nur allgemeine Anfragen: Cloud-Anbieter sind erlaubt, bekommen aber keine Programmdaten; mit Vertrag nur beim eingetragenen Anbieter")
    void programmdaten() {
        setze("ALLGEMEIN", "MIT_BESTAETIGUNG");
        assertThat(ober.sperrgrund(AnbieterTyp.CLAUDE)).isEmpty();
        assertThat(ober.sperrtProgrammdaten(AnbieterTyp.CLAUDE)).isTrue();
        assertThat(ober.sperrtProgrammdaten(AnbieterTyp.MISTRAL)).isTrue();
        assertThat(ober.sperrtProgrammdaten(AnbieterTyp.LOKAL)).isFalse();

        setze("MIT_VERTRAG", "MIT_BESTAETIGUNG");
        speicher.put("ki.obergrenze.vertrag.MISTRAL", "true");
        assertThat(ober.sperrtProgrammdaten(AnbieterTyp.MISTRAL)).as("Vertrag eingetragen").isFalse();
        assertThat(ober.sperrtProgrammdaten(AnbieterTyp.CLAUDE)).as("kein Vertrag eingetragen").isTrue();
        assertThat(ober.hatVertrag(AnbieterTyp.LOKAL)).as("für die lokale KI gibt es keinen Vertrag").isFalse();

        setze("CLOUD", "MIT_BESTAETIGUNG");
        assertThat(ober.sperrtProgrammdaten(AnbieterTyp.CLAUDE)).isFalse();
    }

    @Test
    @DisplayName("Ohne Rückfrage läuft ein Werkzeug nur im Modus „Erweitert“ und nur, wenn es freigeschaltet ist")
    void ohneRueckfrage() {
        speicher.put("ki.obergrenze.ohne-rueckfrage", "zeit_buchen, notiz_anlegen");

        setze("CLOUD", "MIT_BESTAETIGUNG");
        assertThat(ober.ohneRueckfrage("zeit_buchen")).as("freigeschaltet, aber der Modus erlaubt es nicht").isFalse();
        setze("CLOUD", "ERWEITERT");
        assertThat(ober.ohneRueckfrage("zeit_buchen")).isTrue();
        assertThat(ober.ohneRueckfrage("notiz_anlegen")).isTrue();
        assertThat(ober.ohneRueckfrage("lager_buchen")).as("nicht freigeschaltet").isFalse();
        assertThat(ober.freigeschalteteWerkzeuge()).containsExactly("zeit_buchen", "notiz_anlegen");
    }

    @Test
    @DisplayName("Speichern: legt alles ab und schreibt, wer was von wo nach wo geändert hat, ins KI-Protokoll; ohne Änderung kein Eintrag")
    void speichern() {
        ober.speichern(new Eingabe("MIT_VERTRAG", "ERWEITERT", Map.of("MISTRAL", true, "CLAUDE", false), Set.of("zeit_buchen")), Set.of("zeit_buchen", "lager_buchen"), 7L, "anna");

        assertThat(ober.datenschutz()).isEqualTo(Datenschutzstufe.MIT_VERTRAG);
        assertThat(ober.eingriff()).isEqualTo(Eingriff.ERWEITERT);
        assertThat(ober.hatVertrag(AnbieterTyp.MISTRAL)).isTrue();
        assertThat(ober.hatVertrag(AnbieterTyp.CLAUDE)).isFalse();
        assertThat(ober.freigeschalteteWerkzeuge()).containsExactly("zeit_buchen");
        ArgumentCaptor<KiAktionProtokoll> p = ArgumentCaptor.forClass(KiAktionProtokoll.class);
        verify(protokoll).save(p.capture());
        assertThat(p.getValue().getBenutzer()).isEqualTo("anna");
        assertThat(p.getValue().getProfilId()).isEqualTo(7L);
        assertThat(p.getValue().getWerkzeug()).isEqualTo("obergrenze_aendern");
        assertThat(p.getValue().getErgebnis()).contains("Vorher: Datenschutz CLOUD, Eingriff MIT_BESTAETIGUNG").contains("Nachher: Datenschutz MIT_VERTRAG, Eingriff ERWEITERT");

        org.mockito.Mockito.clearInvocations(protokoll);
        ober.speichern(new Eingabe("MIT_VERTRAG", "ERWEITERT", Map.of("MISTRAL", true), Set.of("zeit_buchen")), Set.of("zeit_buchen"), 7L, "anna");
        verify(protokoll, never()).save(any());
    }

    @Test
    @DisplayName("Falsche Eingaben werden mit Klartext abgelehnt und ändern nichts: unbekannte Stufe, nicht freischaltbares Werkzeug")
    void ablehnungen() {
        assertThatThrownBy(() -> ober.speichern(new Eingabe("GEHEIM", "MIT_BESTAETIGUNG", Map.of(), Set.of()), Set.of(), 1L, "a")).hasMessageContaining("Unbekannte Stufe für Datenschutz");
        assertThatThrownBy(() -> ober.speichern(new Eingabe("CLOUD", "ALLES", Map.of(), Set.of()), Set.of(), 1L, "a")).hasMessageContaining("Unbekannte Stufe für Eingriff");
        assertThatThrownBy(() -> ober.speichern(new Eingabe("CLOUD", "ERWEITERT", Map.of(), Set.of("artikel_loeschen")), Set.of("zeit_buchen"), 1L, "a"))
                .hasMessageContaining("artikel_loeschen").hasMessageContaining("nicht ohne Rückfrage");

        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
        verify(protokoll, never()).save(any());
        assertThat(List.of(ober.datenschutz(), ober.eingriff())).containsExactly(Datenschutzstufe.CLOUD, Eingriff.MIT_BESTAETIGUNG);
    }
}
