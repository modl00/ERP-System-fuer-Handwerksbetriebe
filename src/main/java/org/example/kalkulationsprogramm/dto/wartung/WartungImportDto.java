package org.example.kalkulationsprogramm.dto.wartung;

import java.util.List;

/**
 * Die Vorschau der Übernahme von Wartungsprotokollen aus HandwerkPro. Sie schreibt nichts: Sie zeigt, was der Import anlegen würde, wohin jeder alte Wert
 * kommt und was eine Entscheidung braucht.
 */
public final class WartungImportDto {

    private WartungImportDto() { }

    /** Aktionen für einen Wert: {@code ZUGEORDNET} (eindeutig auf einen Eintrag der Liste), {@code LEER} (keine Angabe), {@code NEU_IN_LISTE} (noch nicht in der Auswahlliste),
     * {@code FREITEXT} (bleibt Text), {@code UNGUELTIG} (kein gültiges Jahr o. Ä., wird leer gelassen). */
    public static final String ZUGEORDNET = "ZUGEORDNET";
    public static final String LEER = "LEER";
    public static final String NEU_IN_LISTE = "NEU_IN_LISTE";
    public static final String FREITEXT = "FREITEXT";
    public static final String UNGUELTIG = "UNGUELTIG";

    public record Vorschau(Zusammenfassung zusammenfassung, List<KundeZeile> kunden, List<ProtokollZeile> protokolle, List<WertZeile> werte, List<EigenerPunkt> eigenePruefpunkte, List<String> hinweise) { }

    /** Ein alter Prüfpunkt, den es im Katalog der gewählten Module nicht gibt (bleibt als eigener Punkt im Protokoll). */
    public record EigenerPunkt(String label, int anzahl) { }

    public record Zusammenfassung(int wartungen, int abgeschlossen, int offen, int modulErgaenzt, int modulUnklar, int kundenNeu, int objekteNeu, int tuerenNeu,
            int pruefpunkteGesamt, int pruefpunkteZugeordnet, int pruefpunkteEigene, int werteNeuInListe, int werteUngueltig) { }

    /** {@code vorschlag}: ZUORDNEN (Treffer im ERP) oder NEU (wird neu angelegt). */
    public record KundeZeile(Long altId, String name, Long erpId, String erpName, String vorschlag, int wartungen) { }

    /**
     * Ein Altprotokoll in der Vorschau. {@code untertypen} ordnet jedem Modul die Variante zu (Schlüssel im ERP, oder {@code NEU:<Bezeichnung>}, wenn der Katalog sie erst
     * ergänzt bekommt); sie wird für die Übernahme gebraucht.
     */
    public record ProtokollZeile(Long altId, String altNummer, String titel, String status, String protokollart, List<String> module, String modulQuelle, List<String> ergaenzteModule, String bauart, java.util.Map<String, String> untertypen,
            int pruefpunkte, int pruefpunkteZugeordnet, int pruefpunkteEigene, List<String> fragen) { }

    /** Ein Wert in der Abbildungstabelle: neues Feld, alter Wert, neuer Wert, wie oft er vorkommt und was damit passiert. */
    public record WertZeile(String feld, String altWert, String neuerWert, int anzahl, String aktion) { }
}
