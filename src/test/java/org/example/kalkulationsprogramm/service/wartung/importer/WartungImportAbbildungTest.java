package org.example.kalkulationsprogramm.service.wartung.importer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.example.kalkulationsprogramm.dto.wartung.WartungImportDto;
import org.example.kalkulationsprogramm.service.wartung.importer.WartungImportAbbildung.Ergebnis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Die Werte stammen aus der Auswertung der echten HandwerkPro-Daten (nur die Schreibweisen, keine Kundendaten). */
class WartungImportAbbildungTest {

    private static Ergebnis e(String feld, String wert, String aktion) {
        return new Ergebnis(feld, wert, aktion);
    }

    @ParameterizedTest(name = "„{0}“ ist keine Angabe")
    @ValueSource(strings = { "", "  ", "-", "–", "Keine Angaben", "Keine Angabe", "keine Angabe", "Keine Angbae", "Unbekannt", "unbekannt", "n.b.", "k.A." })
    @DisplayName("Alle Schreibweisen von „keine Angabe“ werden erkannt")
    void keineAngabe(String wert) {
        assertThat(WartungImportAbbildung.istKeineAngabe(wert)).isTrue();
    }

    @Test
    @DisplayName("Echte Werte sind keine „keine Angabe“")
    void echteWerte() {
        assertThat(WartungImportAbbildung.istKeineAngabe("Schüco")).isFalse();
        assertThat(WartungImportAbbildung.istKeineAngabe("2014")).isFalse();
        assertThat(WartungImportAbbildung.istKeineAngabe(null)).isTrue();
    }

    static Stream<Arguments> anschlag() {
        return Stream.of(Arguments.of("DINL", "DIN links", "ZUGEORDNET"), Arguments.of("DIN L", "DIN links", "ZUGEORDNET"), Arguments.of("DinL", "DIN links", "ZUGEORDNET"),
                Arguments.of("DINR", "DIN rechts", "ZUGEORDNET"), Arguments.of("DIN R", "DIN rechts", "ZUGEORDNET"), Arguments.of("DinR", "DIN rechts", "ZUGEORDNET"),
                Arguments.of("DIN", "keine Angabe", "LEER"), Arguments.of("Din", "keine Angabe", "LEER"), Arguments.of("", "keine Angabe", "LEER"),
                Arguments.of("Schiebetor", "Schiebetor", "ZUGEORDNET"), Arguments.of("Pendeltür", "Pendeltür", "NEU_IN_LISTE"));
    }

    @ParameterizedTest(name = "Anschlag „{0}“ wird „{1}“")
    @MethodSource("anschlag")
    void anschlag(String alt, String neu, String aktion) {
        assertThat(WartungImportAbbildung.abbilden("Anschlag", alt, Set.of())).containsExactly(e("Anschlag", neu, aktion));
    }

    @Test
    @DisplayName("Feuerwiderstand und Rauchschutz: aus „T30 RS“ wird T30 plus RS „Ja“, „T30“ allein bleibt ohne RS, „RS“ allein hat keine Klasse, „DST“ ist neu")
    void feuerwiderstand() {
        String f = "Feuerwiederstand / Rauchschutzklasse";
        for (String alt : new String[] { "T30 RS", "T30 / RS", "T30 - RS", "T30-RS", "T30-1-Tür (RS)" }) {
            assertThat(WartungImportAbbildung.abbilden(f, alt, Set.of())).as(alt).containsExactly(e("Feuerwiderstand", "T30", "ZUGEORDNET"), e("Rauchschutz (RS)", "Ja", "ZUGEORDNET"));
        }
        assertThat(WartungImportAbbildung.abbilden(f, "T30", Set.of())).containsExactly(e("Feuerwiderstand", "T30", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden(f, "T90", Set.of())).containsExactly(e("Feuerwiderstand", "T90", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden(f, "F30", Set.of())).containsExactly(e("Feuerwiderstand", "F30", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden(f, "RS", Set.of())).containsExactly(e("Feuerwiderstand", "keine Angabe", "LEER"), e("Rauchschutz (RS)", "Ja", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden(f, "RST", Set.of())).contains(e("Rauchschutz (RS)", "Ja", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden(f, "DST", Set.of())).containsExactly(e("Feuerwiderstand", "DST", "NEU_IN_LISTE"));
        assertThat(WartungImportAbbildung.abbilden(f, "keine Angabe", Set.of())).containsExactly(e("Feuerwiderstand", "keine Angabe", "LEER"));
        assertThat(WartungImportAbbildung.abbilden(f, "", Set.of())).containsExactly(e("Feuerwiderstand", "keine Angabe", "LEER"));
    }

    @Test
    @DisplayName("Hersteller: Namen aus der Liste, Typ abgetrennt, Unbekanntes als neu, keine Angabe leer")
    void hersteller() {
        assertThat(WartungImportAbbildung.abbilden("Hersteller", "Schüco", Set.of())).containsExactly(e("Hersteller", "Schüco", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden("Hersteller", "Hörmann H6", Set.of())).containsExactly(e("Hersteller", "Hörmann", "ZUGEORDNET"), e("Typ / Modell", "H6", "FREITEXT"));
        assertThat(WartungImportAbbildung.abbilden("Hersteller", "HOBA Typ7", Set.of())).containsExactly(e("Hersteller", "Hoba", "ZUGEORDNET"), e("Typ / Modell", "Typ7", "FREITEXT"));
        assertThat(WartungImportAbbildung.abbilden("Hersteller", "Hoba", Set.of())).containsExactly(e("Hersteller", "Hoba", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden("Hersteller", "Riexinger Typ 81", Set.of())).containsExactly(e("Hersteller", "Riexinger", "ZUGEORDNET"), e("Typ / Modell", "Typ 81", "FREITEXT"));
        assertThat(WartungImportAbbildung.abbilden("Hersteller", "Wires Bauelemente", Set.of())).containsExactly(e("Hersteller", "Wires Bauelemente", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden("Hersteller", "W. Podszuck KG. Kiel", Set.of())).containsExactly(e("Hersteller", "W. Podszuck KG. Kiel", "NEU_IN_LISTE"));
        assertThat(WartungImportAbbildung.abbilden("Hersteller", "Keine Angaben", Set.of())).containsExactly(e("Hersteller", "keine Angabe", "LEER"));
        assertThat(WartungImportAbbildung.abbilden("Hersteller", "Hörmannsdorfer", Set.of())).containsExactly(e("Hersteller", "Hörmannsdorfer", "NEU_IN_LISTE"));
        assertThat(WartungImportAbbildung.abbilden("Hersteller", "Muster Metallbau", Set.of("Muster Metallbau"))).containsExactly(e("Hersteller", "Muster Metallbau", "ZUGEORDNET"));
    }

    @Test
    @DisplayName("Doppelte Felder (Hersteller / Fabrikat, Hersteller der Tür, Standort / Lage der Tür, Festellvorrichtung, Einbaudatum) landen im selben Katalogfeld")
    void doppelteFelder() {
        assertThat(WartungImportAbbildung.katalogFeld("Hersteller / Fabrikat")).isEqualTo("Hersteller");
        assertThat(WartungImportAbbildung.katalogFeld("Hersteller der Tür")).isEqualTo("Hersteller");
        assertThat(WartungImportAbbildung.katalogFeld("Standort / Lage der Tür")).isEqualTo("Standort / Lage");
        assertThat(WartungImportAbbildung.katalogFeld("Festellvorrichtung")).isEqualTo("Feststellanlage Hersteller / Typ");
        assertThat(WartungImportAbbildung.katalogFeld("Feststellanlage / Hersteller")).isEqualTo("Feststellanlage Hersteller / Typ");
        assertThat(WartungImportAbbildung.katalogFeld("Feststellvorrichtung Hersteller/Typ")).isEqualTo("Feststellanlage Hersteller / Typ");
        assertThat(WartungImportAbbildung.katalogFeld("Feuerwiederstand / Rauchschutzklasse")).isEqualTo("Feuerwiderstand");
        assertThat(WartungImportAbbildung.katalogFeld("Rauchmelder Einbaudatum")).isEqualTo("Rauchmelder Einbaujahr");
        assertThat(WartungImportAbbildung.katalogFeld("Branderkennungsteil")).isEqualTo("Rauchmelder Typ");
        assertThat(WartungImportAbbildung.katalogFeld("Türschließer ")).isNull();
        assertThat(WartungImportAbbildung.istFluegelFeld("Anzahl Türflügel / Torart")).isTrue();
        assertThat(WartungImportAbbildung.istFluegelFeld(" Anzahl Türflügel ")).isTrue();
    }

    @Test
    @DisplayName("Jahre: vierstellig und plausibel, aus Datum das Jahr, alles andere leer (ungültig) oder keine Angabe (leer)")
    void jahre() {
        assertThat(WartungImportAbbildung.abbilden("Baujahr", "2014", Set.of())).containsExactly(e("Baujahr", "2014", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden("Baujahr", " 1992 ", Set.of())).containsExactly(e("Baujahr", "1992", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden("Rauchmelder Einbaudatum", "12.05.2025", Set.of())).containsExactly(e("Rauchmelder Einbaujahr", "2025", "ZUGEORDNET"));
        assertThat(WartungImportAbbildung.abbilden("Baujahr", "Keine Angabe", Set.of())).containsExactly(e("Baujahr", "", "LEER"));
        assertThat(WartungImportAbbildung.abbilden("Baujahr", "-", Set.of())).containsExactly(e("Baujahr", "", "LEER"));
        assertThat(WartungImportAbbildung.abbilden("Baujahr", "ca. 80er", Set.of())).containsExactly(e("Baujahr", "", "UNGUELTIG"));
        assertThat(WartungImportAbbildung.abbilden("Baujahr", "1850", Set.of())).containsExactly(e("Baujahr", "", "UNGUELTIG"));
        assertThat(WartungImportAbbildung.abbilden("Baujahr", "20145", Set.of())).containsExactly(e("Baujahr", "", "UNGUELTIG"));
    }

    @Test
    @DisplayName("Standort und Zulassungsnummer bleiben Freitext, „keine Angabe“ wird leer")
    void freitext() {
        assertThat(WartungImportAbbildung.abbilden("Standort / Lage", "Treppenhaus 2. OG", Set.of())).containsExactly(e("Standort / Lage", "Treppenhaus 2. OG", "FREITEXT"));
        assertThat(WartungImportAbbildung.abbilden("Zulassungsnummer", "Z-6.20-2203", Set.of())).containsExactly(e("Zulassungsnummer", "Z-6.20-2203", "FREITEXT"));
        assertThat(WartungImportAbbildung.abbilden("Zulassungsnummer", "Keine Angbae", Set.of())).containsExactly(e("Zulassungsnummer", "", "LEER"));
        assertThat(WartungImportAbbildung.abbilden("Standort / Lage", "", Set.of())).containsExactly(e("Standort / Lage", "", "LEER"));
    }

    static Stream<Arguments> fluegel() {
        return Stream.of(
                Arguments.of("1-flügelig", "einflg", List.of()),
                Arguments.of("2-flügelig", "zweiflg", List.of()),
                Arguments.of("1", "einflg", List.of()),
                Arguments.of("2", "zweiflg", List.of()),
                Arguments.of("2 - Flügelig", "zweiflg", List.of()),
                Arguments.of("1-Flügelig", "einflg", List.of()),
                Arguments.of("1-Flügelig / Stahl / Drehtür", "einflg", List.of(e("Werkstoff", "Stahl", "ZUGEORDNET"), e("Tür- / Torart", "Drehtür", "ZUGEORDNET"))),
                Arguments.of("1-Flügelig / Stahl / Drehtür DinL", "einflg", List.of(e("Werkstoff", "Stahl", "ZUGEORDNET"), e("Tür- / Torart", "Drehtür", "ZUGEORDNET"), e("Anschlag", "DIN links", "ZUGEORDNET"))),
                Arguments.of("1-Flügelig / Aluminium / Drehtür DinL", "einflg", List.of(e("Werkstoff", "Aluminium", "ZUGEORDNET"), e("Tür- / Torart", "Drehtür", "ZUGEORDNET"), e("Anschlag", "DIN links", "ZUGEORDNET"))),
                Arguments.of("1-Flügelig / Holz / Glas / Drehtür", "einflg", List.of(e("Werkstoff", "Holz / Glas", "ZUGEORDNET"), e("Tür- / Torart", "Drehtür", "ZUGEORDNET"))),
                Arguments.of("1-Flügelig / Holz / Drehtür DinR", "einflg", List.of(e("Werkstoff", "Holz", "ZUGEORDNET"), e("Tür- / Torart", "Drehtür", "ZUGEORDNET"), e("Anschlag", "DIN rechts", "ZUGEORDNET"))),
                Arguments.of("Brandschutztor, Schiebetor", "schiebe", List.of(e("Tür- / Torart", "Brandschutztor", "ZUGEORDNET"))),
                Arguments.of("", null, List.of()),
                Arguments.of("Keine Angabe", null, List.of()),
                Arguments.of("Sondertür", null, List.of()));
    }

    @ParameterizedTest(name = "Flügelfeld „{0}“ → Bauart {1}")
    @MethodSource("fluegel")
    @DisplayName("Aus „Anzahl Türflügel / Torart“ werden Bauart, Werkstoff, Torart und Anschlag getrennt")
    void fluegel(String alt, String bauart, List<Ergebnis> felder) {
        WartungImportAbbildung.Fluegel f = WartungImportAbbildung.fluegel(alt);

        assertThat(f.bauart()).isEqualTo(bauart);
        assertThat(f.felder()).containsExactlyElementsOf(felder);
    }

    @Test
    @DisplayName("Auswahlfelder ohne feste Regel: der alte Wert kommt als neu in die Liste, leer ist keine Angabe")
    void sonstigeAuswahl() {
        assertThat(WartungImportAbbildung.abbilden("Feststellanlage / Hersteller", "GEZE ISM", Set.of())).containsExactly(e("Feststellanlage Hersteller / Typ", "GEZE ISM", "NEU_IN_LISTE"));
        assertThat(WartungImportAbbildung.abbilden("Rauchmelder Hersteller/Typ", "", Set.of())).containsExactly(e("Rauchmelder Typ", "keine Angabe", "LEER"));
        assertThat(WartungImportAbbildung.abbilden("Verglasung / Glasart", "Keine Angaben", Set.of())).containsExactly(e("Glasart", "keine Angabe", "LEER"));
        assertThat(WartungImportAbbildung.abbilden("Gibt es nicht", "x", Set.of())).isEmpty();
    }

    @Test
    @DisplayName("Protokollart: bekannte Namen unverändert (auch in anderer Schreibweise), unbekannte ergeben null")
    void protokollart() {
        assertThat(WartungImportAbbildung.protokollart("Wartungsprotokoll")).isEqualTo("Wartungsprotokoll");
        assertThat(WartungImportAbbildung.protokollart(" instandhaltungsprotokoll ")).isEqualTo("Instandhaltungsprotokoll");
        assertThat(WartungImportAbbildung.protokollart("Pruefprotokoll")).isEqualTo("Prüfprotokoll");
        assertThat(WartungImportAbbildung.protokollart("Sonstiges")).isNull();
        assertThat(WartungImportAbbildung.protokollart(null)).isNull();
        assertThat(WartungImportDto.ZUGEORDNET).isEqualTo("ZUGEORDNET");
    }
}
