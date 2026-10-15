package org.example.kalkulationsprogramm.service.ki;

import org.example.kalkulationsprogramm.service.KiModellService;

/**
 * KI-Funktionen, die schon über die gemeinsame Anbieter-Schnittstelle laufen. Je Funktion lassen sich Anbieter und Datenschutz-Stufe einstellen.
 * Die übrigen KI-Funktionen (Belegerkennung, Scanner, Spracheingabe, Beiträge) brauchen Bilder oder Ton und nutzen noch fest Gemini.
 */
public enum KiFunktion {

    HILFE("hilfe", "KI-Hilfe (Chat)", "ki.hilfe.anbieter", AnbieterTyp.GEMINI, Datenschutz.CLOUD_ERLAUBT, KiModellService.Funktion.HILFE),
    ASSISTENT("assistent", "KI-Assistent mit Auskünften", "ki.assistent.anbieter", AnbieterTyp.LOKAL, Datenschutz.NUR_LOKAL, KiModellService.Funktion.ASSISTENT),
    /** Eingehende E-Mails einem Projekt oder einer Anfrage zuordnen. Standard wie bisher Gemini. */
    EMAIL_EINORDNEN("email-einordnen", "E-Mails einordnen", "ki.email-einordnen.anbieter", AnbieterTyp.GEMINI, Datenschutz.CLOUD_ERLAUBT,
            KiModellService.Funktion.EMAIL_KLASSIFIZIERUNG),
    /** Entwürfe von E-Mails verbessern (Rechtschreibung, Grammatik). Standard wie bisher Gemini. */
    EMAIL_TEXT("email-text", "E-Mail-Texte verbessern", "ki.email-text.anbieter", AnbieterTyp.GEMINI, Datenschutz.CLOUD_ERLAUBT, KiModellService.Funktion.EMAIL_TEXT),
    /**
     * Rechnungen und Angebote aus PDFs auslesen (#232). Bei Gemini geht das PDF selbst an die KI (liest auch Scans). Bei einem anderen Anbieter wird der Text aus dem PDF
     * gelesen und nur der Text gefragt; PDFs ohne Textschicht (reine Scans) braucht dann Gemini.
     */
    PDF_AUSLESEN("pdf-auslesen", "PDF-Rechnungen und -Angebote auslesen", "ki.pdf-auslesen.anbieter", AnbieterTyp.GEMINI, Datenschutz.CLOUD_ERLAUBT,
            KiModellService.Funktion.PDF_EXTRAKTION),
    /** Plantafel (#184): Vorgänge für einen Auftrag vorschlagen und den Liefertermin erklären. Die Termine rechnet das Programm, die KI schlägt nur vor und erklärt. */
    PLANUNG("plantafel", "Plantafel: Vorgänge vorschlagen und Termine erklären", "ki.plantafel.anbieter", AnbieterTyp.GEMINI, Datenschutz.CLOUD_ERLAUBT,
            KiModellService.Funktion.PLANUNG),
    /** Test-Box: Die Antwort des Administrators auf eine Rückfrage als kurzen Kommentar für Gitea aufschreiben und einordnen. Nur Texte der Tickets, keine Kundendaten. */
    /** Scanner: aus dem Foto eines Dokuments einen Dateinamen machen. Das Foto geht an den gewählten Anbieter (Bild-fähig: Gemini, Claude, lokale Modelle mit Bild). */
    SCANNER("scanner", "Scanner: Dateinamen aus Fotos", "ki.scanner.anbieter", AnbieterTyp.GEMINI, Datenschutz.CLOUD_ERLAUBT, KiModellService.Funktion.SCANNER),
    /** Beiträge für die Website: Entwurf aus Projektdaten, Notizen und Fotos. Die Fotos gehen an den gewählten Anbieter; das Modell „Schwierige Fälle“ gilt bei Gemini. Nur öffentliche Texte, keine Kundennamen. */
    BEITRAG("beitrag", "Beiträge für die Website (Entwurf mit Fotos)", "ki.beitrag.anbieter", AnbieterTyp.GEMINI, Datenschutz.CLOUD_ERLAUBT, KiModellService.Funktion.PRO),
    /**
     * Rechnungen, Lieferscheine und Auftragsbestätigungen aus PDFs und Fotos erkennen. Die Datei geht an den gewählten Anbieter; kann er keine PDFs lesen (Mistral,
     * lokale Modelle), bekommt er die Seiten als Bilder. Bei Gemini gilt „Dokumente und Belege erkennen“ als Modell.
     */
    DOKUMENT_ERKENNEN("dokument-erkennen", "Dokumente und Belege erkennen", "ki.dokument-erkennen.anbieter", AnbieterTyp.GEMINI, Datenschutz.CLOUD_ERLAUBT,
            KiModellService.Funktion.DOKUMENT_ANALYSE),
    TESTBOX("testbox", "Test-Box: Rückmeldungen aufbereiten", "ki.testbox.anbieter", AnbieterTyp.GEMINI, Datenschutz.CLOUD_ERLAUBT, KiModellService.Funktion.TESTBOX);

    /** Wohin die Daten gehen dürfen. */
    public enum Datenschutz {
        /** Nur die lokale KI im eigenen Netz; Cloud-Anbieter sind für diese Funktion gesperrt. */
        NUR_LOKAL,
        /** Auch Cloud-Anbieter (Gemini, Claude, Mistral) dürfen die Fragen und die Daten sehen, die zur Antwort nötig sind. */
        CLOUD_ERLAUBT
    }

    private final String schluessel;
    private final String anzeige;
    private final String anbieterSchluessel;
    private final AnbieterTyp standardAnbieter;
    private final Datenschutz standardDatenschutz;
    private final KiModellService.Funktion geminiFunktion;

    KiFunktion(String schluessel, String anzeige, String anbieterSchluessel, AnbieterTyp standardAnbieter, Datenschutz standardDatenschutz,
            KiModellService.Funktion geminiFunktion) {
        this.schluessel = schluessel;
        this.anzeige = anzeige;
        this.anbieterSchluessel = anbieterSchluessel;
        this.standardAnbieter = standardAnbieter;
        this.standardDatenschutz = standardDatenschutz;
        this.geminiFunktion = geminiFunktion;
    }

    /** Die Modellwahl für Gemini, wenn diese Funktion über Gemini läuft (siehe {@link KiModellService}). */
    public KiModellService.Funktion geminiFunktion() {
        return geminiFunktion;
    }

    /** Einstellung des Modells für diese Funktion bei einem Anbieter außer Gemini (Gemini nutzt {@link #geminiFunktion()}). */
    public String modellSchluessel(AnbieterTyp anbieter) {
        return "ki.funktion-modell." + schluessel + "." + anbieter.name();
    }

    public String schluessel() {
        return schluessel;
    }

    public String anzeige() {
        return anzeige;
    }

    /** Schlüssel in {@code system_setting} für den gewählten Anbieter (bei der KI-Hilfe der bisherige {@code ki.hilfe.anbieter}). */
    public String anbieterSchluessel() {
        return anbieterSchluessel;
    }

    public String datenschutzSchluessel() {
        return "ki.datenschutz." + schluessel;
    }

    public AnbieterTyp standardAnbieter() {
        return standardAnbieter;
    }

    public Datenschutz standardDatenschutz() {
        return standardDatenschutz;
    }

    public static java.util.Optional<KiFunktion> ausSchluessel(String s) {
        for (KiFunktion f : values()) {
            if (f.schluessel.equals(s)) {
                return java.util.Optional.of(f);
            }
        }
        return java.util.Optional.empty();
    }
}
