package org.example.kalkulationsprogramm.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("KalenderController.uebernehmeTage() und KalenderEintragRequest Konstruktor nach Upstream-Merge geändert")
class KalenderMehrtaegigTest {

    @Test
    void einMehrtaegigerTerminIstGanztaegigUndHatKeineUhrzeit() {}

    @Test
    void einLetzterTagAmStarttagOderDavorMachtEinenEintaegigenTermin() {}

    @Test
    void ohneAngabeBleibtDerLetzteTagErhalten() {}

    @Test
    void wirdDerStarttagHinterDenLetztenTagVerlegtIstDerTerminEintaegig() {}
}
