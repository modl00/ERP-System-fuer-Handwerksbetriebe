package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.ArtikelWerkstoffe;
import org.example.kalkulationsprogramm.domain.Verrechnungseinheit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import jakarta.persistence.EntityManager;

/** Ein im Programm angelegter Artikel wird zum Halbzeug, sobald er Gewicht oder Maße bekommen soll (#228). Echte Datenbank (H2), Dummy-Daten. */
@DataJpaTest
class ArtikelWerkstoffDatenAnlegenTest {

    @Autowired
    private ArtikelRepository artikelRepository;

    @Autowired
    private EntityManager em;

    private Artikel einfacherArtikel() {
        Artikel a = new Artikel();
        a.setProduktname("Konsole");
        a.setVerrechnungseinheit(Verrechnungseinheit.STUECK);
        return artikelRepository.saveAndFlush(a);
    }

    @Test
    void ausEinemEinfachenArtikelWirdHalbzeugMitLeerenWerten() {
        Long id = einfacherArtikel().getId();
        assertThat(artikelRepository.findById(id).orElseThrow()).isNotInstanceOf(ArtikelWerkstoffe.class);

        int angelegt = artikelRepository.legeWerkstoffDatenAn(id);

        assertThat(angelegt).isEqualTo(1);
        Artikel neu = artikelRepository.findById(id).orElseThrow();
        assertThat(neu).isInstanceOf(ArtikelWerkstoffe.class);
        assertThat(neu.getProduktname()).isEqualTo("Konsole");
        assertThat(((ArtikelWerkstoffe) neu).getMasse()).isNull();
        assertThat(((ArtikelWerkstoffe) neu).isGeschliffen()).isFalse();
    }

    @Test
    void danachLassenSichGewichtUndFlaecheSpeichernUndLesen() {
        Long id = einfacherArtikel().getId();
        artikelRepository.legeWerkstoffDatenAn(id);

        ArtikelWerkstoffe aw = (ArtikelWerkstoffe) artikelRepository.findById(id).orElseThrow();
        aw.setKgJeStueck(new BigDecimal("2.5"));
        aw.setM2JeStueck(new BigDecimal("0.3"));
        aw.setFlaecheJeQm(new BigDecimal("1.8"));
        aw.setGewichtHerkunft("HAND");
        artikelRepository.saveAndFlush(aw);
        em.clear();

        ArtikelWerkstoffe gelesen = (ArtikelWerkstoffe) artikelRepository.findById(id).orElseThrow();
        assertThat(gelesen.getKgJeStueck()).isEqualByComparingTo("2.5");
        assertThat(gelesen.getM2JeStueck()).isEqualByComparingTo("0.3");
        assertThat(gelesen.getFlaecheJeQm()).isEqualByComparingTo("1.8");
        assertThat(gelesen.getGewichtHerkunft()).isEqualTo("HAND");
    }

    @Test
    void esIstHarmlosWennDerArtikelSchonHalbzeugIst() {
        Long id = einfacherArtikel().getId();
        artikelRepository.legeWerkstoffDatenAn(id);

        assertThat(artikelRepository.legeWerkstoffDatenAn(id)).as("zweiter Aufruf legt nichts doppelt an").isZero();
        assertThat(artikelRepository.findById(id).orElseThrow()).isInstanceOf(ArtikelWerkstoffe.class);
    }

    @Test
    void einUnbekannterArtikelLegtNichtsAn() {
        assertThat(artikelRepository.legeWerkstoffDatenAn(999_999L)).isZero();
    }

    @Test
    void dieZuschnittFormelWirdMitFeldernGespeichertUndGelesen() {
        Long id = einfacherArtikel().getId();
        artikelRepository.legeWerkstoffDatenAn(id);

        ArtikelWerkstoffe aw = (ArtikelWerkstoffe) artikelRepository.findById(id).orElseThrow();
        aw.setFormelFelderJson("[{\"name\":\"L\",\"bezeichnung\":\"Länge\",\"einheit\":\"mm\",\"standard\":1000}]");
        aw.setFormelGewicht("(L/1000) * kg_je_meter");
        aw.setFormelFlaeche("(L/1000) * m2_je_meter");
        artikelRepository.saveAndFlush(aw);
        em.clear();

        ArtikelWerkstoffe gelesen = (ArtikelWerkstoffe) artikelRepository.findById(id).orElseThrow();
        assertThat(gelesen.getFormelGewicht()).isEqualTo("(L/1000) * kg_je_meter");
        assertThat(gelesen.getFormelFlaeche()).isEqualTo("(L/1000) * m2_je_meter");
        assertThat(gelesen.getFormelFelderJson()).contains("Länge");
    }
}
