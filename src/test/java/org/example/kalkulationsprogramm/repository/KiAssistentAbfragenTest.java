package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Artikel;
import org.example.kalkulationsprogramm.domain.KiAktionProtokoll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/** Die Abfragen des KI-Assistenten gegen eine echte (H2-)Datenbank: Suche und Protokoll. */
@DataJpaTest
class KiAssistentAbfragenTest {

    @Autowired ArtikelRepository artikel;
    @Autowired KiAktionProtokollRepository protokoll;

    private Artikel a(String name, String nummer, String ean, String suchtext) {
        Artikel x = new Artikel();
        x.setProduktname(name);
        x.setArtikelnummer(nummer);
        x.setEan(ean);
        x.setSuchtext(suchtext);
        return artikel.saveAndFlush(x);
    }

    @BeforeEach
    void daten() {
        a("Rundrohr 42,4 x 2", "ST-RR-042", "4006381333931", "rundrohr rohr rund st-rr-042 rundrohr 42,4 x 2 42.4");
        a("Quadratrohr 40x40x2", "ST-QR-040", null, "quadratrohr rohr quadrat st-qr-040 40x40x2");
        a("Ohne Suchtext", "XY-1", null, null);
    }

    private List<Artikel> suche(String w1, String w2, String w3, String ganz, String ean) {
        return artikel.sucheFuerAssistent(w1, w2, w3, ganz, ean, PageRequest.of(0, 10));
    }

    @Test
    void alleSuchwoerterMuessenImSuchtextVorkommen() {
        assertThat(suche("%rohr%", "%42,4%", "%", "%rohr 42,4%", "x")).extracting(Artikel::getProduktname).containsExactly("Rundrohr 42,4 x 2");
        assertThat(suche("%rohr%", "%", "%", "%rohr%", "x")).extracting(Artikel::getProduktname).containsExactly("Quadratrohr 40x40x2", "Rundrohr 42,4 x 2");
        assertThat(suche("%rohr%", "%gibtesnicht%", "%", "%rohr gibtesnicht%", "x")).isEmpty();
    }

    @Test
    void artikelnummerUndEanFindenAuchOhneSuchwort() {
        assertThat(suche("%zzz%", "%", "%", "%st-qr-040%", "x")).extracting(Artikel::getArtikelnummer).containsExactly("ST-QR-040");
        assertThat(suche("%zzz%", "%", "%", "%zzz%", "4006381333931")).extracting(Artikel::getArtikelnummer).containsExactly("ST-RR-042");
    }

    @Test
    void artikelOhneSuchtextBringenDieAbfrageNichtDurcheinander() {
        assertThat(suche("%suchtext%", "%", "%", "%xy-1%", "x")).extracting(Artikel::getArtikelnummer).containsExactly("XY-1");
    }

    @Test
    void dieTrefferzahlIstBegrenzt() {
        for (int i = 0; i < 15; i++) {
            a("Schraube " + i, "SCH-" + i, null, "schraube " + i);
        }
        assertThat(artikel.sucheFuerAssistent("%schraube%", "%", "%", "%schraube%", "x", PageRequest.of(0, 10))).hasSize(10);
    }

    @Test
    void protokollKommtNeuesteZuerstUndBegrenzt() {
        for (int i = 0; i < 5; i++) {
            KiAktionProtokoll p = new KiAktionProtokoll();
            p.setZeit(LocalDateTime.of(2026, 10, 4, 10, i));
            p.setFunktion("assistent");
            p.setAnbieter("LOKAL");
            p.setWerkzeug("artikel_suchen");
            p.setStatus("AUSGEFUEHRT");
            p.setParameter("{\"suche\":\"" + i + "\"}");
            protokoll.saveAndFlush(p);
        }

        List<KiAktionProtokoll> neueste = protokoll.findAllByOrderByZeitDescIdDesc(PageRequest.of(0, 3));

        assertThat(neueste).hasSize(3);
        assertThat(neueste.get(0).getParameter()).contains("4");
        assertThat(neueste.get(2).getParameter()).contains("2");
    }
}
