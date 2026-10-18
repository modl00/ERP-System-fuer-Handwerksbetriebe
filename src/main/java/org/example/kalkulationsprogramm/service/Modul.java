package org.example.kalkulationsprogramm.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * Module, die sich in den Einstellungen ein- und ausschalten lassen, ohne dass etwas gelöscht wird. Jedes Modul hat einen Namen für die
 * Schnittstelle, einen Schlüssel in {@code system_setting}, einen Namen für Meldungen und die Pfade, die bei ausgeschaltetem Modul gesperrt sind.
 * Wartung und Lager haben noch ihre eigenen Schalter-Dienste; neue Module kommen hier hinzu.
 */
public enum Modul {

    MIETE("miete", "miete.modul.aktiv", "Das Mietabrechnungsmodul", "/api/miete/**"),
    WEBSITE("website", "website.modul.aktiv", "Das Neuigkeiten-Modul", "/api/beitraege/**", "/api/website-analytics/**"),
    PLANTAFEL("plantafel", "plantafel.modul.aktiv", "Das Plantafel-Modul", "/api/plantafel/**");

    private final String name;
    private final String schluessel;
    private final String anzeige;
    private final String[] pfade;

    Modul(String name, String schluessel, String anzeige, String... pfade) {
        this.name = name;
        this.schluessel = schluessel;
        this.anzeige = anzeige;
        this.pfade = pfade;
    }

    /** Name in der Adresse, zum Beispiel {@code miete}. */
    public String getName() {
        return name;
    }

    public String getSchluessel() {
        return schluessel;
    }

    /** Für Meldungen: "Das Mietabrechnungsmodul". */
    public String getAnzeige() {
        return anzeige;
    }

    public String[] getPfade() {
        return pfade.clone();
    }

    public static Optional<Modul> ausName(String name) {
        return Arrays.stream(values()).filter(m -> m.name.equals(name)).findFirst();
    }
}
