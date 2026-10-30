package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.dto.Kunde.KundeAnsprechpartnerDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeAnsprechpartnerSpeichernDto;
import org.example.kalkulationsprogramm.repository.KundeAnsprechpartnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Mehrere Ansprechpartner je Kunde, Hauptansprechpartner und der Abgleich mit dem alten Textfeld (gegen die Test-Datenbank). */
@DataJpaTest
@Import(KundeAnsprechpartnerService.class)
class KundeAnsprechpartnerServiceTest {

    @Autowired private TestEntityManager em;
    @Autowired private KundeAnsprechpartnerService service;
    @Autowired private KundeAnsprechpartnerRepository repository;

    private Kunde kunde;
    private Kunde anderer;

    @BeforeEach
    void setUp() {
        kunde = kunde("K-1", "Max Mustermann GmbH");
        anderer = kunde("K-2", "Erika Beispiel AG");
        em.flush();
        em.clear();
    }

    private Kunde kunde(String nummer, String name) {
        Kunde k = new Kunde();
        k.setKundennummer(nummer);
        k.setName(name);
        return em.persist(k);
    }

    private static KundeAnsprechpartnerSpeichernDto eingabe(String name, Boolean haupt) {
        return new KundeAnsprechpartnerSpeichernDto(name, "Hausmeister", "0931 123456", "0171 1234567", "hans@example.org", "Schlüssel im Büro", haupt);
    }

    private String textfeld(Long kundeId) {
        em.flush();
        em.clear();
        return em.find(Kunde.class, kundeId).getAnsprechspartner();
    }

    @Test
    @DisplayName("Der erste Ansprechpartner wird automatisch zum Hauptansprechpartner, sein Name steht im Textfeld des Kunden")
    void ersterIstHaupt() {
        KundeAnsprechpartnerDto a = service.anlegen(kunde.getId(), eingabe("Hans Beispiel", null));

        assertThat(a.haupt()).isTrue();
        assertThat(a.funktion()).isEqualTo("Hausmeister");
        assertThat(a.email()).isEqualTo("hans@example.org");
        assertThat(textfeld(kunde.getId())).isEqualTo("Hans Beispiel");
    }

    @Test
    @DisplayName("Weitere Ansprechpartner sind erst keine Hauptansprechpartner; die Liste zeigt den Hauptansprechpartner zuerst")
    void reihenfolge() {
        service.anlegen(kunde.getId(), eingabe("Zacharias Zeller", null));
        service.anlegen(kunde.getId(), eingabe("Anna Aumann", null));
        service.anlegen(kunde.getId(), eingabe("Berta Bauer", null));

        List<KundeAnsprechpartnerDto> liste = service.liste(kunde.getId());

        assertThat(liste).extracting(KundeAnsprechpartnerDto::name).containsExactly("Zacharias Zeller", "Anna Aumann", "Berta Bauer");
        assertThat(liste).extracting(KundeAnsprechpartnerDto::haupt).containsExactly(true, false, false);
    }

    @Test
    @DisplayName("Einen anderen zum Hauptansprechpartner machen: Der bisherige verliert die Markierung, das Textfeld folgt")
    void hauptWechseln() {
        service.anlegen(kunde.getId(), eingabe("Hans Beispiel", null));
        KundeAnsprechpartnerDto zweiter = service.anlegen(kunde.getId(), eingabe("Erika Muster", null));

        service.hauptSetzen(kunde.getId(), zweiter.id());

        assertThat(service.liste(kunde.getId())).extracting(KundeAnsprechpartnerDto::name, KundeAnsprechpartnerDto::haupt)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Erika Muster", true), org.assertj.core.groups.Tuple.tuple("Hans Beispiel", false));
        assertThat(textfeld(kunde.getId())).isEqualTo("Erika Muster");
    }

    @Test
    @DisplayName("Beim Anlegen oder Ändern mit haupt=true wird der bisherige Hauptansprechpartner abgelöst (immer nur einer)")
    void nurEinerIstHaupt() {
        service.anlegen(kunde.getId(), eingabe("Hans Beispiel", null));
        KundeAnsprechpartnerDto neu = service.anlegen(kunde.getId(), eingabe("Erika Muster", true));
        KundeAnsprechpartnerDto dritter = service.anlegen(kunde.getId(), eingabe("Otto Otter", null));
        service.aendern(kunde.getId(), dritter.id(), eingabe("Otto Otter", true));

        assertThat(neu.haupt()).isTrue();
        assertThat(service.liste(kunde.getId())).filteredOn(KundeAnsprechpartnerDto::haupt)
                .extracting(KundeAnsprechpartnerDto::name).containsExactly("Otto Otter");
        assertThat(textfeld(kunde.getId())).isEqualTo("Otto Otter");
    }

    @Test
    @DisplayName("Ändern: alle Felder werden gespeichert, Leeres wird zu null und Namen werden gekürzt")
    void aendern() {
        KundeAnsprechpartnerDto a = service.anlegen(kunde.getId(), eingabe("Hans Beispiel", null));

        KundeAnsprechpartnerDto neu = service.aendern(kunde.getId(), a.id(),
                new KundeAnsprechpartnerSpeichernDto("  Hans Neu  ", "  ", null, "0172 555", "", "Notiz", null));

        assertThat(neu.name()).isEqualTo("Hans Neu");
        assertThat(neu.funktion()).isNull();
        assertThat(neu.telefon()).isNull();
        assertThat(neu.mobil()).isEqualTo("0172 555");
        assertThat(neu.email()).isNull();
        assertThat(neu.notizen()).isEqualTo("Notiz");
        assertThat(neu.haupt()).isTrue();
        assertThat(textfeld(kunde.getId())).isEqualTo("Hans Neu");
    }

    @Test
    @DisplayName("Löschen des Hauptansprechpartners: der nächste rückt nach; ist es der letzte, wird das Textfeld leer")
    void loeschen() {
        KundeAnsprechpartnerDto a = service.anlegen(kunde.getId(), eingabe("Hans Beispiel", null));
        KundeAnsprechpartnerDto b = service.anlegen(kunde.getId(), eingabe("Erika Muster", null));

        service.loeschen(kunde.getId(), a.id());

        assertThat(service.liste(kunde.getId())).extracting(KundeAnsprechpartnerDto::name, KundeAnsprechpartnerDto::haupt)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Erika Muster", true));
        assertThat(textfeld(kunde.getId())).isEqualTo("Erika Muster");

        service.loeschen(kunde.getId(), b.id());

        assertThat(service.liste(kunde.getId())).isEmpty();
        assertThat(textfeld(kunde.getId())).isNull();
    }

    @Test
    @DisplayName("Ein Ansprechpartner eines anderen Kunden ist 'nicht gefunden': ändern, löschen und Haupt setzen laufen ins Leere")
    void fremderKunde() {
        KundeAnsprechpartnerDto fremd = service.anlegen(anderer.getId(), eingabe("Fritz Fremd", null));

        assertThat(statusVon(() -> service.aendern(kunde.getId(), fremd.id(), eingabe("X", null)))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusVon(() -> service.loeschen(kunde.getId(), fremd.id()))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusVon(() -> service.hauptSetzen(kunde.getId(), fremd.id()))).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(repository.countByKundeId(anderer.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Unbekannte Kunden-Nummer: 404 mit Meldung (auch 0, negativ und Long.MAX_VALUE)")
    void unbekannterKunde() {
        for (long id : new long[] {0L, -1L, Long.MAX_VALUE, 424242L}) {
            assertThatThrownBy(() -> service.liste(id)).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(e.getReason()).isEqualTo("Der Kunde wurde nicht gefunden.");
            });
        }
    }

    @Test
    @DisplayName("Höchstens 50 Ansprechpartner je Kunde: der 51. wird mit 409 und Hinweis abgelehnt")
    void obergrenze() {
        for (int i = 1; i <= KundeAnsprechpartnerService.MAX_JE_KUNDE; i++) {
            service.anlegen(kunde.getId(), eingabe("Person " + i, null));
        }

        assertThatThrownBy(() -> service.anlegen(kunde.getId(), eingabe("Eine zu viel", null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(e.getReason()).contains("50");
                });
    }

    @Test
    @DisplayName("SQL und Skript im Namen werden unverändert als Text gespeichert")
    void sonderzeichen() {
        service.anlegen(kunde.getId(), eingabe("'; DROP TABLE kunde_ansprechpartner; --", null));
        KundeAnsprechpartnerDto xss = service.anlegen(kunde.getId(), eingabe("<script>alert(1)</script>", null));

        assertThat(xss.name()).isEqualTo("<script>alert(1)</script>");
        assertThat(service.liste(kunde.getId())).hasSize(2);
    }

    // ── Altbestand: nur das Textfeld "Ansprechpartner" ──────────────────────

    @Test
    @DisplayName("Kunde nur mit dem alten Textfeld: Beim ersten Öffnen der Liste wird daraus der Hauptansprechpartner (nur einmal)")
    void altbestand() {
        Kunde alt = em.find(Kunde.class, kunde.getId());
        alt.setAnsprechspartner("  Hans Altmann ");
        em.flush();
        em.clear();

        assertThat(service.anzahl(em.find(Kunde.class, kunde.getId()))).isEqualTo(1);
        assertThat(repository.countByKundeId(kunde.getId())).isZero();

        List<KundeAnsprechpartnerDto> erste = service.liste(kunde.getId());
        List<KundeAnsprechpartnerDto> zweite = service.liste(kunde.getId());

        assertThat(erste).extracting(KundeAnsprechpartnerDto::name, KundeAnsprechpartnerDto::haupt)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Hans Altmann", true));
        assertThat(zweite).hasSize(1);
    }

    @Test
    @DisplayName("Direkt-Aufruf zum Anlegen bei einem Kunden nur mit altem Textfeld: Der alte Name bleibt Hauptansprechpartner, der neue ist keiner")
    void anlegenBeiAltbestand() {
        Kunde alt = em.find(Kunde.class, kunde.getId());
        alt.setAnsprechspartner("Hans Altmann");
        em.flush();
        em.clear();

        KundeAnsprechpartnerDto neu = service.anlegen(kunde.getId(), eingabe("Erika Muster", null));

        assertThat(neu.haupt()).isFalse();
        assertThat(service.liste(kunde.getId())).extracting(KundeAnsprechpartnerDto::name, KundeAnsprechpartnerDto::haupt)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Hans Altmann", true), org.assertj.core.groups.Tuple.tuple("Erika Muster", false));
        assertThat(textfeld(kunde.getId())).isEqualTo("Hans Altmann");
    }

    @Test
    @DisplayName("Das Spiegeln des Namens erhöht die Versionsnummer des Kunden nicht (ein gleichzeitig gespeichertes Kundenformular scheitert nicht)")
    void spiegelnLaesstDieVersionStehen() {
        service.anlegen(kunde.getId(), eingabe("Hans Beispiel", null));
        em.flush();
        em.clear();
        Long vorher = em.find(Kunde.class, kunde.getId()).getVersion();

        service.anlegen(kunde.getId(), eingabe("Erika Muster", true));
        em.flush();
        em.clear();

        Kunde danach = em.find(Kunde.class, kunde.getId());
        assertThat(danach.getAnsprechspartner()).isEqualTo("Erika Muster");
        assertThat(danach.getVersion()).isEqualTo(vorher);
    }

    @Test
    @DisplayName("Aus dem Kundenformular: das Feld gilt nur, solange es noch keine Liste gibt; mit Liste bleibt sie maßgebend")
    void kundenformular() {
        Kunde k = em.find(Kunde.class, kunde.getId());

        service.ausKundenformular(k, "Hans Formular");
        assertThat(service.hatListe(k.getId())).isTrue();
        assertThat(service.liste(k.getId())).extracting(KundeAnsprechpartnerDto::name).containsExactly("Hans Formular");

        service.ausKundenformular(k, "Jemand Anderes");
        assertThat(service.liste(k.getId())).extracting(KundeAnsprechpartnerDto::name).containsExactly("Hans Formular");
    }

    @Test
    @DisplayName("Aus dem Kundenformular ohne Namen entsteht kein Ansprechpartner")
    void kundenformularLeer() {
        Kunde k = em.find(Kunde.class, kunde.getId());

        service.ausKundenformular(k, "   ");
        service.ausKundenformular(k, null);

        assertThat(service.hatListe(k.getId())).isFalse();
        assertThat(service.anzahl(k)).isZero();
    }

    private static HttpStatus statusVon(Runnable aufruf) {
        try {
            aufruf.run();
            return HttpStatus.OK;
        } catch (ResponseStatusException e) {
            return HttpStatus.valueOf(e.getStatusCode().value());
        }
    }
}
