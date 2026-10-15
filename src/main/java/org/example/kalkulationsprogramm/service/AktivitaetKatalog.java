package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Welche Aktionen im Aktivitätsprotokoll landen (Issue #93). Es ist bewusst eine <b>feste Liste</b> der wichtigen Änderungen: Alles, was hier nicht steht, wird
 * nicht protokolliert, damit nichts Unbeabsichtigtes (und nichts Inhaltliches) im Protokoll landet. Eine Regel nennt Methode und Adressmuster des Controllers
 * (Platzhalter in geschweiften Klammern; ihre Namen sind egal), die Art, die Objektart und den Satz für die Anzeige. Das Objekt ist der erste Platzhalter der Adresse.
 */
public final class AktivitaetKatalog {

    public static final String ANGELEGT = "ANGELEGT";
    public static final String GEAENDERT = "GEAENDERT";
    public static final String GELOESCHT = "GELOESCHT";
    public static final String VERSENDET = "VERSENDET";
    public static final String GEBUCHT = "GEBUCHT";
    public static final String ZUGEORDNET = "ZUGEORDNET";
    public static final String ABGESCHLOSSEN = "ABGESCHLOSSEN";

    /** Eine Regel. {@code objektTyp} leer heißt: kein einzelnes Objekt (zum Beispiel eine E-Mail). */
    public record Regel(String methode, String muster, String art, String objektTyp, String text) { }

    private static final Pattern PLATZHALTER = Pattern.compile("\\{[^/}]*}");

    private static final List<Regel> REGELN = List.of(
            r("POST", "/api/projekte", ANGELEGT, "Projekt", "Projekt angelegt"),
            r("PUT", "/api/projekte/{}", GEAENDERT, "Projekt", "Projekt geändert"),
            r("PATCH", "/api/projekte/{}/abgeschlossen", ABGESCHLOSSEN, "Projekt", "Projekt abgeschlossen oder wieder geöffnet"),
            r("POST", "/api/projekte/{}/zeiten", GEBUCHT, "Projekt", "Zeiten im Projekt gebucht"),
            r("POST", "/api/projekte/{}/materialkosten/artikel", GEAENDERT, "Projekt", "Material im Projekt hinzugefügt"),
            r("POST", "/api/projekte/{}/material-abrechnung/{}", ZUGEORDNET, "Projekt", "Material einer Rechnung zugeordnet"),
            r("POST", "/api/kunden", ANGELEGT, "Kunde", "Kunde angelegt"),
            r("PUT", "/api/kunden/{}", GEAENDERT, "Kunde", "Kunde geändert"),
            r("POST", "/api/anfragen", ANGELEGT, "Anfrage", "Anfrage angelegt"),
            r("PUT", "/api/anfragen/{}", GEAENDERT, "Anfrage", "Anfrage geändert"),
            r("DELETE", "/api/anfragen/{}", GELOESCHT, "Anfrage", "Anfrage gelöscht"),
            r("POST", "/api/ausgangs-dokumente", ANGELEGT, "Dokument", "Dokument angelegt"),
            r("PUT", "/api/ausgangs-dokumente/{}", GEAENDERT, "Dokument", "Dokument geändert"),
            r("POST", "/api/ausgangs-dokumente/{}/buchen", GEBUCHT, "Dokument", "Dokument gebucht"),
            r("POST", "/api/ausgangs-dokumente/{}/email-versendet", VERSENDET, "Dokument", "Dokument per E-Mail versendet"),
            r("POST", "/api/ausgangs-dokumente/{}/pdf-speichern", GEAENDERT, "Dokument", "PDF des Dokuments gespeichert"),
            r("POST", "/api/ausgangs-dokumente/{}/storno", GEAENDERT, "Dokument", "Dokument storniert"),
            r("DELETE", "/api/ausgangs-dokumente/{}", GELOESCHT, "Dokument", "Dokument gelöscht"),
            r("POST", "/api/email/send", VERSENDET, null, "E-Mail gesendet"),
            r("POST", "/api/email/send/anfrage", VERSENDET, null, "E-Mail zu einer Anfrage gesendet"),
            r("POST", "/api/emails/send", VERSENDET, null, "E-Mail gesendet"),
            r("POST", "/api/artikel", ANGELEGT, "Artikel", "Artikel angelegt"),
            r("PUT", "/api/artikel/{}/stammdaten", GEAENDERT, "Artikel", "Artikel geändert"),
            r("POST", "/api/lager/buchungen", GEBUCHT, null, "Lagerbuchung"),
            r("POST", "/api/lager/umbuchungen", GEBUCHT, null, "Umbuchung im Lager"),
            r("POST", "/api/lieferanten", ANGELEGT, "Lieferant", "Lieferant angelegt"),
            r("PUT", "/api/lieferanten/{}", GEAENDERT, "Lieferant", "Lieferant geändert"),
            r("POST", "/api/lieferanten/{}/dokumente/import", ANGELEGT, "Lieferant", "Dokument beim Lieferanten abgelegt"),
            r("POST", "/api/bestellungen-uebersicht/zuordnen", ZUGEORDNET, null, "Rechnung Projekten zugeordnet"),
            r("POST", "/api/lieferanten-bestellungen", ANGELEGT, "Bestellung", "Bestellung angelegt"),
            r("PUT", "/api/lieferanten-bestellungen/{}", GEAENDERT, "Bestellung", "Bestellung geändert"),
            r("POST", "/api/lieferanten-bestellungen/{}/bestellen", VERSENDET, "Bestellung", "Bestellung bestellt"),
            r("POST", "/api/lieferanten-bestellungen/{}/stornieren", GEAENDERT, "Bestellung", "Bestellung storniert"),
            r("DELETE", "/api/lieferanten-bestellungen/{}", GELOESCHT, "Bestellung", "Bestellentwurf gelöscht"),
            r("POST", "/api/buchhaltung/belege", ANGELEGT, "Beleg", "Beleg erfasst"),
            r("PUT", "/api/buchhaltung/belege/{}", GEAENDERT, "Beleg", "Beleg geändert"),
            r("DELETE", "/api/buchhaltung/belege/{}", GELOESCHT, "Beleg", "Beleg gelöscht"),
            r("POST", "/api/buchhaltung/kassenbuch/buchungen", GEBUCHT, null, "Kassenbuchung"),
            r("POST", "/api/zeitverwaltung/monatsabschluesse/{}/{}/{}/abschliessen", ABGESCHLOSSEN, "Mitarbeiter", "Monat abgeschlossen"),
            r("POST", "/api/zeitverwaltung/monatsabschluesse/{}/{}/{}/oeffnen", GEAENDERT, "Mitarbeiter", "Monat wieder geöffnet"),
            r("POST", "/api/mitarbeiter", ANGELEGT, "Mitarbeiter", "Mitarbeiter angelegt"),
            r("PUT", "/api/mitarbeiter/{}", GEAENDERT, "Mitarbeiter", "Mitarbeiter geändert"));

    private static final Map<String, Regel> NACH_SCHLUESSEL = REGELN.stream().collect(Collectors.toUnmodifiableMap(r -> schluessel(r.methode(), r.muster()), r -> r));

    private AktivitaetKatalog() { }

    private static Regel r(String methode, String muster, String art, String objektTyp, String text) {
        return new Regel(methode, normalisiere(muster), art, objektTyp, text);
    }

    /** Die Regel zu Methode und dem Adressmuster, mit dem Spring die Anfrage zugeordnet hat (zum Beispiel {@code /api/projekte/{projektID}}); sonst leer. */
    public static Optional<Regel> finde(String methode, String muster) {
        if (methode == null || muster == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(NACH_SCHLUESSEL.get(schluessel(methode.toUpperCase(java.util.Locale.ROOT), normalisiere(muster))));
    }

    public static List<Regel> alle() {
        return REGELN;
    }

    static String normalisiere(String muster) {
        String m = PLATZHALTER.matcher(muster).replaceAll("{}");
        return m.length() > 1 && m.endsWith("/") ? m.substring(0, m.length() - 1) : m;
    }

    private static String schluessel(String methode, String muster) {
        return methode + " " + muster;
    }
}
