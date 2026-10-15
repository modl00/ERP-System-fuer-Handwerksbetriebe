package org.example.kalkulationsprogramm.dto.Rechte;

import java.util.List;
import java.util.Map;

/**
 * Die Übersicht "Wer darf was?": je aktivem Benutzer und je Bereich ein Feld mit Stufe, Kurztext und Begründung, dazu Warnungen zur Einrichtung.
 * {@code stufe} ist ALLES (alles erlaubt), TEILWEISE (zum Beispiel nur Ansehen) oder NICHTS.
 */
public record RechteUebersichtDto(List<Bereich> bereiche, List<Benutzer> benutzer, List<Warnung> warnungen) {

    /** Ein Bereich des Programms (eine Spalte der Übersicht). {@code gruppe} fasst Spalten zusammen (zum Beispiel "Lager und Einkauf"). */
    public record Bereich(String schluessel, String name, String gruppe, String beschreibung) { }

    /** Ein Benutzer mit seinen Feldern; der Schlüssel der Map ist {@link Bereich#schluessel()}. */
    public record Benutzer(Long profilId, String anzeigename, String benutzername, boolean administrator, Long mitarbeiterId, String mitarbeiterName,
            List<String> abteilungen, Map<String, Feld> felder) { }

    public record Feld(String stufe, String text, String grund) { }

    /** Ein Hinweis auf eine unvollständige Einrichtung. {@code ziel} ist die Seite, auf der man es behebt. */
    public record Warnung(String art, String text, String ziel, Long profilId) { }
}
