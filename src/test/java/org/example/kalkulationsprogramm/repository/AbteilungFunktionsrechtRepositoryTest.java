package org.example.kalkulationsprogramm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.AbteilungFunktionsrecht;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AbteilungFunktionsrechtRepositoryTest {

    @Autowired AbteilungRepository abteilungen;
    @Autowired AbteilungFunktionsrechtRepository rechte;

    private Abteilung abteilung(String name) {
        Abteilung a = new Abteilung();
        a.setName(name);
        return abteilungen.saveAndFlush(a);
    }

    private AbteilungFunktionsrecht zeile(Abteilung a, Funktion f, boolean sehen) {
        AbteilungFunktionsrecht z = new AbteilungFunktionsrecht();
        z.setAbteilung(a);
        z.setFunktion(f.name());
        z.setAnsehen(sehen);
        z.setAnlegen(false);
        return z;
    }

    @Test
    void speichertUndFindetDieRechteJeAbteilung() {
        Abteilung montage = abteilung("Montage");
        Abteilung buero = abteilung("Büro");
        rechte.saveAndFlush(zeile(montage, Funktion.LAGER, true));
        rechte.saveAndFlush(zeile(montage, Funktion.WARTUNG, false));
        rechte.saveAndFlush(zeile(buero, Funktion.LAGER, true));

        List<AbteilungFunktionsrecht> montageRechte = rechte.findByAbteilungId(montage.getId());

        assertThat(montageRechte).extracting(AbteilungFunktionsrecht::getFunktion).containsExactlyInAnyOrder("LAGER", "WARTUNG");
        assertThat(rechte.findByAbteilungIdIn(List.of(montage.getId(), buero.getId()))).hasSize(3);
        assertThat(rechte.findByAbteilungIdIn(List.of(buero.getId()))).hasSize(1);
    }

    @Test
    void eineFunktionGibtEsJeAbteilungNurEinmal() {
        Abteilung montage = abteilung("Montage");
        rechte.saveAndFlush(zeile(montage, Funktion.LAGER, true));

        assertThatThrownBy(() -> rechte.saveAndFlush(zeile(montage, Funktion.LAGER, false)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void ohneAnsehenErlaubtKeinAnlegenAendernLoeschen() {
        AbteilungFunktionsrecht z = zeile(abteilung("Montage"), Funktion.LAGER, false);
        z.setAendern(true);
        z.setLoeschen(true);

        for (var aktion : org.example.kalkulationsprogramm.domain.FunktionsAktion.values()) {
            assertThat(z.erlaubt(aktion)).as(aktion.name()).isFalse();
        }
    }
}
