package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.Kunde.KundeObjektDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeObjektSpeichernDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Objekte (Liegenschaften) im Kundenstamm: dieselben Datensätze wie in der Wartung, auch ohne Wartungsmodul nutzbar. */
@DataJpaTest
@Import(KundeObjektService.class)
class KundeObjektServiceTest {

    @Autowired private TestEntityManager em;
    @Autowired private KundeObjektService service;

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

    private static KundeObjektSpeichernDto eingabe(String bezeichnung) {
        return new KundeObjektSpeichernDto(bezeichnung, "Industriestraße 12", "97080", "Würzburg", "Hans Beispiel", "0931 123456", "Schlüssel beim Hausmeister");
    }

    private WartungTuer tuer(Long objektId, String name, boolean aktiv) {
        WartungTuer t = new WartungTuer();
        t.setObjekt(em.find(WartungObjekt.class, objektId));
        t.setBezeichnung(name);
        t.setAktiv(aktiv);
        return em.persist(t);
    }

    @Test
    @DisplayName("Objekt anlegen: alle Angaben werden gespeichert, es ist aktiv und hat noch keine Wartung")
    void anlegen() {
        KundeObjektDto o = service.anlegen(kunde.getId(), eingabe("  Werk Nord  "));

        assertThat(o.id()).isPositive();
        assertThat(o.bezeichnung()).isEqualTo("Werk Nord");
        assertThat(o.strasse()).isEqualTo("Industriestraße 12");
        assertThat(o.plz()).isEqualTo("97080");
        assertThat(o.ort()).isEqualTo("Würzburg");
        assertThat(o.ansprechpartnerName()).isEqualTo("Hans Beispiel");
        assertThat(o.ansprechpartnerTelefon()).isEqualTo("0931 123456");
        assertThat(o.notizen()).isEqualTo("Schlüssel beim Hausmeister");
        assertThat(o.aktiv()).isTrue();
        assertThat(o.anzahlWartungen()).isZero();
    }

    @Test
    @DisplayName("Liste: nur Objekte des Kunden, alphabetisch, mit Anzahl der aktiven Wartungen; Inaktive nur auf Wunsch")
    void liste() {
        KundeObjektDto b = service.anlegen(kunde.getId(), eingabe("Büro"));
        KundeObjektDto a = service.anlegen(kunde.getId(), eingabe("Autohaus"));
        service.anlegen(anderer.getId(), eingabe("Fremdes Objekt"));
        tuer(a.id(), "Tor 1", true);
        tuer(a.id(), "Tor 2", true);
        tuer(a.id(), "Altes Tor", false);
        KundeObjektDto weg = service.anlegen(kunde.getId(), eingabe("Abgerissen"));
        service.aktivSetzen(kunde.getId(), weg.id(), false);
        em.flush();
        em.clear();

        List<KundeObjektDto> aktive = service.liste(kunde.getId(), false);
        List<KundeObjektDto> alle = service.liste(kunde.getId(), true);

        assertThat(aktive).extracting(KundeObjektDto::bezeichnung).containsExactly("Autohaus", "Büro");
        assertThat(aktive).extracting(KundeObjektDto::anzahlWartungen).containsExactly(2, 0);
        assertThat(alle).extracting(KundeObjektDto::bezeichnung).containsExactly("Abgerissen", "Autohaus", "Büro");
        assertThat(b.bezeichnung()).isEqualTo("Büro");
        assertThat(service.anzahl(kunde.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("Ändern: Angaben werden ersetzt, Leeres wird zu null; die Anzahl der Wartungen bleibt sichtbar")
    void aendern() {
        KundeObjektDto o = service.anlegen(kunde.getId(), eingabe("Werk Nord"));
        tuer(o.id(), "Tor 1", true);
        em.flush();
        em.clear();

        KundeObjektDto neu = service.aendern(kunde.getId(), o.id(), new KundeObjektSpeichernDto("Werk Süd", " ", null, "Ulm", "", null, null));

        assertThat(neu.bezeichnung()).isEqualTo("Werk Süd");
        assertThat(neu.strasse()).isNull();
        assertThat(neu.ort()).isEqualTo("Ulm");
        assertThat(neu.ansprechpartnerName()).isNull();
        assertThat(neu.anzahlWartungen()).isEqualTo(1);
    }

    @Test
    @DisplayName("Deaktivieren und wieder aktivieren (weiches Löschen): das Objekt und seine Wartungen bleiben erhalten")
    void aktivSetzen() {
        KundeObjektDto o = service.anlegen(kunde.getId(), eingabe("Werk Nord"));
        tuer(o.id(), "Tor 1", true);

        KundeObjektDto aus = service.aktivSetzen(kunde.getId(), o.id(), false);
        assertThat(aus.aktiv()).isFalse();
        assertThat(service.liste(kunde.getId(), false)).isEmpty();
        assertThat(em.find(WartungObjekt.class, o.id())).isNotNull();

        KundeObjektDto an = service.aktivSetzen(kunde.getId(), o.id(), true);
        assertThat(an.aktiv()).isTrue();
        assertThat(an.anzahlWartungen()).isEqualTo(1);
    }

    @Test
    @DisplayName("Ein Objekt eines anderen Kunden ist 'nicht gefunden': ändern und deaktivieren laufen ins Leere")
    void fremdesObjekt() {
        KundeObjektDto fremd = service.anlegen(anderer.getId(), eingabe("Fremdes Objekt"));

        assertThatThrownBy(() -> service.aendern(kunde.getId(), fremd.id(), eingabe("Übernommen")))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.aktivSetzen(kunde.getId(), fremd.id(), false))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(service.liste(anderer.getId(), false)).extracting(KundeObjektDto::bezeichnung).containsExactly("Fremdes Objekt");
    }

    @Test
    @DisplayName("Unbekannter Kunde und ungültige Nummern (0, negativ, Long.MAX_VALUE): 404 mit Meldung")
    void unbekannterKunde() {
        for (long id : new long[] {0L, -1L, Long.MAX_VALUE, 424242L}) {
            assertThatThrownBy(() -> service.liste(id, false)).isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                assertThat(e.getReason()).isEqualTo("Der Kunde wurde nicht gefunden.");
            });
            assertThatThrownBy(() -> service.anlegen(id, eingabe("X"))).isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    @DisplayName("SQL und Skript in der Bezeichnung werden unverändert als Text gespeichert")
    void sonderzeichen() {
        KundeObjektDto sql = service.anlegen(kunde.getId(), eingabe("'; DROP TABLE wartung_objekt; --"));
        KundeObjektDto xss = service.anlegen(kunde.getId(), eingabe("<script>alert(1)</script>"));

        assertThat(sql.bezeichnung()).contains("DROP TABLE");
        assertThat(xss.bezeichnung()).isEqualTo("<script>alert(1)</script>");
        assertThat(service.liste(kunde.getId(), false)).hasSize(2);
    }

    @Test
    @DisplayName("Dasselbe Objekt erscheint in der Wartung (gleiche Tabelle): kein zweiter Bestand")
    void gleicheDatenWieDieWartung() {
        KundeObjektDto o = service.anlegen(kunde.getId(), eingabe("Werk Nord"));
        em.flush();
        em.clear();

        WartungObjekt gespeichert = em.find(WartungObjekt.class, o.id());

        assertThat(gespeichert.getKunde().getId()).isEqualTo(kunde.getId());
        assertThat(gespeichert.getBezeichnung()).isEqualTo("Werk Nord");
    }
}
