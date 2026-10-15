package org.example.kalkulationsprogramm.dto.wartung;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.kalkulationsprogramm.domain.wartung.WartungBereich;
import org.example.kalkulationsprogramm.domain.wartung.WartungFeldtyp;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulKategorie;

import java.util.List;

/**
 * Ein Modul des Vorlagenkatalogs mit seinem ganzen Inhalt (Bauarten, Abschnitte, Prüfpunkte, Datenfelder).
 * Der Modul-Editor liest und speichert es als Ganzes: Die Listen müssen deshalb immer mitgeschickt werden,
 * eine fehlende Liste hieße sonst "alles löschen" und wird abgelehnt. Vorhandene Einträge behalten ihre {@code id}, neue haben
 * keine; was in der Liste fehlt, wird gelöscht. Die Reihenfolge der Listen ist die Reihenfolge im Protokoll.
 * Die Felder {@code modulKey}, {@code systemModul} und die Zähler füllt nur der Server, beim Speichern werden
 * sie ignoriert.
 */
public record WartungModulBearbeitenDto(
        Long id,
        Long version,
        String modulKey,
        @NotBlank(message = "Bitte einen Namen eintragen.")
        @Size(max = 120, message = "Der Name ist zu lang (höchstens 120 Zeichen).") String name,
        @Size(max = 2000, message = "Die Beschreibung ist zu lang (höchstens 2.000 Zeichen).") String beschreibung,
        @Pattern(regexp = "^[A-Za-z0-9]{0,40}$", message = "Ungültiges Symbol.") String icon,
        @NotNull(message = "Bitte die Art des Moduls wählen.") WartungModulKategorie kategorie,
        boolean systemModul,
        @NotNull(message = "Bitte angeben, ob das Modul aktiv ist.") Boolean aktiv,
        @NotNull(message = "Bitte ein Standard-Prüfintervall eintragen.")
        @Min(value = 1, message = "Das Prüfintervall muss mindestens 1 Monat betragen.")
        @Max(value = 120, message = "Das Prüfintervall darf höchstens 120 Monate betragen.") Integer standardIntervallMonate,
        @NotNull(message = "Die Liste der Basis-Elemente fehlt.")
        @Size(max = 100, message = "Zu viele Basis-Elemente gewählt.") List<Long> andockbarAn,
        @NotNull(message = "Die Liste der Bauarten fehlt.")
        @Valid @Size(max = 30, message = "Ein Modul kann höchstens 30 Bauarten haben.") List<Untertyp> untertypen,
        @NotNull(message = "Die Liste der Abschnitte fehlt.")
        @Valid @Size(max = 30, message = "Ein Modul kann höchstens 30 Abschnitte haben.") List<Abschnitt> abschnitte,
        int anzahlTueren,
        int anzahlProtokolle
) {

    /** Bauart. Der Schlüssel bleibt nach dem Anlegen fest; ohne Angabe wird er aus der Bezeichnung gebildet. */
    public record Untertyp(
            Long id,
            @Pattern(regexp = "^[a-z0-9_]{0,30}$", message = "Der Schlüssel darf nur Kleinbuchstaben, Ziffern und _ enthalten (höchstens 30 Zeichen).") String schluessel,
            @NotBlank(message = "Bitte jede Bauart benennen.")
            @Size(max = 100, message = "Die Bezeichnung einer Bauart ist zu lang (höchstens 100 Zeichen).") String label
    ) { }

    /** Abschnitt mit Prüfpunkten ({@code PRUEFPUNKTE}) oder Datenfeldern ({@code DATENFELDER}). */
    public record Abschnitt(
            Long id,
            @NotNull(message = "Bitte die Art des Abschnitts wählen.") WartungBereich bereich,
            @NotBlank(message = "Bitte jeden Abschnitt benennen.")
            @Size(max = 200, message = "Die Überschrift eines Abschnitts ist zu lang (höchstens 200 Zeichen).") String label,
            @Valid @Size(max = 300, message = "Ein Abschnitt kann höchstens 300 Prüfpunkte haben.") List<Pruefpunkt> pruefpunkte,
            @Valid @Size(max = 100, message = "Ein Abschnitt kann höchstens 100 Datenfelder haben.") List<Datenfeld> datenfelder
    ) { }

    /** Prüfpunkt; {@code nurBeiUntertyp} = Schlüssel der Bauart, für die er gilt (leer = für alle). */
    public record Pruefpunkt(
            Long id,
            @NotBlank(message = "Bitte jeden Prüfpunkt beschreiben.")
            @Size(max = 500, message = "Ein Prüfpunkt ist zu lang (höchstens 500 Zeichen).") String label,
            String nurBeiUntertyp
    ) { }

    /** Datenfeld der Datenaufnahme; {@code optionen} gibt es nur bei {@code AUSWAHL}. */
    public record Datenfeld(
            Long id,
            @NotBlank(message = "Bitte jedes Datenfeld benennen.")
            @Size(max = 200, message = "Die Bezeichnung eines Datenfelds ist zu lang (höchstens 200 Zeichen).") String label,
            @NotNull(message = "Bitte die Art des Datenfelds wählen.") WartungFeldtyp feldtyp,
            @Size(max = 50, message = "Ein Datenfeld kann höchstens 50 Optionen haben.") List<@Size(max = 200, message = "Eine Option ist zu lang (höchstens 200 Zeichen).") String> optionen
    ) { }
}
