package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class ArtikelVariantenRepositoryTest {

    @Autowired
    private ArtikelRepository artikelRepository;

    private ArtikelWerkstoffe grundartikel() {
        ArtikelWerkstoffe g = new ArtikelWerkstoffe();
        g.setGrundartikel(true);
        g.setKurzbezeichnung("QH");
        g.setProduktname("Quadratrohr");
        return artikelRepository.saveAndFlush(g);
    }

    @Test
    void variantenZeigenAufIhrenGrundartikel() {
        ArtikelWerkstoffe g = grundartikel();
        ArtikelWerkstoffe v = new ArtikelWerkstoffe();
        v.setProduktname("QH 40x40x2");
        v.setGrundartikelRef(g);
        artikelRepository.saveAndFlush(v);
        ArtikelWerkstoffe fremd = new ArtikelWerkstoffe();
        fremd.setProduktname("Anderes");
        artikelRepository.saveAndFlush(fremd);

        assertThat(artikelRepository.findByGrundartikelRef(g)).extracting(Artikel::getProduktname).containsExactly("QH 40x40x2");
    }

    @Test
    void kurzbezeichnungIstOhneRuecksichtAufGrossUndKleinschreibungEindeutigPruefbar() {
        grundartikel();

        assertThat(artikelRepository.existsByGrundartikelTrueAndKurzbezeichnungIgnoreCase("qh")).isTrue();
        assertThat(artikelRepository.existsByGrundartikelTrueAndKurzbezeichnungIgnoreCase("RR")).isFalse();
    }

    @Test
    void eineVarianteMitGleicherKurzbezeichnungZaehltNichtAlsGrundartikel() {
        ArtikelWerkstoffe v = new ArtikelWerkstoffe();
        v.setKurzbezeichnung("QH");
        artikelRepository.saveAndFlush(v);

        assertThat(artikelRepository.existsByGrundartikelTrueAndKurzbezeichnungIgnoreCase("QH")).isFalse();
    }
}
