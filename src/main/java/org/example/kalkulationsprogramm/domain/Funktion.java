package org.example.kalkulationsprogramm.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * Funktionen des Programms, für die sich in der Rechteverwaltung je Abteilung einstellen lässt, was erlaubt ist (siehe {@link FunktionsAktion}).
 * Das Recht gilt gleich für das Programm am PC und für die Handy-App. Neue Funktionen kommen hier hinzu, ohne Migration (die Spalte ist Text).
 * Aufgenommen werden nur Funktionen, bei denen das Recht wirklich geprüft wird.
 */
public enum Funktion {

    LAGER("Lager", "Lagerbestand ansehen, Zugang, Entnahme, Inventur und Umbuchen"),
    WARTUNG("Wartung", "Wartungen, Anlagen und Feldeinsätze"),
    BESTELLUNGEN("Bestellungen", "Bestellungen an Lieferanten ansehen, anlegen, bestellen, Lieferung eintragen und stornieren"),
    ZEITERFASSUNG("Zeit nachtragen", "Arbeitszeit, Pausen und Fahrten in der Handy-App rückwirkend erfassen (anlegen), ändern und löschen"),
    KUNDEN("Kunden", "Kunden ansehen, anlegen (auch in der Handy-App), ändern und löschen"),
    ANFRAGEN("Anfragen", "Anfragen ansehen, anlegen (auch in der Handy-App), ändern und löschen"),
    LIEFERSCHEINE("Lieferscheine", "Lieferscheine an Kunden in der Handy-App erstellen, vom Empfänger unterschreiben lassen, ansehen, als Kopie versenden und Entwürfe löschen");

    private final String anzeige;
    private final String beschreibung;

    Funktion(String anzeige, String beschreibung) {
        this.anzeige = anzeige;
        this.beschreibung = beschreibung;
    }

    public String getAnzeige() {
        return anzeige;
    }

    public String getBeschreibung() {
        return beschreibung;
    }

    public static Optional<Funktion> ausName(String name) {
        return Arrays.stream(values()).filter(f -> f.name().equals(name)).findFirst();
    }
}
