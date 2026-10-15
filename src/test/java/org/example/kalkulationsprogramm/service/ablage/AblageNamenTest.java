package org.example.kalkulationsprogramm.service.ablage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class AblageNamenTest {

    @Test
    void ordnerEntferntPfadtrennerUndSonderzeichen() {
        assertThat(AblageNamen.ordner("2026/014 - Anbau: Halle?", "x")).isEqualTo("2026-014 - Anbau Halle");
        assertThat(AblageNamen.ordner("a\\b*c\"d<e>f|g", "x")).isEqualTo("a-b c d e f g");
        assertThat(AblageNamen.ordner("Müller & Söhne GmbH (Würzburg)", "x")).isEqualTo("Müller & Söhne GmbH (Würzburg)");
    }

    @Test
    void ordnerKannNieAusDemZielHinausFuehren() {
        assertThat(AblageNamen.ordner("..", "Ersatz")).isEqualTo("Ersatz");
        assertThat(AblageNamen.ordner(".", "Ersatz")).isEqualTo("Ersatz");
        assertThat(AblageNamen.ordner("../../etc/passwd", "Ersatz")).isEqualTo("etc-passwd");
        assertThat(AblageNamen.ordner("...versteckt", "Ersatz")).doesNotStartWith(".");
        assertThat(AblageNamen.datei("../../../root/.ssh/id_rsa", "Ersatz", null)).doesNotContain("/").doesNotContain("\\");
    }

    @Test
    void leereUndLeerzeichenNamenWerdenErsetzt() {
        assertThat(AblageNamen.ordner(null, "Projekt 7")).isEqualTo("Projekt 7");
        assertThat(AblageNamen.ordner("   ", "Projekt 7")).isEqualTo("Projekt 7");
        assertThat(AblageNamen.ordner("///", "Projekt 7")).isEqualTo("Projekt 7");
        assertThat(AblageNamen.datei(null, "Dokument 3", "abc.pdf")).isEqualTo("Dokument 3.pdf");
    }

    @Test
    void windowsEndungenUndReservierteNamen() {
        assertThat(AblageNamen.ordner("CON", "x")).isEqualTo("_CON");
        assertThat(AblageNamen.ordner("nul", "x")).isEqualTo("_nul");
        assertThat(AblageNamen.datei("com1.txt", "x", null)).isEqualTo("_com1.txt");
        assertThat(AblageNamen.ordner("Plan. ", "x")).isEqualTo("Plan");
        assertThat(AblageNamen.ordner("Konsole", "x")).isEqualTo("Konsole");
        assertThat(AblageNamen.ordner("Anbau Halle / Süd", "x")).isEqualTo("Anbau Halle - Süd");
        assertThat(AblageNamen.ordner("2026/10/00014", "x")).isEqualTo("2026-10-00014");
    }

    @Test
    void dateinameBehaeltDieEndungAuchBeiLangenNamen() {
        String lang = "A".repeat(300) + ".pdf";
        String name = AblageNamen.datei(lang, "x", null);
        assertThat(name).hasSizeLessThanOrEqualTo(AblageNamen.MAX_DATEI).endsWith(".pdf");
        assertThat(AblageNamen.ordner("B".repeat(300), "x")).hasSizeLessThanOrEqualTo(AblageNamen.MAX_ORDNER);
    }

    @Test
    void endungKommtNotfallsVomGespeichertenNamen() {
        assertThat(AblageNamen.datei("Rechnung ohne Endung", "x", "uuid-123.pdf")).isEqualTo("Rechnung ohne Endung.pdf");
        assertThat(AblageNamen.datei("Plan.PDF", "x", null)).isEqualTo("Plan.PDF");
        assertThat(AblageNamen.endung("archiv.tar.gz")).isEqualTo(".gz");
        assertThat(AblageNamen.endung("seltsam.diesehrlangeendung")).isEmpty();
        assertThat(AblageNamen.endung(".versteckt")).isEmpty();
        assertThat(AblageNamen.endung("ende.")).isEmpty();
    }

    @Test
    void gleicheNamenWerdenDeterministischDurchnummeriert() {
        List<String> ergebnis = AblageNamen.eindeutig(List.of(
                "P/Plan.pdf", "P/plan.PDF", "P/Plan.pdf", "P/Anderes.pdf", "Q/Plan.pdf"));

        assertThat(ergebnis).containsExactly("P/Plan.pdf", "P/plan (2).PDF", "P/Plan (3).pdf", "P/Anderes.pdf", "Q/Plan.pdf");
        assertThat(AblageNamen.eindeutig(List.of("P/Plan.pdf", "P/plan.PDF", "P/Plan.pdf"))).as("gleiche Eingabe, gleiche Namen")
                .isEqualTo(AblageNamen.eindeutig(List.of("P/Plan.pdf", "P/plan.PDF", "P/Plan.pdf")));
    }

    @Test
    void nummerierterNameKollidiertNichtMitEinemEchtenDieserArt() {
        List<String> ergebnis = AblageNamen.eindeutig(List.of("P/Plan.pdf", "P/Plan (2).pdf", "P/Plan.pdf"));

        assertThat(ergebnis).doesNotHaveDuplicates();
        assertThat(ergebnis.stream().map(String::toLowerCase).distinct()).hasSize(3);
    }
}
