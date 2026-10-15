package org.example.kalkulationsprogramm.domain.wartung;

/** Ergebnis eines Prüfpunkts. Ein noch nicht bewerteter Punkt hat gar kein Ergebnis ({@code null}). */
public enum WartungErgebnis {
    /** in Ordnung (i.O.) */
    IO,
    /** nicht in Ordnung (n.i.O.): erscheint als Mangel im Protokoll */
    NIO,
    /** nicht anwendbar (n.a.) */
    NA
}
