package org.example.kalkulationsprogramm.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.repository.KostenstelleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

/** Die Spalte jahres_soll und die Abfrage der Fixkosten-Kostenstellen laufen gegen die echte Abbildung (#237). */
@DataJpaTest
class KostenstelleSollAbfrageTest {

    @Autowired
    private KostenstelleRepository repo;

    private Kostenstelle neu(String name, KostenstellenTyp typ, boolean fix, boolean aktiv, int sortierung, String soll) {
        Kostenstelle k = new Kostenstelle();
        k.setBezeichnung(name);
        k.setTyp(typ);
        k.setIstFixkosten(fix);
        k.setAktiv(aktiv);
        k.setSortierung(sortierung);
        k.setJahresSoll(soll == null ? null : new BigDecimal(soll));
        return repo.saveAndFlush(k);
    }

    @Test
    void speichertDasSollUndLiefertNurAktiveFixkostenInReihenfolge() {
        neu("Versicherung", KostenstellenTyp.GEMEINKOSTEN, true, true, 2, "1800.50");
        neu("Miete", KostenstellenTyp.GEMEINKOSTEN, true, true, 1, "6000.00");
        neu("Lager", KostenstellenTyp.LAGER, false, true, 3, null);
        neu("Alt", KostenstellenTyp.GEMEINKOSTEN, true, false, 4, "10.00");

        var liste = repo.findByAktivTrueAndIstFixkostenTrueOrderBySortierungAsc();

        assertThat(liste).extracting(Kostenstelle::getBezeichnung).containsExactly("Miete", "Versicherung");
        assertThat(liste.get(1).getJahresSoll()).isEqualByComparingTo("1800.50");
    }

    @Test
    void einFehlendesSollBleibtLeer() {
        Kostenstelle k = neu("Telefon", KostenstellenTyp.GEMEINKOSTEN, true, true, 1, null);

        assertThat(repo.findById(k.getId()).orElseThrow().getJahresSoll()).isNull();
    }
}
