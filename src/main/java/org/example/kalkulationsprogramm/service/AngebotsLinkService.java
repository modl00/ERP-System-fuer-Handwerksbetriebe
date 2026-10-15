package org.example.kalkulationsprogramm.service;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Einstellung für den Link „Angebot ansehen und annehmen“ in Angebots-E-Mails (#88).
 *
 * <p>Der Link führt auf die <em>öffentliche</em> Adresse des Programms. Läuft das Programm nur im eigenen
 * Netz, kann ein Kunde ihn nicht öffnen; dann bleibt der Link aus, statt einen toten Link zu verschicken.
 * Ein Link entsteht nur, wenn der Schalter an <strong>und</strong> eine Adresse eingetragen ist.</p>
 *
 * <p>Bestandsbetriebe: Wurde nie etwas eingestellt, gilt der Link als eingeschaltet, wenn die Konfiguration
 * {@code freigabe.public-base-url} (Umgebungsvariable {@code FREIGABE_PUBLIC_BASE_URL}) eine Adresse enthält. Es gibt
 * keine eingebaute Adresse mehr; ohne Eintrag bleibt der Link aus.</p>
 */
@Service
public class AngebotsLinkService {

    static final String AKTIV = "freigabe.link.aktiv";
    static final String ADRESSE = "freigabe.link.adresse";
    static final String TAGE = "freigabe.link.gueltigkeit-tage";
    static final int MIN_TAGE = 1;
    static final int MAX_TAGE = 365;
    private static final int MAX_ADRESSE = 200;

    /**
     * @param aktiv                 Schalter (eigene Einstellung, sonst "an", wenn die Konfiguration eine Adresse hat)
     * @param adresse               wirksame öffentliche Adresse, leer wenn keine
     * @param adresseAusKonfiguration die Adresse stammt aus der Konfigurationsdatei, nicht aus den Einstellungen
     * @param moeglich              Schalter an und Adresse vorhanden: Angebots-E-Mails bekommen den Link
     * @param gueltigkeitTage       Vorschlag für die Gültigkeit des Links in Tagen
     */
    public record Konfiguration(boolean aktiv, String adresse, boolean adresseAusKonfiguration, boolean moeglich, int gueltigkeitTage) { }

    /** Was der Editor wissen muss (für alle angemeldeten Benutzer, ohne die Adresse). */
    public record Status(boolean moeglich, int gueltigkeitTage) { }

    private final SystemSettingsService einstellungen;
    private final String standardAdresse;

    public AngebotsLinkService(SystemSettingsService einstellungen, @Value("${freigabe.public-base-url:}") String standardAdresse) {
        this.einstellungen = einstellungen;
        this.standardAdresse = standardAdresse == null ? "" : standardAdresse.strip();
    }

    public boolean aktiv() {
        String gespeichert = text(einstellungen.get(AKTIV, ""));
        return gespeichert.isEmpty() ? !standardAdresse.isEmpty() : Boolean.parseBoolean(gespeichert);
    }

    /** Die wirksame Adresse ohne abschließenden Schrägstrich; leer, wenn keine eingestellt ist. */
    public String adresse() {
        String gespeichert = text(einstellungen.get(ADRESSE, ""));
        String a = gespeichert.isEmpty() ? standardAdresse : gespeichert;
        return a.replaceAll("/+$", "");
    }

    /** Bekommen Angebots-E-Mails den Link? Nur mit eingeschaltetem Schalter und vorhandener Adresse. */
    public boolean linkMoeglich() {
        return aktiv() && !adresse().isEmpty();
    }

    public int gueltigkeitTage() {
        try {
            int t = Integer.parseInt(text(einstellungen.get(TAGE, "")));
            return t >= MIN_TAGE && t <= MAX_TAGE ? t : DokumentFreigabeService.DEFAULT_GUELTIGKEITS_TAGE;
        } catch (NumberFormatException e) {
            return DokumentFreigabeService.DEFAULT_GUELTIGKEITS_TAGE;
        }
    }

    public Status status() {
        return new Status(linkMoeglich(), gueltigkeitTage());
    }

    @Transactional(readOnly = true)
    public Konfiguration konfiguration() {
        boolean eigeneAdresse = !text(einstellungen.get(ADRESSE, "")).isEmpty();
        return new Konfiguration(aktiv(), adresse(), !eigeneAdresse && !standardAdresse.isEmpty(), linkMoeglich(), gueltigkeitTage());
    }

    /**
     * Speichert Schalter, Adresse und Standard-Gültigkeit. Einschalten geht nur mit Adresse. Die Adresse muss
     * https sein ({@code http} nur für localhost) und darf keine Zugangsdaten, Parameter oder Pfad-Tricks enthalten.
     */
    @Transactional
    public Konfiguration speichern(boolean aktiv, String adresse, Integer gueltigkeitTage) {
        String a = adresse == null ? "" : adresse.strip();
        if (!a.isEmpty()) {
            pruefeAdresse(a);
        }
        if (aktiv && a.isEmpty() && standardAdresse.isEmpty()) {
            throw fehler("Zum Einschalten bitte zuerst die öffentliche Adresse eintragen, unter der Ihre Kunden das Programm erreichen.");
        }
        if (gueltigkeitTage != null && (gueltigkeitTage < MIN_TAGE || gueltigkeitTage > MAX_TAGE)) {
            throw fehler("Die Gültigkeit muss zwischen " + MIN_TAGE + " und " + MAX_TAGE + " Tagen liegen.");
        }
        einstellungen.save(AKTIV, Boolean.toString(aktiv), "Link „Angebot ansehen und annehmen“ in Angebots-E-Mails");
        einstellungen.save(ADRESSE, a, "Öffentliche Adresse des Programms für den Annahme-Link");
        if (gueltigkeitTage != null) {
            einstellungen.save(TAGE, Integer.toString(gueltigkeitTage), "Standard-Gültigkeit des Annahme-Links in Tagen");
        }
        return konfiguration();
    }

    static void pruefeAdresse(String adresse) {
        if (adresse.length() > MAX_ADRESSE) {
            throw fehler("Die Adresse ist zu lang.");
        }
        try {
            URI uri = new URI(adresse);
            String schema = uri.getScheme();
            boolean lokal = "http".equalsIgnoreCase(schema) && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            if (schema == null || !("https".equalsIgnoreCase(schema) || lokal)) {
                throw fehler("Die Adresse muss mit https:// beginnen, sonst warnt der Browser Ihre Kunden (nur zum Testen am eigenen Rechner ist http://localhost erlaubt).");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw fehler("In der Adresse fehlt der Rechnername.");
            }
            if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw fehler("Die Adresse darf keine Zugangsdaten oder Parameter enthalten.");
            }
        } catch (URISyntaxException e) {
            throw fehler("Die Adresse ist ungültig.");
        }
    }

    private static String text(String wert) {
        return wert == null ? "" : wert.strip();
    }

    private static ResponseStatusException fehler(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
