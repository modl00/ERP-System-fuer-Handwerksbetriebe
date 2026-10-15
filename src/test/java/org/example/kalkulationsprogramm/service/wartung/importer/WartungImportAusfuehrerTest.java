package org.example.kalkulationsprogramm.service.wartung.importer;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.domain.wartung.WartungErgebnis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Die kleinen Regeln der Übernahme; der Ablauf selbst ist gegen eine echte MySQL-Datenbank mit der echten Exportdatei geprüft. */
class WartungImportAusfuehrerTest {

    @Test
    @DisplayName("Der Schlüssel einer neuen Variante ist klein, ohne Umlaute und höchstens 30 Zeichen lang")
    void schluessel() {
        assertThat(WartungImportAusfuehrer.schluesselAus("Standard ohne Handtaster")).isEqualTo("standard_ohne_handtaster");
        assertThat(WartungImportAusfuehrer.schluesselAus("Größe & Öffner (neu)")).isEqualTo("groesse_oeffner_neu");
        assertThat(WartungImportAusfuehrer.schluesselAus("!!!")).isEqualTo("variante");
        assertThat(WartungImportAusfuehrer.schluesselAus("Eine sehr lange Bezeichnung einer Variante der Feststellanlage")).hasSize(30);
    }

    @Test
    @DisplayName("Ein Wert kommt nur an die Liste, wenn er dort noch nicht steht (Groß- und Kleinschreibung egal)")
    void option() {
        assertThat(WartungImportAusfuehrer.mitOption("Hörmann\nGeze", "Dorma")).isEqualTo("Hörmann\nGeze\nDorma");
        assertThat(WartungImportAusfuehrer.mitOption("Hörmann\nGeze", "geze")).isNull();
        assertThat(WartungImportAusfuehrer.mitOption(null, " Dorma ")).isEqualTo("Dorma");
        assertThat(WartungImportAusfuehrer.mitOption("  ", "Dorma")).isEqualTo("Dorma");
    }

    @Test
    @DisplayName("Ergebnisse aus HandwerkPro: i.O., n.i.O., n.a.; leer und Unbekanntes bleiben offen")
    void ergebnis() {
        assertThat(WartungImportAusfuehrer.ergebnis("i.O.")).isEqualTo(WartungErgebnis.IO);
        assertThat(WartungImportAusfuehrer.ergebnis("n.i.O.")).isEqualTo(WartungErgebnis.NIO);
        assertThat(WartungImportAusfuehrer.ergebnis("n.a.")).isEqualTo(WartungErgebnis.NA);
        assertThat(WartungImportAusfuehrer.ergebnis(null)).isNull();
        assertThat(WartungImportAusfuehrer.ergebnis("vielleicht")).isNull();
    }
}
