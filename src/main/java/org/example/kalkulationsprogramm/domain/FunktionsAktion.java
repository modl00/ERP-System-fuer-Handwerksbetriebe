package org.example.kalkulationsprogramm.domain;

/** Was mit einer {@link Funktion} getan werden darf. Anlegen, Ändern und Löschen setzen Ansehen voraus. */
public enum FunktionsAktion {

    ANSEHEN("Sehen"),
    ANLEGEN("Anlegen"),
    AENDERN("Ändern"),
    LOESCHEN("Löschen");

    private final String anzeige;

    FunktionsAktion(String anzeige) {
        this.anzeige = anzeige;
    }

    public String getAnzeige() {
        return anzeige;
    }

    /** Die Aktion zu einer HTTP-Methode: Lesen = Ansehen, POST = Anlegen, PUT/PATCH = Ändern, DELETE = Löschen. */
    public static FunktionsAktion zuHttpMethode(String methode) {
        if (methode == null) {
            return ANSEHEN;
        }
        return switch (methode.toUpperCase(java.util.Locale.ROOT)) {
            case "POST" -> ANLEGEN;
            case "PUT", "PATCH" -> AENDERN;
            case "DELETE" -> LOESCHEN;
            default -> ANSEHEN;
        };
    }
}
