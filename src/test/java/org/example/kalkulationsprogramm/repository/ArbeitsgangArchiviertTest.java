package org.example.kalkulationsprogramm.repository;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
@Disabled("ArbeitsgangRepository.anzahlInLeistungen() nach Upstream-Merge entfernt")
class ArbeitsgangArchiviertTest {

    @Test
    void einNeuerArbeitsgangIstSichtbar() {}

    @Test
    void dasAusblendenWirdGespeichert() {}

    @Test
    void dieZaehlungInLeistungenFunktioniertAuchOhneTreffer() {}
}
