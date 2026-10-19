package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/** Der Hinweis am Beleg, warum (noch) keine Eingangsrechnung entstanden ist (#190). */
@Disabled("eingangsrechnungHinweis() und getEingangsrechnungHinweis() in BelegService/DTO nach Upstream-Merge entfernt; Test auf neue Architektur portieren")
class BelegEingangsrechnungHinweisTest {

    @Test
    void eineRechnungOhneLieferantenSagtDassDerLieferantFehlt() {}

    @Test
    void mitLieferantenUndNeuerNummerGibtEsKeinenHinweis() {}

    @Test
    void eineNummerDieEsBeimLieferantenSchonGibtWirdAlsDoppeltGemeldet() {}

    @Test
    void ohneNummerGibtEsKeinenDoppeltHinweis() {}

    @Test
    void QuittungenLieferscheinePrivateUmbuchungenUndVerworfeneBelegeBekommenKeinenHinweis() {}

    @Test
    void eineEingangsrechnungOhneRechnungsdatumFaelltBeiDerMonatsauswahlHerausUndWirdGemeldet() {}

    @Test
    void bestehtSchonEineEingangsrechnungGibtEsKeinenHinweisSondernDieId() {}
}
