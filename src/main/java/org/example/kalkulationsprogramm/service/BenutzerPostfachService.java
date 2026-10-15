package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.BenutzerPostfach;
import org.example.kalkulationsprogramm.repository.BenutzerPostfachRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;

/**
 * Persönliche Postfächer je Benutzer verwalten (Stufe 1 von Issue #272): anlegen, ändern, löschen, Verbindung testen. Jeder Benutzer sieht und ändert nur seine eigenen.
 * Passwörter werden verschlüsselt gespeichert und nie zurückgegeben. Der Abruf und die Sichtbarkeit der Mails folgen in den nächsten Stufen.
 *
 * <p>Damit niemand den Server als Werkzeug zum Abtasten des eigenen Netzes nutzen kann, sind nur die üblichen Mail-Ports erlaubt.</p>
 */
@Service
@RequiredArgsConstructor
public class BenutzerPostfachService {

    static final int MAX_JE_BENUTZER = 10;
    private static final Set<Integer> IMAP_PORTS = Set.of(143, 993);
    private static final Set<Integer> SMTP_PORTS = Set.of(25, 465, 587);
    private static final Pattern HOST = Pattern.compile("^[A-Za-z0-9]([A-Za-z0-9.-]{0,253}[A-Za-z0-9])?$");
    private static final Pattern MAIL = Pattern.compile("^[^@\\s]+@[^@\\s.]+(?:\\.[^@\\s.]+)+$");

    private final BenutzerPostfachRepository postfaecher;
    private final PasswortSchutz schutz;

    public record PostfachDto(Long id, String bezeichnung, String emailAdresse, String imapHost, int imapPort, String smtpHost, Integer smtpPort, String benutzername,
            boolean passwortGesetzt, boolean aktiv, LocalDateTime erstelltAm) { }

    /** Eingabe zum Anlegen und Ändern. Beim Ändern bleibt das Passwort, wenn {@code passwort} leer ist. */
    public record Eingabe(String bezeichnung, String emailAdresse, String imapHost, Integer imapPort, String smtpHost, Integer smtpPort, String benutzername, String passwort,
            Boolean aktiv) { }

    public record Testergebnis(boolean ok, String meldung) { }

    @Transactional(readOnly = true)
    public List<PostfachDto> liste(Long profilId) {
        return postfaecher.findByProfilIdOrderByBezeichnungAscIdAsc(profilId).stream().map(BenutzerPostfachService::dto).toList();
    }

    @Transactional
    public PostfachDto anlegen(Long profilId, Eingabe e) {
        if (postfaecher.countByProfilId(profilId) >= MAX_JE_BENUTZER) {
            throw new IllegalArgumentException("Es lassen sich höchstens " + MAX_JE_BENUTZER + " Postfächer anlegen.");
        }
        if (e == null || e.passwort() == null || e.passwort().isEmpty()) {
            throw new IllegalArgumentException("Bitte das Passwort des Postfachs eingeben.");
        }
        BenutzerPostfach p = new BenutzerPostfach();
        p.setProfilId(profilId);
        p.setErstelltAm(LocalDateTime.now());
        uebernehme(p, e);
        p.setPasswortVerschluesselt(schutz.verschluessele(e.passwort()));
        return dto(postfaecher.save(p));
    }

    @Transactional
    public PostfachDto aendern(Long profilId, Long id, Eingabe e) {
        BenutzerPostfach p = eigenes(profilId, id);
        uebernehme(p, e);
        if (e.passwort() != null && !e.passwort().isEmpty()) {
            p.setPasswortVerschluesselt(schutz.verschluessele(e.passwort()));
        }
        return dto(postfaecher.save(p));
    }

    @Transactional
    public void loesche(Long profilId, Long id) {
        postfaecher.delete(eigenes(profilId, id));
    }

    /**
     * Prüft die Anmeldung am Postfach. Mit {@code id} und leerem Passwort gilt das gespeicherte; ohne {@code id} werden die Angaben aus dem Formular geprüft (noch nicht gespeichert).
     */
    @Transactional(readOnly = true)
    public Testergebnis teste(Long profilId, Long id, Eingabe e) {
        String passwort = e == null ? null : e.passwort();
        BenutzerPostfach gespeichert = id == null ? null : eigenes(profilId, id);
        if ((passwort == null || passwort.isEmpty()) && gespeichert != null) {
            passwort = schutz.entschluessele(gespeichert.getPasswortVerschluesselt());
        }
        if (e == null || passwort == null || passwort.isEmpty()) {
            throw new IllegalArgumentException("Bitte das Passwort eingeben.");
        }
        String host = pruefeHost(e.imapHost(), "Der Empfangs-Server");
        int port = pruefePort(e.imapPort(), IMAP_PORTS, "Empfang", "143 oder 993");
        String benutzer = leerZuNull(e.benutzername()) != null ? e.benutzername().strip() : (e.emailAdresse() == null ? "" : e.emailAdresse().strip());
        if (benutzer.isEmpty()) {
            throw new IllegalArgumentException("Bitte den Benutzernamen (meist die E-Mail-Adresse) eingeben.");
        }
        Properties props = imapEigenschaften(port, 10000, 15000);
        try (Store store = Session.getInstance(props).getStore(port == 143 ? "imap" : "imaps")) {
            store.connect(host, port, benutzer, passwort);
            return new Testergebnis(true, "Die Anmeldung hat geklappt.");
        } catch (AuthenticationFailedException ex) {
            return new Testergebnis(false, "Die Anmeldung wurde abgelehnt. Bitte Benutzername und Passwort prüfen (bei Gmail oder Microsoft 365 ist oft ein App-Passwort nötig).");
        } catch (MessagingException ex) {
            return new Testergebnis(false, "Keine Verbindung zum Server: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    private BenutzerPostfach eigenes(Long profilId, Long id) {
        return postfaecher.findByIdAndProfilId(id, profilId).orElseThrow(() -> new IllegalArgumentException("Das Postfach gibt es nicht."));
    }

    private void uebernehme(BenutzerPostfach p, Eingabe e) {
        if (e == null) {
            throw new IllegalArgumentException("Keine Angaben.");
        }
        String bezeichnung = leerZuNull(e.bezeichnung());
        if (bezeichnung == null || bezeichnung.length() > 120) {
            throw new IllegalArgumentException("Bitte einen Namen für das Postfach angeben (höchstens 120 Zeichen).");
        }
        String adresse = leerZuNull(e.emailAdresse());
        if (adresse == null || adresse.length() > 255 || !MAIL.matcher(adresse).matches()) {
            throw new IllegalArgumentException("Bitte eine gültige E-Mail-Adresse angeben.");
        }
        p.setBezeichnung(bezeichnung);
        p.setEmailAdresse(adresse);
        p.setImapHost(pruefeHost(e.imapHost(), "Der Empfangs-Server"));
        p.setImapPort(pruefePort(e.imapPort(), IMAP_PORTS, "Empfang", "143 oder 993"));
        String smtp = leerZuNull(e.smtpHost());
        p.setSmtpHost(smtp == null ? null : pruefeHost(smtp, "Der Sende-Server"));
        p.setSmtpPort(smtp == null ? null : pruefePort(e.smtpPort(), SMTP_PORTS, "Versand", "25, 465 oder 587"));
        String benutzer = leerZuNull(e.benutzername());
        if (benutzer != null && benutzer.length() > 255) {
            throw new IllegalArgumentException("Der Benutzername ist zu lang.");
        }
        p.setBenutzername(benutzer == null ? adresse : benutzer);
        if (e.aktiv() != null) {
            p.setAktiv(e.aktiv());
        }
    }

    private static String pruefeHost(String host, String wer) {
        String h = leerZuNull(host);
        if (h == null || !HOST.matcher(h).matches()) {
            throw new IllegalArgumentException(wer + " fehlt oder ist ungültig (zum Beispiel imap.example.de).");
        }
        return h;
    }

    private static int pruefePort(Integer port, Set<Integer> erlaubt, String wofuer, String liste) {
        if (port == null || !erlaubt.contains(port)) {
            throw new IllegalArgumentException("Für " + wofuer + " sind nur die Ports " + liste + " erlaubt.");
        }
        return port;
    }

    private static String leerZuNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static PostfachDto dto(BenutzerPostfach p) {
        return new PostfachDto(p.getId(), p.getBezeichnung(), p.getEmailAdresse(), p.getImapHost(), p.getImapPort(), p.getSmtpHost(), p.getSmtpPort(), p.getBenutzername(),
                p.getPasswortVerschluesselt() != null && !p.getPasswortVerschluesselt().isBlank(), p.isAktiv(), p.getErstelltAm());
    }

    // MailSicherheit wurde im Upstream-Merge entfernt; Logik hier inlinen
    private static Properties imapEigenschaften(int port, int verbindungMs, int lesenMs) {
        boolean ssl = port != 143;
        String protokoll = ssl ? "imaps" : "imap";
        Properties props = new Properties();
        props.put("mail.store.protocol", protokoll);
        props.put("mail.mime.address.strict", "false");
        if (ssl) {
            props.put("mail.imaps.ssl.enable", "true");
        } else {
            props.put("mail.imap.starttls.enable", "true");
            props.put("mail.imap.starttls.required", "true");
        }
        props.put("mail." + protokoll + ".connectiontimeout", String.valueOf(verbindungMs));
        props.put("mail." + protokoll + ".timeout", String.valueOf(lesenMs));
        return props;
    }
}
