package org.example.kalkulationsprogramm.service.ki.assistent;

/** Für wen die KI arbeitet: der angemeldete Benutzer am PC. Jedes Werkzeug prüft dessen Rechte, die KI hat nie mehr Rechte als er. */
public record KiKontext(Long profilId, String benutzer, boolean admin, Fehlerkontext fehler) {

    /** Ohne Fehlerkontext (zum Beispiel beim Bestätigen oder in Tests). */
    public KiKontext(Long profilId, String benutzer, boolean admin) {
        this(profilId, benutzer, admin, null);
    }
}
