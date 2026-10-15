package org.example.kalkulationsprogramm.service.ki.assistent;

import java.util.List;

/**
 * Was das Programm einem Fehlerbericht an die Entwicklung automatisch mitgibt (#255): wo der Benutzer war und was der Browser an fehlgeschlagenen Aufrufen und Fehlern
 * gesehen hat. Kommt vom Browser und gilt deshalb als ungeprüfte Eingabe: Der Server kürzt und bereinigt alles, bevor es in einen Bericht geht.
 */
public record Fehlerkontext(String route, String titel, List<String> seitenFehler, List<BrowserFehler> browserFehler) {

    /** Ein fehlgeschlagener Aufruf oder Fehler im Browser. {@code status} ist leer bei einem Fehler ohne Antwort (zum Beispiel keine Verbindung). */
    public record BrowserFehler(String zeit, String methode, String pfad, Integer status, String meldung) { }
}
