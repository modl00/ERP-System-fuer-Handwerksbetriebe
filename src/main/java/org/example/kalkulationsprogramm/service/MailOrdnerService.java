package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.example.email.MailSicherheit;
import org.springframework.stereotype.Service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.sun.mail.imap.IMAPFolder;

import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;

/**
 * Welche Ordner des Postfachs das Programm liest (E-Mail-Center). <b>Nur lesen:</b> Es wird nichts im Postfach verschoben, gelöscht oder markiert.
 * Gespeichert wird die Auswahl in {@code system_setting} ({@value #SCHLUESSEL}); ohne Auswahl bleibt es beim bisherigen Verhalten (Posteingang, Gesendet, alte Archivordner).
 * Zu jedem Ordner gehört ein Datum „seit“: Ältere Mails werden nicht importiert. Das Datum bekommt ein neu gewählter Ordner (heute), es sei denn, ältere Mails
 * sollen ausdrücklich nachgeladen werden. Eine spätere Änderung der Richtung lässt das Datum unverändert.
 */
@Service
@RequiredArgsConstructor
public class MailOrdnerService {

    public static final String SCHLUESSEL = "email.ordner.auswahl";
    static final int MAX_ORDNER = 100;
    static final int MAX_NAME = 300;
    private static final ObjectMapper JSON = new ObjectMapper();

    private final SystemSettingsService einstellungen;

    /** Ein gewählter Ordner. {@code richtung}: EINGANG oder GESENDET. {@code seit}: ISO-Datum, {@code null} = alle Mails. */
    public record Eintrag(String name, String richtung, String seit) { }

    /** Ein Ordner auf dem Server mit Vorschlag für die Richtung. */
    public record ServerOrdner(String name, String anzeige, String vorschlag) { }

    public record Uebersicht(boolean konfiguriert, List<Eintrag> ausgewaehlt, List<ServerOrdner> ordner) { }

    /** Die Eingabe des Benutzers. {@code nachladen}: auch ältere Mails holen (nur wirksam für neu gewählte Ordner). */
    public record Wahl(String name, String richtung, boolean nachladen) { }

    /** Die gespeicherte Auswahl; leer, wenn keine getroffen wurde oder der Text nicht lesbar ist. */
    public static List<Eintrag> lies(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Eintrag> alle = JSON.readValue(json, new TypeReference<List<Eintrag>>() { });
            List<Eintrag> gueltig = new ArrayList<>();
            for (Eintrag e : alle) {
                if (e != null && e.name() != null && !e.name().isBlank() && ("EINGANG".equals(e.richtung()) || "GESENDET".equals(e.richtung()))) {
                    gueltig.add(e);
                }
            }
            return gueltig;
        } catch (Exception e) {
            return List.of();
        }
    }

    public List<Eintrag> gespeichert() {
        return lies(einstellungen.get(SCHLUESSEL, ""));
    }

    /** Verbindet sich mit dem Postfach und listet die Ordner auf. */
    public Uebersicht lade() {
        List<Eintrag> gewaehlt = gespeichert();
        return new Uebersicht(!gewaehlt.isEmpty(), gewaehlt, serverOrdner());
    }

    List<ServerOrdner> serverOrdner() {
        var zugang = einstellungen.getStandardImapZugang();
        if (zugang.username() == null || zugang.username().isBlank() || zugang.password() == null || zugang.password().isBlank()) {
            throw new IllegalStateException("Das Postfach ist noch nicht eingerichtet. Bitte zuerst Adresse und Passwort unter „Haupt-Postfach“ speichern.");
        }
        Properties props = MailSicherheit.imap(zugang.port(), 15000, 30000);
        Session session = Session.getInstance(props);
        try (Store store = session.getStore(MailSicherheit.imapProtokoll(zugang.port()))) {
            store.connect(zugang.host(), zugang.port(), zugang.username(), zugang.password());
            Map<String, ServerOrdner> nachName = new LinkedHashMap<>();
            for (Folder f : store.getDefaultFolder().list("*")) {
                boolean selektierbar = (f.getType() & Folder.HOLDS_MESSAGES) != 0;
                if (!selektierbar) {
                    continue;
                }
                boolean gesendet = false;
                if (f instanceof IMAPFolder imap) {
                    try {
                        for (String a : imap.getAttributes()) {
                            if ("\\Sent".equalsIgnoreCase(a)) {
                                gesendet = true;
                            }
                        }
                    } catch (MessagingException ignored) {
                        // ohne Attribute bleibt es beim Namen
                    }
                }
                String name = f.getFullName();
                gesendet = gesendet || EmailImportService.istGesendetName(name);
                nachName.put(name, new ServerOrdner(name, f.getName(), gesendet ? "GESENDET" : "EINGANG"));
            }
            return new ArrayList<>(nachName.values());
        } catch (MessagingException e) {
            throw new IllegalStateException("Die Verbindung zum Postfach ist fehlgeschlagen: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * Speichert die Auswahl. Bereits gewählte Ordner behalten ihr Datum; neue bekommen heute oder (bei {@code nachladen}) kein Datum und holen dann auch ältere Mails.
     *
     * @throws IllegalArgumentException bei ungültigen Angaben
     */
    public List<Eintrag> speichere(List<Wahl> wahl, LocalDate heute) {
        if (wahl == null) {
            throw new IllegalArgumentException("Bitte mindestens einen Ordner wählen.");
        }
        if (wahl.size() > MAX_ORDNER) {
            throw new IllegalArgumentException("Es lassen sich höchstens " + MAX_ORDNER + " Ordner wählen.");
        }
        Map<String, Eintrag> bisher = new LinkedHashMap<>();
        for (Eintrag e : gespeichert()) {
            bisher.put(e.name(), e);
        }
        Map<String, Eintrag> neu = new LinkedHashMap<>();
        for (Wahl w : wahl) {
            if (w == null || w.name() == null || w.name().isBlank() || w.name().length() > MAX_NAME) {
                throw new IllegalArgumentException("Ungültiger Ordnername.");
            }
            if (!"EINGANG".equals(w.richtung()) && !"GESENDET".equals(w.richtung())) {
                throw new IllegalArgumentException("Bitte für jeden Ordner „Eingang“ oder „Gesendet“ wählen.");
            }
            String name = w.name().strip();
            Eintrag alt = bisher.get(name);
            String seit = alt != null ? alt.seit() : (w.nachladen() ? null : heute.toString());
            neu.put(name, new Eintrag(name, w.richtung(), seit));
        }
        List<Eintrag> ergebnis = new ArrayList<>(neu.values());
        try {
            einstellungen.save(SCHLUESSEL, ergebnis.isEmpty() ? "" : JSON.writeValueAsString(ergebnis), "Ordner, die das E-Mail-Center liest");
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Die Auswahl konnte nicht gespeichert werden.");
        }
        return ergebnis;
    }
}
