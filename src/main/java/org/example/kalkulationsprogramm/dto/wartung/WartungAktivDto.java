package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.constraints.NotNull;

/** Aktiv oder deaktiviert (weiches Löschen) für Objekte und Türen. Pflichtfeld, damit ein leerer Body nichts deaktiviert. */
public record WartungAktivDto(@NotNull(message = "Bitte angeben, ob aktiv oder deaktiviert.") Boolean aktiv) { }
