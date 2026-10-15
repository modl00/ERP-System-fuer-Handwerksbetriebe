package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import jakarta.persistence.EntityManager;

/** Ausgeblendete Arbeitsgänge (#235) gegen eine echte Datenbank (H2); Dummy-Daten. */
@DataJpaTest
class ArbeitsgangArchiviertTest {

    @Autowired private ArbeitsgangRepository arbeitsgaenge;
    @Autowired private AbteilungRepository abteilungen;
    @Autowired private EntityManager em;

    private Arbeitsgang neu(String name) {
        Abteilung a = new Abteilung();
        a.setName("Werkstatt " + name);
        abteilungen.saveAndFlush(a);
        Arbeitsgang g = new Arbeitsgang();
        g.setBeschreibung(name);
        g.setAbteilung(a);
        return arbeitsgaenge.saveAndFlush(g);
    }

    @Test
    void einNeuerArbeitsgangIstSichtbar() {
        Arbeitsgang g = neu("Sägen");
        em.clear();
        assertThat(arbeitsgaenge.findById(g.getId()).orElseThrow().isArchiviert()).isFalse();
    }

    @Test
    void dasAusblendenWirdGespeichert() {
        Arbeitsgang g = neu("Bohren");
        g.setArchiviert(true);
        arbeitsgaenge.saveAndFlush(g);
        em.clear();
        assertThat(arbeitsgaenge.findById(g.getId()).orElseThrow().isArchiviert()).isTrue();
    }

    @Test
    void dieZaehlungInLeistungenFunktioniertAuchOhneTreffer() {
        Arbeitsgang g = neu("Schweißen");
        assertThat(arbeitsgaenge.anzahlInLeistungen(g.getId())).isZero();
    }
}
