package org.example.kalkulationsprogramm.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("ArtikelController.gewichtVeraltet() nach Upstream-Merge entfernt")
class ArtikelControllerGewichtTest {

    @Test
    void geaenderteDichteDesWerkstoffsMachtDasGewichtVeraltet() {}

    @Test
    void gleicheDichteIstNichtVeraltet() {}

    @Test
    void ohneWerkstoffGiltStahl() {}

    @Test
    void nurBerechnetesGewichtKannVeralten() {}
}
