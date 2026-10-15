package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Die Suche in der Wissensbasis, zuerst mit kleinem Beispieltext (Logik), dann mit der echten mitgelieferten Datei (Prüffragen): Die richtigen Seiten müssen zu
 * typischen Fragen vorn stehen.
 */
class KiWissenServiceTest {

    private static final String BEISPIEL = """
            # Kopf

            ## Seite: Artikel
            Menüweg: Vorlagen & Stammdaten → Katalog → Artikel
            Adresse: /artikel
            Inhalt: Neuer Artikel · Artikelnummer · Einheit · Kategorie

            ## Seite: Lager
            Menüweg: Vorlagen & Stammdaten → Katalog → Lager
            Adresse: /lager
            Inhalt: Bestand · Zugang buchen · Entnahme buchen · Inventur · Mindestbestand

            ## Seite: Kunden
            Menüweg: Vorlagen & Stammdaten → Kontakte → Kunden
            Adresse: /kunden
            Inhalt: Neuer Kunde · Ansprechpartner · Telefon
            """;

    private KiWissenService service;

    @BeforeEach
    void setUp() {
        service = new KiWissenService();
    }

    @Test
    void passendeAbschnitteKommenZuerstUndOhneTrefferKommtNichts() {
        service.init(BEISPIEL);

        String text = service.relevantes("Wie buche ich einen Zugang im Lager?", 5000);

        assertThat(text).startsWith("## Seite: Lager");
        assertThat(service.relevantes("xyzabc qwertz", 5000)).isEmpty();
        assertThat(service.relevantes("", 5000)).isEmpty();
        assertThat(service.relevantes(null, 5000)).isEmpty();
        assertThat(service.relevantes("Lager", 0)).isEmpty();
    }

    @Test
    void trefferImTitelZaehlenMehrAlsImText() {
        service.init(BEISPIEL);
        // "Ansprechpartner" steht im Text der Seite Kunden, "Kunden" im Titel: die Frage nach Kunden findet die Seite Kunden zuerst
        assertThat(service.relevantes("Kunden", 5000)).startsWith("## Seite: Kunden");
    }

    @Test
    void einzahlUndMehrzahlUndUmlauteFindenSich() {
        service.init("## Seite: Nummern\nInhalt: Dokumentnummer · Nummernkreis · Größe · Löschen\n");
        assertThat(service.relevantes("nummer", 2000)).contains("Nummern");
        assertThat(service.relevantes("Nummern", 2000)).contains("Nummern");
        assertThat(service.relevantes("Groesse", 2000)).as("ö/oe, ß/ss gleichgesetzt nur in einer Richtung: Größe → groe").isEmpty();
        assertThat(service.relevantes("löschen", 2000)).contains("Löschen");
    }

    @Test
    void fuellwoerterAllein_findenNichts() {
        service.init(BEISPIEL);
        assertThat(service.relevantes("Wie kann ich das machen?", 2000)).isEmpty();
    }

    @Test
    void dieLaengeIstBegrenztUndEinAbschnittWirdGekuerzt() {
        service.init("## Seite: A\n" + "lager ".repeat(2000) + "\n\n## Seite: B\nlager kurz\n");

        String text = service.relevantes("lager", 1000);

        assertThat(text.length()).isLessThanOrEqualTo(1000);
        assertThat(text).endsWith("…");
    }

    @Test
    void sonderzeichenInDerFrageSindHarmlos() {
        service.init(BEISPIEL);
        assertThat(service.relevantes("'; DROP TABLE x; -- <script>alert(1)</script> Lager", 3000)).contains("Seite: Lager");
        assertThat(service.relevantes("a".repeat(100_000), 3000)).isEmpty();
    }

    // ------------------------------------------------------------------ echte Wissensbasis (Prüffragen)

    private KiWissenService echt() {
        KiWissenService s = new KiWissenService();
        s.laden();
        return s;
    }

    private static String ersterAbschnitt(String text) {
        int ende = text.indexOf("\n\n");
        return ende < 0 ? text : text.substring(0, ende);
    }

    @Test
    void dieMitgelieferteWissensbasisIstGefuellt() {
        KiWissenService s = echt();

        assertThat(s.anzahlAbschnitte()).as("Seiten, Anleitungen und Menüübersicht").isGreaterThan(80);
        assertThat(s.grenzen()).contains("Dokumentnummern").contains("nicht einstellen");
    }

    @Test
    void pruefFragenFindenDieRichtigenSeiten() {
        KiWissenService s = echt();

        assertThat(s.relevantes("Wo finde ich den Lagerbestand und wie buche ich einen Zugang?", 6000)).contains("## Seite: Lager");
        assertThat(s.relevantes("Wie lege ich einen neuen Artikel an?", 6000)).contains("## Seite: Artikel");
        assertThat(s.relevantes("Wo sehe ich meine offenen Posten?", 6000)).contains("## Seite: Offene Posten");
        assertThat(s.relevantes("Wo stelle ich die Firmendaten und das Logo ein?", 6000)).contains("## Seite: Firma");
        assertThat(s.relevantes("Wie erfasse ich einen Urlaubsantrag?", 6000)).contains("Urlaubsanträge");
        assertThat(s.relevantes("Wo ist der Kalender für Termine?", 6000)).contains("## Seite: Kalender");
        assertThat(s.relevantes("Wie schalte ich die Mietabrechnung aus?", 6000)).contains("Module");
    }

    @Test
    void dieMenuestrukturIstEinBlockDerBeiNavigationsfragenKommt() {
        KiWissenService s = echt();

        String text = s.relevantes("In welchem Hauptreiter ist der Menüpunkt Mietabrechnung?", 6000);

        assertThat(text).contains("## Menüstruktur des Programms").contains("Mietabrechnung");
    }

    @Test
    void dieWissensbasisEnthaeltKeinenCodeUndKeineGeheimnisse() {
        KiWissenService s = echt();
        String alles = s.relevantes("Seite Einstellungen Firma Lager Artikel Kunden Wartung KI Telefon E-Mail", 1_000_000);

        assertThat(alles).doesNotContain("className").doesNotContain("password=").doesNotContain("Bearer ");
        assertThat(alles).doesNotContainPattern("sk-[A-Za-z0-9]{20,}");
        assertThat(ersterAbschnitt(alles)).isNotBlank();
    }
}
