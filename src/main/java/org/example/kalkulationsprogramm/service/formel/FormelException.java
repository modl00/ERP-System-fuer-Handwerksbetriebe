package org.example.kalkulationsprogramm.service.formel;

/** Eine Formel ist fehlerhaft oder lässt sich mit den Werten nicht berechnen. Die Meldung ist ein Klartext für die Anwender. */
public class FormelException extends RuntimeException {

    public FormelException(String message) {
        super(message);
    }
}
