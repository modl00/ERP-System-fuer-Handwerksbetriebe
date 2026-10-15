package org.example.kalkulationsprogramm.service.ki;

import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAntwort;

/** Ein KI-Anbieter hinter der gemeinsamen Schnittstelle: Text, Werkzeugaufrufe, ein Gespräch mit Verlauf. */
public interface KiAnbieter {

    AnbieterTyp typ();

    /** Das Modell, mit dem dieser Anbieter gerade arbeitet. */
    String modell();

    /**
     * Kann dieser Anbieter eine Datei dieses Typs lesen? Ohne Überschreiben gilt: nein, nur Text.
     * Wer es kann, nimmt sie in {@link #chat} als Anhang einer Nutzernachricht entgegen.
     */
    default boolean liest(String mime) {
        return false;
    }

    /** Wirft einen Klartext-Fehler, wenn ein Anhang der Anfrage von diesem Anbieter nicht gelesen werden kann (statt eine unverständliche Antwort des Dienstes abzuwarten). */
    default void pruefeAnhaenge(KiAnfrage anfrage) throws KiAnbieterFehler {
        for (var n : anfrage.nachrichten()) {
            if (n instanceof KiTypen.Nutzer u) {
                for (var a : u.anhaenge()) {
                    if (!liest(a.mime())) {
                        String art = a.istTon() ? "Aufnahmen" : a.istPdf() ? "PDF-Dateien" : a.istBild() ? "Bilder dieses Typs (" + a.mime() + ")" : "Dateien vom Typ " + a.mime();
                        throw new KiAnbieterFehler(typ().anzeige() + " kann keine " + art + " lesen. Bitte für diese Funktion einen anderen Anbieter wählen"
                                + (a.istTon() ? " (zum Beispiel Gemini)." : "."));
                    }
                }
            }
        }
    }

    /** Schickt das Gespräch an das Modell und gibt dessen Antwort zurück (Text und/oder gewünschte Werkzeugaufrufe). */
    KiAntwort chat(KiAnfrage anfrage) throws KiAnbieterFehler, InterruptedException;
}
