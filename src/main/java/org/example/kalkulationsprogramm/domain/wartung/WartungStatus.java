package org.example.kalkulationsprogramm.domain.wartung;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lebenslauf eines Wartungsprotokolls.
 *
 * <pre>
 *   OFFEN ⇄ IN_BEARBEITUNG → ABGESCHLOSSEN → ARCHIVIERT
 *                              ↑___ wieder öffnen ___|
 * </pre>
 *
 * <p>Inhalte (Prüfpunkte, Datenfelder, Unterschriften) lassen sich nur ändern, solange das
 * Protokoll {@link #OFFEN} oder {@link #IN_BEARBEITUNG} ist. Ein abgeschlossenes Protokoll muss
 * erst bewusst wieder geöffnet werden.</p>
 */
public enum WartungStatus {
    OFFEN,
    IN_BEARBEITUNG,
    ABGESCHLOSSEN,
    ARCHIVIERT;

    /** Erlaubte direkte Wechsel. Das Wiederherstellen aus dem Archiv entscheidet der Service. */
    public Set<WartungStatus> erlaubteZiele() {
        return switch (this) {
            case OFFEN -> EnumSet.of(IN_BEARBEITUNG, ABGESCHLOSSEN, ARCHIVIERT);
            case IN_BEARBEITUNG -> EnumSet.of(OFFEN, ABGESCHLOSSEN, ARCHIVIERT);
            case ABGESCHLOSSEN -> EnumSet.of(IN_BEARBEITUNG, ARCHIVIERT);
            case ARCHIVIERT -> EnumSet.of(OFFEN, ABGESCHLOSSEN);
        };
    }

    public boolean darfWechselnZu(WartungStatus ziel) {
        return ziel != null && erlaubteZiele().contains(ziel);
    }

    /** Prüfpunkte, Datenfelder und Unterschriften dürfen nur hier verändert werden. */
    public boolean istInhaltBearbeitbar() {
        return this == OFFEN || this == IN_BEARBEITUNG;
    }
}
