package org.example.kalkulationsprogramm.service.planung;

/** Jemand anderes hat den Vorgang geändert, seit er geladen wurde: Die Änderung wird nicht gespeichert, die Ansicht lädt neu (HTTP 409). */
public class PlanKonfliktException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PlanKonfliktException(String meldung) {
        super(meldung);
    }
}
