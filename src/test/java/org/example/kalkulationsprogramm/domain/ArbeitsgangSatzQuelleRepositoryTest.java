package org.example.kalkulationsprogramm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

/** Die Spalten für die Satzquelle werden gespeichert und geladen; ein neuer Arbeitsgang hat seinen Satz von Hand. */
@DataJpaTest
class ArbeitsgangSatzQuelleRepositoryTest {

    @Autowired
    private ArbeitsgangRepository arbeitsgaenge;
    @Autowired
    private AbteilungRepository abteilungen;

    private Arbeitsgang neu(String name) {
        Abteilung abt = new Abteilung();
        abt.setName("Werkstatt " + name);
        abt = abteilungen.save(abt);
        Arbeitsgang a = new Arbeitsgang();
        a.setBeschreibung(name);
        a.setAbteilung(abt);
        return a;
    }

    @Test
    void eineNeuerArbeitsgangHatDenSatzVonHand() {
        Arbeitsgang gespeichert = arbeitsgaenge.saveAndFlush(neu("Sägen"));

        Arbeitsgang geladen = arbeitsgaenge.findById(gespeichert.getId()).orElseThrow();
        assertThat(geladen.getSatzQuelle()).isEqualTo("EIGEN");
        assertThat(geladen.getSatzGruppe()).isNull();
        assertThat(geladen.folgtVerrechnungssatz()).isFalse();
    }

    @Test
    void dieQuelleUndDieQualifikationBleibenErhalten() {
        Arbeitsgang a = neu("Schweißen");
        a.setSatzQuelle(Arbeitsgang.SATZ_VERRECHNUNG);
        a.setSatzGruppe("FACHARBEITER");
        Long id = arbeitsgaenge.saveAndFlush(a).getId();

        Arbeitsgang geladen = arbeitsgaenge.findById(id).orElseThrow();
        assertThat(geladen.folgtVerrechnungssatz()).isTrue();
        assertThat(geladen.getSatzGruppe()).isEqualTo("FACHARBEITER");
    }
}
