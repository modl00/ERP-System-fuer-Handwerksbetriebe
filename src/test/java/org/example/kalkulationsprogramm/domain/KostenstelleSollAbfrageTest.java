package org.example.kalkulationsprogramm.domain;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("KostenstelleRepository.findByAktivTrueAndIstFixkostenTrueOrderBySortierungAsc() nach Upstream-Merge entfernt")
class KostenstelleSollAbfrageTest {

    @Test
    void speichertDasSollUndLiefertNurAktiveFixkostenInReihenfolge() {}

    @Test
    void einFehlendesSollBleibtLeer() {}
}
