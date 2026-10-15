package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AktivitaetKatalogTest {

    private static final Set<String> ARTEN = Set.of("ANGELEGT", "GEAENDERT", "GELOESCHT", "VERSENDET", "GEBUCHT", "ZUGEORDNET", "ABGESCHLOSSEN");

    @Test
    @DisplayName("Die Namen der Platzhalter sind egal: {projektID} und {id} meinen dasselbe; ein Schrägstrich am Ende auch")
    void platzhalter() {
        assertThat(AktivitaetKatalog.finde("PUT", "/api/projekte/{projektID}")).get().extracting(AktivitaetKatalog.Regel::text).isEqualTo("Projekt geändert");
        assertThat(AktivitaetKatalog.finde("put", "/api/projekte/{id}/")).isPresent();
        assertThat(AktivitaetKatalog.finde("POST", "/api/projekte/{projektId}/material-abrechnung/{dokumentId}")).get().extracting(AktivitaetKatalog.Regel::art).isEqualTo("ZUGEORDNET");
        assertThat(AktivitaetKatalog.finde("POST", "/api/zeitverwaltung/monatsabschluesse/{a}/{b}/{c}/abschliessen")).get().extracting(AktivitaetKatalog.Regel::objektTyp).isEqualTo("Mitarbeiter");
    }

    @Test
    @DisplayName("Unbekannte Adressen und Methoden werden nicht protokolliert; Lesen ist nie dabei")
    void unbekannt() {
        assertThat(AktivitaetKatalog.finde("GET", "/api/projekte/{id}")).isEmpty();
        assertThat(AktivitaetKatalog.finde("POST", "/api/auth/login")).isEmpty();
        assertThat(AktivitaetKatalog.finde("POST", "/api/zeiterfassung/nachtrag")).isEmpty();
        assertThat(AktivitaetKatalog.finde("DELETE", "/api/projekte/{id}")).isEmpty();
        assertThat(AktivitaetKatalog.finde(null, "/api/projekte")).isEmpty();
        assertThat(AktivitaetKatalog.finde("POST", null)).isEmpty();
        assertThat(AktivitaetKatalog.alle()).noneMatch(r -> r.methode().equals("GET"));
    }

    @Test
    @DisplayName("Jede Regel hat eine bekannte Art, einen Satz für die Anzeige und ein eindeutiges Muster; Regeln ohne Objekt stehen ohne Objektart")
    void regelnSindVollstaendig() {
        List<AktivitaetKatalog.Regel> regeln = AktivitaetKatalog.alle();

        assertThat(regeln).isNotEmpty().allSatisfy(r -> {
            assertThat(ARTEN).contains(r.art());
            assertThat(r.text()).isNotBlank();
            assertThat(r.muster()).startsWith("/api/").doesNotContain("{projekt");
        });
        assertThat(regeln.stream().map(r -> r.methode() + " " + r.muster()).distinct().count()).isEqualTo(regeln.size());
        assertThat(AktivitaetKatalog.finde("POST", "/api/emails/send")).get().extracting(AktivitaetKatalog.Regel::objektTyp).isNull();
    }

    @Test
    @DisplayName("Kein Satz der Anzeige verrät Inhalte: nur Art der Handlung, keine Platzhalter für Beträge oder Texte")
    void keineInhalte() {
        assertThat(AktivitaetKatalog.alle()).allSatisfy(r -> assertThat(r.text()).doesNotContain("{").doesNotContain("€").doesNotContainIgnoringCase("betrag"));
    }
}
