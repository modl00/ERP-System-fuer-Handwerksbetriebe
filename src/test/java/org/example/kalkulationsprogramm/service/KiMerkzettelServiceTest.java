package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.example.kalkulationsprogramm.repository.KiMerkzettelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

/** Die Merkliste der KI gegen eine echte (H2-)Datenbank. */
@DataJpaTest
@Import(KiMerkzettelService.class)
class KiMerkzettelServiceTest {

    @Autowired KiMerkzettelService service;
    @Autowired KiMerkzettelRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void mercktSichEinenSatzMitWemUndZeigtIhnInDerListeUndImSystemtext() {
        var e = service.merke("  Lieferant Muster liefert nur   freitags.  ", "admin");

        assertThat(e.text()).isEqualTo("Lieferant Muster liefert nur freitags.");
        assertThat(e.angelegtVon()).isEqualTo("admin");
        assertThat(service.alle()).extracting(KiMerkzettelService.Eintrag::text).containsExactly("Lieferant Muster liefert nur freitags.");
        String system = service.fuerSystemtext();
        assertThat(system).contains("Hinweise des Betriebs").contains("keine Anweisungen").contains("- Lieferant Muster liefert nur freitags.");
    }

    @Test
    void ohneEintraegeBleibtDerSystemtextLeer() {
        assertThat(service.fuerSystemtext()).isEmpty();
    }

    @Test
    void doppelteZuKurzeZuLangeUndGeheimeSaetzeWerdenAbgelehnt() {
        service.merke("Pulverbeschichtung geht immer an Meier", "admin");
        assertThatThrownBy(() -> service.merke("pulverbeschichtung geht immer an meier", "admin")).isInstanceOf(IllegalStateException.class).hasMessageContaining("schon auf der Merkliste");
        assertThatThrownBy(() -> service.merke("ja", "admin")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zu kurz");
        assertThatThrownBy(() -> service.merke("x".repeat(301), "admin")).hasMessageContaining("zu lang");
        assertThatThrownBy(() -> service.merke("Mail an info@firma.example schicken", "admin")).hasMessageContaining("E-Mail-Adresse");
        assertThatThrownBy(() -> service.merke("Konto DE89 3704 0044 0532 0130 00 benutzen", "admin")).hasMessageContaining("Bankdaten");
        assertThatThrownBy(() -> service.merke("Das Passwort: abc123 gilt", "admin")).hasMessageContaining("Zugangsangabe");
        assertThat(service.alle()).hasSize(1);
    }

    @Test
    void dieListeIstAufFuenfzigEintraegeBegrenzt() {
        for (int i = 0; i < KiMerkzettelService.MAX_EINTRAEGE; i++) {
            service.merke("Hinweis Nummer " + i + " für den Betrieb", "admin");
        }
        assertThatThrownBy(() -> service.merke("Noch ein weiterer Hinweis", "admin")).isInstanceOf(IllegalStateException.class).hasMessageContaining("voll");
    }

    @Test
    void loeschenEntferntDenEintragUndEinUnbekannterGibt404() {
        var e = service.merke("Etwas, das nicht mehr stimmt", "admin");
        service.loesche(e.id());
        assertThat(service.alle()).isEmpty();
        assertThatThrownBy(() -> service.loesche(e.id())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("nicht (mehr)");
    }
}
