package org.example.kalkulationsprogramm.service;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Zugang zur Beiträge-Schnittstelle einer Firmen-Website (Adresse und Schlüssel), einstellbar im Programm. Eine Einstellung hat Vorrang vor den
 * Umgebungsvariablen {@code WEBSITE_BEITRAEGE_BASE_URL} und {@code WEBSITE_BEITRAEGE_API_TOKEN}; ohne Adresse gilt die Website aus den Firmendaten.
 * Der Schlüssel wird nie zurückgegeben, nur ob einer gesetzt ist.
 */
@Service
public class WebsiteZugangService {

    static final String ADRESSE = "website.beitraege.base-url";
    static final String TOKEN = "website.beitraege.api-token";
    private static final int MAX_ADRESSE = 200;
    private static final int MAX_TOKEN = 500;

    /** {@code eingerichtet}: Schlüssel gesetzt und eine Adresse (eigene oder aus den Firmendaten) vorhanden. */
    public record Konfiguration(String adresse, boolean tokenGesetzt, boolean adresseAusFirmendaten, boolean eingerichtet) { }

    private final SystemSettingsService einstellungen;
    private final FirmeninformationService firmeninformationService;
    private final String standardAdresse;
    private final String standardToken;

    public WebsiteZugangService(SystemSettingsService einstellungen, FirmeninformationService firmeninformationService,
            @Value("${website.beitraege.base-url:}") String standardAdresse, @Value("${website.beitraege.api-token:}") String standardToken) {
        this.einstellungen = einstellungen;
        this.firmeninformationService = firmeninformationService;
        this.standardAdresse = standardAdresse == null ? "" : standardAdresse.strip();
        this.standardToken = standardToken == null ? "" : standardToken.strip();
    }

    /** Die Adresse der Website: Einstellung, sonst Umgebungsvariable; leer, wenn beides fehlt (dann gilt die Firmenwebsite). */
    public String adresse() {
        String gespeichert = text(einstellungen.get(ADRESSE, ""));
        return gespeichert.isEmpty() ? standardAdresse : gespeichert;
    }

    /** Der Zugangsschlüssel: Einstellung, sonst Umgebungsvariable. */
    public String token() {
        String gespeichert = text(einstellungen.get(TOKEN, ""));
        return gespeichert.isEmpty() ? standardToken : gespeichert;
    }

    @Transactional(readOnly = true)
    public Konfiguration konfiguration() {
        String adresse = adresse();
        boolean ausFirma = false;
        boolean hatAdresse = !adresse.isEmpty();
        if (!hatAdresse) {
            String firma = firmenWebsite();
            ausFirma = !firma.isEmpty();
            hatAdresse = ausFirma;
        }
        boolean tokenGesetzt = !token().isEmpty();
        return new Konfiguration(adresse, tokenGesetzt, ausFirma, tokenGesetzt && hatAdresse);
    }

    /**
     * Speichert Adresse und Schlüssel. Leere Adresse = zurück zur Umgebungsvariable beziehungsweise Firmenwebsite. {@code token}: {@code null} =
     * unverändert, leer = entfernen. Die Adresse muss https sein ({@code http} nur für localhost), ohne Zugangsdaten, Parameter und Pfad-Tricks.
     */
    @Transactional
    public Konfiguration speichern(String adresse, String token) {
        String a = adresse == null ? "" : adresse.strip();
        if (!a.isEmpty()) {
            pruefeAdresse(a);
        }
        if (token != null && token.strip().length() > MAX_TOKEN) {
            throw fehler("Der Schlüssel ist zu lang.");
        }
        einstellungen.save(ADRESSE, a, "Adresse der Website für die Beiträge-Schnittstelle");
        if (token != null) {
            einstellungen.save(TOKEN, token.strip(), "Zugangsschlüssel der Beiträge-Schnittstelle der Website");
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
                throw fehler("Die Adresse muss mit https:// beginnen, sonst ginge der Schlüssel ungeschützt über die Leitung (nur zum Testen am eigenen Rechner ist http://localhost erlaubt).");
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

    private String firmenWebsite() {
        try {
            String w = firmeninformationService.getFirmeninformation().getWebsite();
            return w == null ? "" : w.strip();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String text(String wert) {
        return wert == null ? "" : wert.strip();
    }

    private static ResponseStatusException fehler(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
