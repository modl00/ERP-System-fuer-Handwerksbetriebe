package org.example.kalkulationsprogramm.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.domain.Dokumenttyp;
import org.example.kalkulationsprogramm.dto.Formular.VorlagenEinstellungen;

/**
 * Inhalt der mitgelieferten Standardvorlagen: das Seitenlayout (Blöcke wie im Formular-Editor) und die Standardtexte
 * (Vortext und Nachtext) je Dokumenttyp. Alles nur Daten, die Anlage übernimmt {@link StandardVorlagenService}.
 *
 * <p>Das Layout folgt DIN 5008 (Adressfeld für Fensterumschläge, Falzmarken) und benutzt Textblöcke mit Platzhaltern. Wo was steht, bestimmen die
 * {@link VorlagenEinstellungen}: Adresszeile, Infoblock mit Ansprechpartner, Betreffzeilen, Fußzeile. Die Firmendaten
 * kommen über {@link FirmenPlatzhalter} aus den Einstellungen. Wer ein fertiges Briefpapier mit Briefkopf und Fußzeile verwendet,
 * stellt in der Firma "Das Briefpapier enthält schon Briefkopf und Fußzeile" ein: Dann lässt das PDF die Firmenblöcke und das Logo von selbst weg.</p>
 */
final class StandardVorlagenInhalt {

    /** Eine Vorlage, die für mehrere Dokumenttypen gilt. */
    record Familie(String vorlagenName, List<Dokumenttyp> typen, VorlagenEinstellungen einstellungen) {

        Familie(String vorlagenName, List<Dokumenttyp> typen) {
            this(vorlagenName, typen, VorlagenEinstellungen.standard());
        }
    }

    static final List<Familie> FAMILIEN = List.of(
            new Familie("Standard Angebot und Auftrag",
                    List.of(Dokumenttyp.ANGEBOT, Dokumenttyp.NACHTRAGSANGEBOT, Dokumenttyp.AUFTRAGSBESTAETIGUNG)),
            new Familie("Standard Rechnung",
                    List.of(Dokumenttyp.RECHNUNG, Dokumenttyp.TEILRECHNUNG, Dokumenttyp.ABSCHLAGSRECHNUNG,
                            Dokumenttyp.SCHLUSSRECHNUNG, Dokumenttyp.STORNORECHNUNG, Dokumenttyp.GUTSCHRIFT)),
            new Familie("Standard Mahnung",
                    List.of(Dokumenttyp.ZAHLUNGSERINNERUNG, Dokumenttyp.ERSTE_MAHNUNG, Dokumenttyp.ZWEITE_MAHNUNG)),
            new Familie("Standard Lieferschein", List.of(Dokumenttyp.LIEFERSCHEIN), VorlagenEinstellungen.lieferschein()),
            new Familie("Standard Bestellformular", List.of(Dokumenttyp.BESTELLFORMULAR)));

    /** Standardtexte eines Dokumenttyps. */
    record Texte(String vortext, String nachtext) {
    }

    private static final String GRUSS = "<p>Mit freundlichen Grüßen</p>";
    private static final String ANREDE = "<p>{{Anrede}} {{Ansprechpartner}},</p>";

    private static final Map<Dokumenttyp, Texte> TEXTE = Map.ofEntries(
            Map.entry(Dokumenttyp.ANGEBOT, new Texte(
                    ANREDE + "<p>vielen Dank für Ihre Anfrage und Ihr Vertrauen. Gerne bieten wir Ihnen die folgenden Leistungen an:</p>",
                    "<p>Dieses Angebot ist 30 Tage gültig. Wenn Sie Fragen haben oder etwas ändern möchten, melden Sie sich gerne bei uns. "
                            + "Wir freuen uns auf Ihren Auftrag.</p>" + GRUSS)),
            Map.entry(Dokumenttyp.NACHTRAGSANGEBOT, new Texte(
                    ANREDE + "<p>bei der Ausführung sind zusätzliche Leistungen nötig geworden, die nicht im ursprünglichen Auftrag enthalten sind. "
                            + "Dafür bieten wir Ihnen Folgendes an:</p>",
                    "<p>Bitte bestätigen Sie uns den Nachtrag, bevor wir mit den zusätzlichen Arbeiten beginnen. "
                            + "Für Rückfragen sind wir jederzeit für Sie da.</p>" + GRUSS)),
            Map.entry(Dokumenttyp.AUFTRAGSBESTAETIGUNG, new Texte(
                    ANREDE + "<p>vielen Dank für Ihren Auftrag. Wir bestätigen Ihnen die Ausführung der folgenden Leistungen:</p>",
                    "<p>Wir melden uns rechtzeitig, um den Termin mit Ihnen abzustimmen. Bei Fragen erreichen Sie uns jederzeit.</p>" + GRUSS)),
            Map.entry(Dokumenttyp.RECHNUNG, new Texte(
                    ANREDE + "<p>vielen Dank für Ihren Auftrag. Für die ausgeführten Leistungen berechnen wir Ihnen:</p>",
                    "<p>Bitte überweisen Sie den Rechnungsbetrag bis zum {{ZAHLUNGSZIEL}} unter Angabe der Rechnungsnummer {{DOKUMENTNUMMER}} "
                            + "auf unser Konto.</p><p>Wir bedanken uns für Ihr Vertrauen.</p>" + GRUSS)),
            Map.entry(Dokumenttyp.TEILRECHNUNG, new Texte(
                    ANREDE + "<p>für die bisher ausgeführten Leistungen berechnen wir Ihnen:</p>",
                    "<p>Bitte überweisen Sie den Betrag bis zum {{ZAHLUNGSZIEL}} unter Angabe der Rechnungsnummer {{DOKUMENTNUMMER}} auf unser Konto. "
                            + "Die Restleistungen rechnen wir nach Fertigstellung ab.</p>" + GRUSS)),
            Map.entry(Dokumenttyp.ABSCHLAGSRECHNUNG, new Texte(
                    ANREDE + "<p>wie vereinbart stellen wir Ihnen eine Abschlagszahlung für das Bauvorhaben {{BAUVORHABEN}} in Rechnung:</p>",
                    "<p>Bitte überweisen Sie den Betrag bis zum {{ZAHLUNGSZIEL}} unter Angabe der Rechnungsnummer {{DOKUMENTNUMMER}} auf unser Konto. "
                            + "Die Abschlagszahlung wird in der Schlussrechnung verrechnet.</p>" + GRUSS)),
            Map.entry(Dokumenttyp.SCHLUSSRECHNUNG, new Texte(
                    ANREDE + "<p>vielen Dank für Ihren Auftrag. Die Arbeiten sind abgeschlossen. Wir berechnen Ihnen die Leistungen wie folgt, "
                            + "bereits geleistete Zahlungen sind abgezogen:</p>",
                    "<p>Bitte überweisen Sie den offenen Betrag bis zum {{ZAHLUNGSZIEL}} unter Angabe der Rechnungsnummer {{DOKUMENTNUMMER}} "
                            + "auf unser Konto.</p><p>Wir bedanken uns für die gute Zusammenarbeit.</p>" + GRUSS)),
            Map.entry(Dokumenttyp.STORNORECHNUNG, new Texte(
                    ANREDE + "<p>hiermit stornieren wir unsere Rechnung {{BEZUGSDOKUMENTNUMMER}} vom {{BEZUGSDOKUMENTDATUM}}. "
                            + "Der Betrag wird Ihnen gutgeschrieben:</p>",
                    "<p>Bitte entschuldigen Sie die Umstände. Bei Rückfragen sprechen Sie uns gerne an.</p>" + GRUSS)),
            Map.entry(Dokumenttyp.GUTSCHRIFT, new Texte(
                    ANREDE + "<p>zu unserer Rechnung {{BEZUGSDOKUMENTNUMMER}} vom {{BEZUGSDOKUMENTDATUM}} schreiben wir Ihnen Folgendes gut:</p>",
                    "<p>Den Betrag erstatten wir Ihnen auf Ihr Konto oder verrechnen ihn mit offenen Rechnungen. Bei Rückfragen sprechen Sie uns gerne an.</p>"
                            + GRUSS)),
            Map.entry(Dokumenttyp.ZAHLUNGSERINNERUNG, new Texte(
                    ANREDE + "<p>bei der Durchsicht unserer Buchhaltung ist uns aufgefallen, dass die unten genannte Rechnung noch offen ist. "
                            + "Sicher handelt es sich nur um ein Versehen.</p>",
                    "<p>Bitte überweisen Sie den offenen Betrag bis zum {{ZAHLUNGSZIEL}} auf unser Konto. Sollten Sie bereits gezahlt haben, "
                            + "betrachten Sie dieses Schreiben bitte als gegenstandslos.</p>" + GRUSS)),
            Map.entry(Dokumenttyp.ERSTE_MAHNUNG, new Texte(
                    ANREDE + "<p>leider konnten wir trotz unserer Zahlungserinnerung noch keinen Zahlungseingang für die unten genannte Rechnung feststellen.</p>",
                    "<p>Wir bitten Sie, den offenen Betrag bis zum {{ZAHLUNGSZIEL}} zu überweisen. Wenn es ein Problem mit der Rechnung gibt, "
                            + "melden Sie sich bitte kurz bei uns. Wir finden eine Lösung.</p>" + GRUSS)),
            Map.entry(Dokumenttyp.LIEFERSCHEIN, new Texte(
                    ANREDE + "<p>wie vereinbart, liefern wir Ihnen nachstehende Waren und/oder Leistungen:</p>",
                    "<p>Ware ordnungsgemäß erhalten:</p><p>&nbsp;</p><p>________________________________________</p><p>Datum, Unterschrift</p>")),
            Map.entry(Dokumenttyp.ZWEITE_MAHNUNG, new Texte(
                    ANREDE + "<p>die unten genannte Rechnung ist trotz unserer bisherigen Schreiben weiterhin nicht bezahlt.</p>",
                    "<p>Wir fordern Sie auf, den offenen Betrag bis zum {{ZAHLUNGSZIEL}} zu überweisen. "
                            + "Andernfalls müssen wir weitere Schritte einleiten. Bitte sprechen Sie uns an, wenn Sie Hilfe bei der Zahlung brauchen.</p>"
                            + GRUSS)),
            Map.entry(Dokumenttyp.BESTELLFORMULAR, new Texte(
                    ANREDE + "<p>hiermit bestellen wir die nachfolgend aufgeführten Positionen:</p>",
                    "<p>Bitte bestätigen Sie diese Bestellung und nennen uns den voraussichtlichen Liefertermin.</p>" + GRUSS)));

    private StandardVorlagenInhalt() {
    }

    static Texte texte(Dokumenttyp typ) {
        return TEXTE.get(typ);
    }

    private static final double SEITE_BREITE = 595;
    private static final double SEITE_HOEHE = 842;

    /** Das Layout mit den Standardeinstellungen. */
    static String layoutHtml() {
        return layoutHtml(VorlagenEinstellungen.standard());
    }

    /**
     * Das Layout als Vorlagen-HTML, im selben Format, das der Formular-Editor speichert. Die Einstellungen stehen als Meta-Tag mit darin,
     * damit sie beim PDF gelten und im Editor wieder änderbar sind.
     */
    static String layoutHtml(VorlagenEinstellungen einstellungen) {
        VorlagenEinstellungen e = (einstellungen == null ? VorlagenEinstellungen.standard() : einstellungen).bereinigt();
        List<String> seite1 = new ArrayList<>();
        List<String> seite2 = new ArrayList<>();
        int z = 1;
        int gr = e.getSchriftgroesse();
        int links = mm(e.getHauptblockLinks());
        int rechts = (int) SEITE_BREITE - mm(e.getHauptblockRechts());
        int breite = rechts - links;

        // ---- Fußzeile: Höhe und Lage bestimmen, daraus das Ende des Hauptblocks ----
        boolean fussDreiteilig = "DOKUMENT_FIRMA_SEITE".equals(e.getFusszeileTyp());
        boolean fussFirmendaten = "FIRMENDATEN".equals(e.getFusszeileTyp());
        int fussDrei = (int) Math.ceil(e.getFusszeileSchriftgroesse() * 1.4 * 3) + 2;
        // Die Fußzeile beginnt für drei Zeilen; die Box ist eine Zeile höher, damit ein Umbruch in der Firmenzeile nichts abschneidet
        int fussHoehe = fussDreiteilig ? fussDrei + (int) Math.ceil(e.getFusszeileSchriftgroesse() * 1.4) : fussFirmendaten ? 44 : 0;
        int fussOben = (int) SEITE_HOEHE - mm(fussHoehe == 0 ? 20 : 30) - (fussDreiteilig ? fussDrei : fussHoehe);
        int hauptUnten = fussOben - mm(e.getFusszeileAbstand());

        // ---- Seite 1: Briefkopf (Firmenblöcke und Logo: Enthält das Briefpapier schon Briefkopf und Fußzeile, lässt das PDF sie von selbst weg) ----
        seite1.add(block("logo", 1, rechts - 170, mm(14), z++, 170, 70, "{{FIRMENLOGO}}", 9, "400", "#4b5563", "right"));
        if (e.isFalzmarken()) {
            seite1.add(block("linie", 1, 0, mm(105), z++, mm(7), 1, null, 9, "400", "#374151", "left"));
            seite1.add(block("linie", 1, 0, mm(148.5), z++, mm(10), 1, null, 9, "400", "#374151", "left"));
            seite1.add(block("linie", 1, 0, mm(210), z++, mm(7), 1, null, 9, "400", "#374151", "left"));
        }

        // ---- Empfängerfeld nach DIN 5008: Absenderzeile, darunter die Anschrift des Kunden ----
        int adrLinks = mm(e.getAdresszeileLinks());
        int adrOben = mm(e.getAdresszeileOben());
        if (e.isAdresszeileAnzeigen()) {
            String zeile = e.getAdresszeileText().isBlank() ? "{{ABSENDERZEILE}}" : e.getAdresszeileText();
            seite1.add(block("text", 1, adrLinks, adrOben, z++, mm(85), 12, zeile, 7, "400", "#374151", "left"));
        }
        seite1.add(block("text", 1, adrLinks, adrOben + mm(9), z++, mm(85), mm(40), "{{KUNDENADRESSE}}", Math.min(14, gr + 1), "400", "#111827", "left"));

        // ---- Infoblock rechts neben der Anschrift: Dokumentnummer, Datum, Ansprechpartner ----
        if (e.isInfoblockAnzeigen()) {
            List<String> zeilen = new ArrayList<>();
            if (e.isInfoDokumentnummer()) zeilen.add("Dokumentennummer\t{{DOKUMENTNUMMER}}");
            if (e.isInfoProjektnummer()) zeilen.add("Projektnummer\t{{PROJEKTNUMMER}}");
            if (e.isInfoReferenzdokument()) zeilen.add("Referenzdokument\t{{BEZUGSDOKUMENT}}");
            if (e.isInfoKundennummer()) zeilen.add("Kundennummer\t{{KUNDENNUMMER}}");
            if (e.isInfoDatum()) zeilen.add("Datum\t{{DATUM}}");
            if (e.isInfoAnsprechpartner()) zeilen.add("Ansprechpartner\t{{BEARBEITER}}");
            if (e.isInfoTelefon()) zeilen.add("Telefon\t{{BEARBEITER_TELEFON}}");
            if (e.isInfoMobil()) zeilen.add("Mobil\t{{BEARBEITER_MOBIL}}");
            if (e.isInfoFax()) zeilen.add("Fax\t{{FIRMENFAX}}");
            if (e.isInfoEmail()) zeilen.add("E-Mail\t{{BEARBEITER_EMAIL}}");
            if (!zeilen.isEmpty()) {
                int hoehe = (int) Math.ceil(zeilen.size() * gr * 1.4) + 4;
                seite1.add(block("text", 1, mm(e.getInfoblockLinks()), mm(e.getInfoblockOben()), z++, mm(e.getInfoblockBreite()), hoehe,
                        String.join("\n", zeilen), gr, "400", "#111827", "left"));
            }
        }
        if (e.isFreitextAnzeigen() && !e.getFreitextInhalt().isBlank()) {
            seite1.add(block("text", 1, mm(e.getFreitextLinks()), mm(e.getFreitextOben()), z++, mm(e.getFreitextBreite()),
                    (int) Math.ceil(gr * 1.4 * (e.getFreitextInhalt().split("\n").length + 1)), e.getFreitextInhalt(), gr, "700", "#111827", "left"));
        }

        // ---- Betreffzeilen: Bauvorhaben und Titel ----
        int y = mm(e.getHauptblockOben());
        if (e.isBetreff1Anzeigen()) {
            String inhalt = "BETREFF".equals(e.getBetreff1Inhalt()) ? "{{BETREFF}}" : "{{BAUVORHABEN}}";
            String praefix = e.getBetreff1Praefix().isBlank() ? "" : e.getBetreff1Praefix().strip() + " ";
            int hoehe = (int) Math.ceil(e.getBetreff1Schriftgroesse() * 1.5);
            seite1.add(block("text", 1, links, y, z++, breite, hoehe, praefix + inhalt, e.getBetreff1Schriftgroesse(),
                    e.isBetreff1Fett() ? "700" : "400", "#111827", "left"));
            y += hoehe + 2;
        }
        if (e.isBetreff2Anzeigen()) {
            String titel = e.getBetreff2Text().isBlank() ? "{{DOKUMENTTYP}} Nr. {{DOKUMENTNUMMER}}" : e.getBetreff2Text();
            int hoehe = (int) Math.ceil(e.getBetreff2Schriftgroesse() * 1.5);
            seite1.add(block("text", 1, links, y, z++, breite, hoehe, titel, e.getBetreff2Schriftgroesse(),
                    e.isBetreff2Fett() ? "700" : "400", "#111827", "left"));
            y += hoehe + 6;
        }

        // ---- Leistungstabelle (nimmt auch die Texte vor und nach den Positionen auf) ----
        seite1.add(tabelle(1, links, y, z++, breite, Math.max(60, hauptUnten - y)));
        seite1.addAll(fusszeile(1, z, e, links, rechts, breite, fussOben, fussHoehe));

        // ---- Seite 2 (Folgeseiten) ----
        int z2 = 1;
        seite2.add(block("text", 2, links, mm(10), z2++, breite - 110, 14, "{{FIRMENNAME}}  ·  {{DOKUMENTTYP}} {{DOKUMENTNUMMER}}", 8, "400", "#6b7280", "left"));
        if (!fussDreiteilig) {
            seite2.add(block("seitenzahl", 2, rechts - 100, mm(10), z2++, 100, 14, null, 8, "400", "#6b7280", "right"));
        }
        seite2.add(tabelle(2, links, mm(22), z2++, breite, Math.max(60, hauptUnten - mm(22))));
        seite2.addAll(fusszeile(2, z2, e, links, rechts, breite, fussOben, fussHoehe));

        return "<meta name=\"page-format\" content=\"A4\" />" + e.alsMetaTag()
                + "<style>body{font-family:" + schriftFamilie(e.getSchriftart()) + ";}</style>"
                + "<div class=\"page\">" + String.join("", seite1) + "</div>"
                + "<div class=\"page\">" + String.join("", seite2) + "</div>";
    }

    private static String schriftFamilie(String art) {
        return switch (art) {
            case "TIMES" -> "'Times New Roman',Times,serif";
            case "COURIER" -> "'Courier New',Courier,monospace";
            default -> "Arial,Helvetica,sans-serif";
        };
    }

    /** Millimeter in Punkt (die Einheit der Vorlagen-Blöcke), auf ganze Punkte gerundet. */
    static int mm(double mm) {
        return (int) Math.round(mm * 72 / 25.4);
    }

    private static List<String> fusszeile(int seite, int ab, VorlagenEinstellungen e, int links, int rechts, int breite, int oben, int hoehe) {
        List<String> l = new ArrayList<>();
        int gr = e.getFusszeileSchriftgroesse();
        if ("DOKUMENT_FIRMA_SEITE".equals(e.getFusszeileTyp())) {
            // Links die Dokumentnummer, in der Mitte die Firma (drei Zeilen), rechts die Seitenzahl
            int seitenBreite = mm(14);
            int linksBreite = mm(30);
            l.add(block("text", seite, links, oben, ab, linksBreite, hoehe, "{{DOKUMENTTYP}} {{DOKUMENTNUMMER}}", gr, "400", "#374151", "left"));
            int mitteX = links + linksBreite + 4;
            l.add(block("text", seite, mitteX, oben, ab + 1, rechts - seitenBreite - 4 - mitteX, hoehe, "{{FIRMENFUSS}}", gr, "400", "#374151", "center"));
            l.add(block("seitenzahl", seite, rechts - seitenBreite, oben, ab + 2, seitenBreite, hoehe, null, gr, "400", "#374151", "right"));
        } else if ("FIRMENDATEN".equals(e.getFusszeileTyp())) {
            int drittel = (breite - 2 * 12) / 3;
            l.add(block("text", seite, links, oben, ab, drittel, hoehe, "{{FIRMENBANK}}", gr, "400", "#4b5563", "left"));
            l.add(block("text", seite, links + drittel + 12, oben, ab + 1, drittel, hoehe, "{{FIRMENSTEUER}}", gr, "400", "#4b5563", "left"));
            l.add(block("text", seite, links + 2 * (drittel + 12), oben, ab + 2, breite - 2 * (drittel + 12), hoehe, "{{FIRMENREGISTER}}", gr, "400", "#4b5563", "left"));
        }
        return l;
    }

    private static String tabelle(int seite, int x, int y, int z, int breite, int hoehe) {
        // Der Editor speichert für Tabellen nur Lage und Größe, die Spalten legt das PDF selbst fest
        return "<div data-block-type=\"table\" data-page=\"" + seite + "\" data-x=\"" + x + "\" data-y=\"" + y + "\" data-z=\"" + z
                + "\" data-width=\"" + breite + "\" data-height=\"" + hoehe + "\"></div>";
    }

    private static String block(String typ, int seite, int x, int y, int z, int breite, int hoehe, String inhalt,
            int schriftgroesse, String gewicht, String farbe, String ausrichtung) {
        String stil = "{\"fontSize\":" + schriftgroesse + ",\"fontWeight\":\"" + gewicht + "\",\"color\":\"" + farbe
                + "\",\"textAlign\":\"" + ausrichtung + "\"}";
        return "<div data-block-type=\"" + typ + "\" data-page=\"" + seite + "\" data-x=\"" + x + "\" data-y=\"" + y + "\" data-z=\"" + z
                + "\" data-width=\"" + breite + "\" data-height=\"" + hoehe + "\""
                + (inhalt == null ? "" : " data-content=\"" + kodiere(inhalt) + "\"")
                + " data-style=\"" + kodiere(stil) + "\"></div>";
    }

    /** Wie {@code encodeURIComponent} im Editor: Leerzeichen als %20, nicht als +. */
    private static String kodiere(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
