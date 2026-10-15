package org.example.kalkulationsprogramm.service.ki;

import java.io.IOException;

/** Ein Fehler beim Befragen der KI mit einer Meldung, die der Anwender lesen darf (zum Beispiel "Kontingent erschöpft"). */
public class KiAnbieterFehler extends IOException {

    public KiAnbieterFehler(String klartext) {
        super(klartext);
    }

    public KiAnbieterFehler(String klartext, Throwable ursache) {
        super(klartext, ursache);
    }
}
