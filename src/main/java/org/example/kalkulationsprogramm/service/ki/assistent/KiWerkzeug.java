package org.example.kalkulationsprogramm.service.ki.assistent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Ein Werkzeug, das die KI nutzen darf. Die KI wählt nur aus und füllt die Angaben aus; ausgeführt wird es vom Programm, mit den Rechten des Benutzers.
 *
 * <p><b>Auskünfte</b> ({@link #aendert()} = false) laufen sofort. <b>Änderungen</b> ({@code aendert()} = true) führt das Programm nie von selbst aus: Sie
 * erscheinen als Vorschlag mit allen Werten, und erst die Bestätigung des Benutzers im Programm löst sie aus.</p>
 */
public interface KiWerkzeug {

    /** Eindeutiger Name ohne Leerzeichen, zum Beispiel {@code artikel_suchen}. */
    String name();

    /** Wofür das Werkzeug gut ist, in einem Satz, für das Modell. */
    String beschreibung();

    /** JSON-Schema der Angaben ({@code type: object}). */
    JsonNode parameter();

    /** Ändert das Werkzeug Daten? Dann nur als Vorschlag mit Bestätigung. */
    boolean aendert();

    /** Löscht das Werkzeug etwas? Solche Werkzeuge laufen nie ohne Rückfrage, auch nicht im Modus „Erweitert“ der KI-Obergrenze. */
    default boolean loescht() {
        return false;
    }

    /** Liest oder ändert das Werkzeug Daten des Betriebs? Nein (zum Beispiel die Programmhilfe) heißt: Es steht auch bei strengem Datenschutz zur Verfügung. */
    default boolean beruehrtProgrammdaten() {
        return true;
    }

    /** Ist das Werkzeug für diesen Benutzer verfügbar (Recht, Modul eingeschaltet)? Nicht Verfügbares sieht das Modell gar nicht. */
    boolean verfuegbar(KiKontext kontext);

    /**
     * Ergänzt die Angaben um Dinge, die der Server (nicht die KI) beisteuert, zum Beispiel einen Fehlerkontext. Läuft vor der Vorschau, damit der Benutzer sieht, was
     * mitgeht. Was die KI selbst unter solchen Namen mitschickt, wird dabei überschrieben.
     */
    default JsonNode erweitere(JsonNode angaben, KiKontext kontext) {
        return angaben;
    }

    /** Der Text für „Das will ich tun: …“ bei Änderungen, mit allen Werten in verständlicher Sprache. */
    default String vorschau(JsonNode angaben) {
        return beschreibung();
    }

    /** Führt das Werkzeug aus und gibt das Ergebnis als Text für das Modell zurück. Fehler als {@link KiWerkzeugFehler} mit Klartext. */
    String ausfuehren(JsonNode angaben, KiKontext kontext);
}
