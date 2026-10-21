package org.example.kalkulationsprogramm.service.ki;

import java.io.IOException;
import java.util.List;

import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Nutzer;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Für KI-Funktionen, die nur Text brauchen (E-Mails einordnen, E-Mail-Texte verbessern): Ist in den Einstellungen (KI, Anbieter und Modelle) nicht Gemini gewählt,
 * läuft die Anfrage über den gewählten Anbieter (Claude, Mistral, lokale KI). Bei Gemini bleibt der bisherige Weg der Funktion unverändert. Die Datenschutz-Stufe der
 * Funktion gilt wie überall.
 */
@Service
@RequiredArgsConstructor
public class KiTextWeiche {

    private final KiAnbieterService anbieterService;

    /** Läuft die Funktion über Gemini (der bisherige feste Weg)? */
    public boolean istGemini(KiFunktion f) {
        return anbieterService.einstellung(f).anbieter() == AnbieterTyp.GEMINI;
    }

    /** Name des gewählten Anbieters in Kleinbuchstaben. */
    public String anbieterName(KiFunktion f) {
        return anbieterService.einstellung(f).anbieter().name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Ist der gewählte Anbieter einsatzbereit (Schlüssel gesetzt, Datenschutz erlaubt)? */
    public boolean bereit(KiFunktion f) {
        try {
            anbieterService.waehle(f);
            return true;
        } catch (KiAnbieterFehler e) {
            return false;
        }
    }

    /**
     * Stellt eine Textfrage an den gewählten Anbieter. Mit {@code json} werden Code-Zäune um eine JSON-Antwort entfernt (manche Modelle setzen sie).
     *
     * @throws IOException mit Klartext, wenn der Anbieter nicht eingerichtet ist oder nicht antwortet
     */
    public String frage(KiFunktion f, String system, String nutzer, double temperatur, int maxTokens, boolean json) throws IOException, InterruptedException {
        try {
            KiAnbieter anbieter = anbieterService.waehle(f);
            String text = anbieter.chat(new KiAnfrage(system, List.of(new Nutzer(nutzer)), List.of(), temperatur, maxTokens)).text();
            return json ? ohneCodeZaeune(text) : text;
        } catch (KiAnbieterFehler e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** Ein ganzes Gespräch (Verlauf, Dateien) an den gewählten Anbieter; die Antwort als Text. */
    public String gespraech(KiFunktion f, KiAnfrage anfrage, boolean json) throws IOException, InterruptedException {
        try {
            String text = anbieterService.waehle(f).chat(anfrage).text();
            return json ? ohneCodeZaeune(text) : text;
        } catch (KiAnbieterFehler e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Wie {@link #frage}, mit Dateien (Foto, PDF, Aufnahme), die der gewählte Anbieter lesen können muss. Kann er es nicht, kommt ein Klartext-Fehler
     * (zum Beispiel „Claude kann keine Aufnahmen lesen“), bevor etwas gesendet wird.
     */
    public String frageMitDateien(KiFunktion f, String system, String nutzer, List<KiTypen.Anhang> dateien, double temperatur, int maxTokens, boolean json)
            throws IOException, InterruptedException {
        try {
            KiAnbieter anbieter = anbieterService.waehle(f);
            // Liest der Anbieter keine PDFs, aber Bilder, bekommt er die Seiten als Bilder
            List<KiTypen.Anhang> lesbar = KiPdfSeiten.fuer(anbieter, dateien);
            String text = anbieter.chat(new KiAnfrage(system, List.of(new Nutzer(nutzer, lesbar)), List.of(), temperatur, maxTokens)).text();
            return json ? ohneCodeZaeune(text) : text;
        } catch (KiAnbieterFehler e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** Aus {@code ```json ... ```} wird der JSON-Inhalt; Thinking-Text vor dem Block wird verworfen; ohne Zäune bleibt der Text, nur getrimmt. */
    static String ohneCodeZaeune(String text) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        if (t.contains("```")) {
            int zaun = t.indexOf("```");
            int zeilenende = t.indexOf('\n', zaun);
            int ende = t.lastIndexOf("```");
            if (zeilenende > zaun && ende > zeilenende) {
                return t.substring(zeilenende + 1, ende).strip();
            }
        }
        return t;
    }
}
