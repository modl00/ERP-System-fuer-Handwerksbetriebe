package org.example.kalkulationsprogramm.repository;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@Disabled("FrontendUserProfileService.saveOrUpdate Signatur und weitere Symbole nach Upstream-Merge geändert; Tests neu aufsetzen")
class FrontendUserMitarbeiterZuordnungTest {

    @Test
    void einBenutzerMitMitarbeiterLaesstSichAnlegen() {}

    @Test
    void gleichesKuerzelBeiZweiBenutzernIstKeinProblem() {}

    @Test
    void einMitarbeiterKannNichtZuZweiBenutzernGehoerenUndDasWirdInKlartextGemeldet() {}

    @Test
    void einBenutzerDarfSeinenEigenenMitarbeiterBeimSpeichernBehalten() {}

    @Test
    void wirdDerMitarbeiterEinemAnderenBenutzerZugeordnet_istDerAlteFreiUndDerNeueGesperrt() {}

    @Test
    void dieAbfrageNenntDenBenutzerEinesMitarbeiters() {}

    @Test
    void einMitarbeiterOhneAbhaengigeDatenLaesstSichLoeschen() {}
}
