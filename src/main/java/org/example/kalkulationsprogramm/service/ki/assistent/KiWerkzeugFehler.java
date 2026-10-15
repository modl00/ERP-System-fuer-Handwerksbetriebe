package org.example.kalkulationsprogramm.service.ki.assistent;

/** Ein Werkzeug kann nicht ausgeführt werden (falsche Angaben, fehlendes Recht, Modul aus). Die Meldung geht an das Modell und an das Protokoll. */
public class KiWerkzeugFehler extends RuntimeException {

    public KiWerkzeugFehler(String meldung) {
        super(meldung);
    }
}
