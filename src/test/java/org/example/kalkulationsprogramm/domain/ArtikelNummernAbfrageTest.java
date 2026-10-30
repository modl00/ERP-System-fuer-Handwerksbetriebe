package org.example.kalkulationsprogramm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** Die Abfragen für den Abgleich der Metallbau-Vorlagen laufen gegen die echte Datenbank-Abbildung (Start-Prüfung der @Query). */
@DataJpaTest
class ArtikelNummernAbfrageTest {

    @Autowired
    private ArtikelRepository repo;

    @Test
    void liefertNummernUndNamenKleingeschrieben() {
        Artikel a = new Artikel();
        a.setProduktname("Rundrohr 42,4 x 2");
        a.setArtikelnummer("VL-RR-42.4x2");
        repo.saveAndFlush(a);
        Artikel ohneNummer = new Artikel();
        ohneNummer.setProduktname("Blech 3 mm");
        repo.saveAndFlush(ohneNummer);

        assertThat(repo.alleArtikelnummern()).containsExactly("VL-RR-42.4x2");
        assertThat(repo.alleProduktnamenKlein()).contains("rundrohr 42,4 x 2", "blech 3 mm");
    }
}
