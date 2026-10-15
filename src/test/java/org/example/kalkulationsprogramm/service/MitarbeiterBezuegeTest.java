package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.example.kalkulationsprogramm.domain.Abwesenheit;
import org.example.kalkulationsprogramm.domain.AbwesenheitsTyp;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

/** Beim Löschen eines Mitarbeiters wird genau genannt, was ihn festhält (#284). Nur Dummy-Daten. */
@DataJpaTest
@Import(MitarbeiterBezuege.class)
class MitarbeiterBezuegeTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private MitarbeiterBezuege bezuege;

    private Mitarbeiter mitarbeiter() {
        Mitarbeiter m = new Mitarbeiter();
        m.setVorname("Max");
        m.setNachname("Mustermann");
        return em.persist(m);
    }

    @Test
    void einMitarbeiterOhneEintraegeHatKeineBezuege() {
        Mitarbeiter m = mitarbeiter();
        em.flush();

        assertThat(bezuege.beschreibe(m.getId())).isEmpty();
    }

    @Test
    void esWirdGezaehltUndInEinzahlOderMehrzahlGenannt() {
        Mitarbeiter m = mitarbeiter();
        for (int tag = 1; tag <= 2; tag++) {
            Abwesenheit a = new Abwesenheit();
            a.setMitarbeiter(m);
            a.setDatum(LocalDate.of(2026, 7, tag));
            a.setTyp(AbwesenheitsTyp.URLAUB);
            a.setStunden(new java.math.BigDecimal("8"));
            em.persist(a);
        }
        em.flush();

        assertThat(bezuege.beschreibe(m.getId())).containsExactly("2 Abwesenheiten (Urlaub, Krankheit)");
    }
}
