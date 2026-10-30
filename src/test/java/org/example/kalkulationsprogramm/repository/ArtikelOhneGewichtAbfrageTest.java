package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Profilform;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/** Die Abfragen für „Gewichte ergänzen“ (Issue #252) gegen eine echte (H2-)Datenbank. */
@DataJpaTest
class ArtikelOhneGewichtAbfrageTest {

    @Autowired ArtikelRepository artikel;

    private ArtikelWerkstoffe aw(String name, Profilform form, BigDecimal masse, BigDecimal qm, BigDecimal stueck) {
        ArtikelWerkstoffe a = new ArtikelWerkstoffe();
        a.setProduktname(name);
        a.setProfilform(form);
        a.setMasse(masse);
        a.setMassePerQm(qm);
        a.setKgJeStueck(stueck);
        return artikel.saveAndFlush(a);
    }

    @Test
    void nurWerkstoffArtikelMitFormUndOhneJedeGewichtsangabeKommenInFrage() {
        aw("Ohne Gewicht", Profilform.RUNDROHR, null, null, null);
        aw("Blech ohne Gewicht", Profilform.BLECH, null, null, null);
        aw("Mit Meterwert", Profilform.RUNDROHR, new BigDecimal("2"), null, null);
        aw("Mit Blechwert", Profilform.BLECH, null, new BigDecimal("23.55"), null);
        aw("Mit Stückwert", Profilform.RUNDROHR, null, null, new BigDecimal("1"));
        aw("Ohne Form", null, null, null, null);
        Artikel gewoehnlich = new Artikel();
        gewoehnlich.setProduktname("Schraube");
        artikel.saveAndFlush(gewoehnlich);

        List<ArtikelWerkstoffe> treffer = artikel.findeOhneGewicht(PageRequest.of(0, 10));

        assertThat(treffer).extracting(ArtikelWerkstoffe::getProduktname).containsExactly("Ohne Gewicht", "Blech ohne Gewicht");
        assertThat(artikel.zaehleOhneGewicht()).isEqualTo(2);
    }

    @Test
    void dieSeitenGroesseBegrenztDieListeAberDieAnzahlBleibtVollstaendig() {
        for (int i = 0; i < 5; i++) {
            aw("A" + i, Profilform.RUNDROHR, null, null, null);
        }
        assertThat(artikel.findeOhneGewicht(PageRequest.of(0, 3))).hasSize(3);
        assertThat(artikel.zaehleOhneGewicht()).isEqualTo(5);
    }
}
