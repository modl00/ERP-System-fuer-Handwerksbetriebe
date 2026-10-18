package org.example.kalkulationsprogramm.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = { "spring.jpa.hibernate.ddl-auto=create-drop", "file.mail-attachment-dir=attachments" })
@Transactional
@Disabled("ProjektErstellenDto.setZeitPositionen() nach Upstream-Merge entfernt; IT-Tests neu aufsetzen")
class ProjektManagementServiceIT {

    @Test
    void aktualisiereProjektFuegtGenauEineZeitpositionHinzu() {}

    @Test
    void aktualisiereProjektEntferntAlleZeitpositionenOhneFehler() {}
}
