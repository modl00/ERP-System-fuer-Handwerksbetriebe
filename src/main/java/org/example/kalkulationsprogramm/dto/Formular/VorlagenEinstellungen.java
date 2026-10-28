package org.example.kalkulationsprogramm.dto.Formular;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.Setter;

/**
 * Die Einstellungen einer Dokumentvorlage: wo Adresszeile, Infoblock und Betreffzeilen stehen, welche Schrift gilt, was in der Fußzeile steht
 * und wie die Positionstabelle aussieht. Aus diesen Angaben baut {@code StandardVorlagenInhalt} das Seitenlayout; sie stehen als
 * {@code <meta name="vorlage-einstellungen">} in der Vorlage und gelten beim Erzeugen des PDF.
 *
 * <p>Alle Abstände in Millimetern, Schriftgrößen in Punkt. Jeder Wert hat einen Standard, der dem mitgelieferten Beispiel entspricht, fehlende
 * Angaben im gespeicherten JSON bleiben also gültig. {@link #bereinigt()} begrenzt alle Werte auf sinnvolle Bereiche, damit auch ein
 * falsch gesetzter Wert kein unlesbares Dokument erzeugt.</p>
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VorlagenEinstellungen {

    public static final String META_NAME = "vorlage-einstellungen";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern META = Pattern.compile("<meta\\s+name=\"" + META_NAME + "\"\\s+content=\"([^\"]*)\"\\s*/?>");

    public static final List<String> SCHRIFTARTEN = List.of("HELVETICA", "TIMES", "COURIER");
    public static final List<String> FUSSZEILEN = List.of("DOKUMENT_FIRMA_SEITE", "FIRMENDATEN", "KEINE");
    public static final List<String> BETREFF1_INHALTE = List.of("BAUVORHABEN", "BETREFF");
    public static final List<String> TABELLENSTILE = List.of("GRAU", "SCHLICHT");

    // ---- Layout ----
    /** HELVETICA (Arial, Calibri), TIMES oder COURIER. Das PDF kennt nur diese drei Schriftfamilien. */
    private String schriftart = "HELVETICA";
    private int schriftgroesse = 9;
    /** Schriftgröße in der Positionstabelle (Kopf, Positionen, Preise); 10 = wie bisher (#286). */
    private int tabellenSchriftgroesse = 10;
    private boolean falzmarken = true;
    /** Wo der Hauptblock (Betreffzeilen, Texte, Positionen) beginnt, von oben. */
    private int hauptblockOben = 118;
    private int hauptblockLinks = 25;
    private int hauptblockRechts = 25;

    // ---- Adresszeile (kleine Absenderzeile über dem Empfänger) ----
    private boolean adresszeileAnzeigen = true;
    /** Leer = Firmenname, Straße und Ort aus den Firmendaten. */
    private String adresszeileText = "";
    private int adresszeileOben = 47;
    private int adresszeileLinks = 26;

    // ---- Betreffzeile 1 (Projektanschrift) ----
    private boolean betreff1Anzeigen = true;
    private boolean betreff1Fett = true;
    /** BAUVORHABEN oder BETREFF. */
    private String betreff1Inhalt = "BAUVORHABEN";
    private int betreff1Schriftgroesse = 11;
    private String betreff1Praefix = "BV:";

    // ---- Betreffzeile 2 (Dokumentnummer) ----
    private boolean betreff2Anzeigen = true;
    private boolean betreff2Fett = true;
    private int betreff2Schriftgroesse = 13;
    /** Leer = "{{DOKUMENTTYP}} Nr. {{DOKUMENTNUMMER}}", beim Lieferschein "Arbeits- & Lieferschein Nr. ...". */
    private String betreff2Text = "";

    // ---- Infoblock (Dokumentinformationen und Kontaktdaten) ----
    private boolean infoblockAnzeigen = true;
    private int infoblockOben = 71;
    private int infoblockLinks = 116;
    private int infoblockBreite = 70;
    private boolean infoDokumentnummer = true;
    private boolean infoProjektnummer = true;
    private boolean infoReferenzdokument = false;
    private boolean infoKundennummer = true;
    private boolean infoDatum = true;
    private boolean infoAnsprechpartner = true;
    private boolean infoTelefon = true;
    private boolean infoMobil = true;
    private boolean infoFax = false;
    private boolean infoEmail = true;

    // ---- Freitext-Infoblock ----
    private boolean freitextAnzeigen = false;
    private int freitextOben = 50;
    private int freitextLinks = 20;
    private int freitextBreite = 50;
    private String freitextInhalt = "";

    // ---- Fußzeile ----
    /** DOKUMENT_FIRMA_SEITE (Dokument links, Firma in der Mitte, Seite rechts), FIRMENDATEN (Bank, Steuer, Register) oder KEINE. */
    private String fusszeileTyp = "DOKUMENT_FIRMA_SEITE";
    /** Abstand zwischen dem Ende des Hauptblocks und der Fußzeile. */
    private int fusszeileAbstand = 8;
    private int fusszeileSchriftgroesse = 7;

    // ---- Briefpapier ----
    /** Aus: Das Briefpapier der Firma und das Hintergrundbild der Vorlage bleiben beim PDF weg (zum Beispiel für den Druck auf fertiges Papier). */
    private boolean briefpapierAnzeigen = true;

    // ---- Positionstabelle ----
    private boolean positionsBeschreibung = true;
    private boolean artikelnummerAnzeigen = false;
    private boolean einzelpreiseAusblenden = false;
    /** GRAU (Kopfzeile und Gruppen grau hinterlegt) oder SCHLICHT (die frühere Darstellung). */
    private String tabellenstil = "GRAU";

    /** Die Standardeinstellungen. */
    public static VorlagenEinstellungen standard() {
        return new VorlagenEinstellungen();
    }

    /** Standard für einen Lieferschein: ohne Preise, mit Artikelnummer. */
    public static VorlagenEinstellungen lieferschein() {
        VorlagenEinstellungen e = new VorlagenEinstellungen();
        e.artikelnummerAnzeigen = true;
        e.infoKundennummer = false;
        e.einzelpreiseAusblenden = true;
        e.betreff2Text = "Arbeits- & Lieferschein Nr. {{DOKUMENTNUMMER}}";
        return e;
    }

    /** Begrenzt jeden Wert auf einen sinnvollen Bereich und ersetzt Unbekanntes durch den Standard. Gibt {@code this} zurück. */
    public VorlagenEinstellungen bereinigt() {
        VorlagenEinstellungen d = new VorlagenEinstellungen();
        schriftart = SCHRIFTARTEN.contains(schriftart) ? schriftart : d.schriftart;
        schriftgroesse = grenze(schriftgroesse, 6, 14);
        tabellenSchriftgroesse = grenze(tabellenSchriftgroesse, 7, 14);
        hauptblockOben = grenze(hauptblockOben, 40, 200);
        hauptblockLinks = grenze(hauptblockLinks, 0, 60);
        hauptblockRechts = grenze(hauptblockRechts, 0, 60);
        adresszeileText = kurz(adresszeileText, 200);
        adresszeileOben = grenze(adresszeileOben, 5, 150);
        adresszeileLinks = grenze(adresszeileLinks, 0, 150);
        betreff1Inhalt = BETREFF1_INHALTE.contains(betreff1Inhalt) ? betreff1Inhalt : d.betreff1Inhalt;
        betreff1Schriftgroesse = grenze(betreff1Schriftgroesse, 6, 24);
        betreff1Praefix = kurz(betreff1Praefix, 30);
        betreff2Schriftgroesse = grenze(betreff2Schriftgroesse, 6, 28);
        betreff2Text = kurz(betreff2Text, 200);
        infoblockOben = grenze(infoblockOben, 5, 200);
        infoblockLinks = grenze(infoblockLinks, 0, 190);
        infoblockBreite = grenze(infoblockBreite, 30, 190 - infoblockLinks > 30 ? 190 - infoblockLinks : 30);
        freitextOben = grenze(freitextOben, 5, 250);
        freitextLinks = grenze(freitextLinks, 0, 190);
        freitextBreite = grenze(freitextBreite, 20, 200);
        freitextInhalt = kurz(freitextInhalt, 1000);
        fusszeileTyp = FUSSZEILEN.contains(fusszeileTyp) ? fusszeileTyp : d.fusszeileTyp;
        fusszeileAbstand = grenze(fusszeileAbstand, 0, 40);
        fusszeileSchriftgroesse = grenze(fusszeileSchriftgroesse, 5, 12);
        tabellenstil = TABELLENSTILE.contains(tabellenstil) ? tabellenstil : d.tabellenstil;
        return this;
    }

    /** Die Einstellungen als Meta-Tag für das Vorlagen-HTML. */
    public String alsMetaTag() {
        try {
            String json = JSON.writeValueAsString(this);
            return "<meta name=\"" + META_NAME + "\" content=\"" + URLEncoder.encode(json, StandardCharsets.UTF_8).replace("+", "%20") + "\" />";
        } catch (Exception e) {
            throw new IllegalStateException("Die Vorlagen-Einstellungen konnten nicht gespeichert werden.", e);
        }
    }

    /**
     * Liest die Einstellungen aus einem Vorlagen-HTML. Fehlt der Meta-Tag (eine Vorlage aus der Zeit davor) oder ist er unlesbar,
     * ist das Ergebnis leer: Solche Vorlagen behalten ihre bisherige Darstellung.
     */
    public static java.util.Optional<VorlagenEinstellungen> ausHtml(String html) {
        if (html == null || html.isEmpty()) {
            return java.util.Optional.empty();
        }
        Matcher m = META.matcher(html);
        if (!m.find()) {
            return java.util.Optional.empty();
        }
        try {
            String json = URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
            return java.util.Optional.of(JSON.readValue(json, VorlagenEinstellungen.class).bereinigt());
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    private static int grenze(int wert, int min, int max) {
        return Math.max(min, Math.min(max, wert));
    }

    private static String kurz(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
