package org.example.kalkulationsprogramm.dto.Aktivitaet;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Ereignisse und Einstellungen des Aktivitätsprotokolls (Issue #93). Es enthält nie Inhalte, nur Art, Objekt und Uhrzeit. */
public final class AktivitaetDto {

    private AktivitaetDto() { }

    /** Ein Ereignis. {@code benutzer} steht nur in der Sicht der Administratoren. */
    public record Ereignis(Long id, LocalDateTime zeitpunkt, String art, String text, String objektTyp, Long objektId, String objektName, String benutzer) { }

    /**
     * Der Tagesverlauf einer Person. {@code aktiv} sagt, ob für sie überhaupt protokolliert wird (Administratoren immer, andere nur bei eingeschaltetem Schalter);
     * {@code aufbewahrungTage} 0 heißt unbegrenzt.
     */
    public record MeinTag(LocalDate datum, boolean aktiv, int aufbewahrungTage, List<Ereignis> ereignisse) { }

    public record Einstellungen(boolean mitarbeiterAktiv, int aufbewahrungTage, long ereignisseGesamt) { }

    public record EinstellungenEingabe(Boolean mitarbeiterAktiv, Integer aufbewahrungTage) { }
}
