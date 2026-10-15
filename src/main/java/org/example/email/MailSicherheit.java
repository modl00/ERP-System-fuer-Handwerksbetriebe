package org.example.email;

import java.util.Properties;

/**
 * Wie die Verbindung zum Mailserver verschlüsselt wird, ergibt sich aus dem <b>Port</b> (so wie es die Anbieter vorgeben):
 * <ul>
 *   <li>Versand: die üblichen Ports für STARTTLS (<b>587</b>, auch 25 und 2525) = <b>STARTTLS</b>, und zwar verlangt (kann der Server das nicht, scheitert die
 *       Verbindung mit einer klaren Meldung, statt Passwörter unverschlüsselt zu senden); jeder andere Port (zum Beispiel <b>465</b>) = SSL/TLS von Anfang an.</li>
 *   <li>Empfang: Port <b>143</b> = <b>STARTTLS</b> (verlangt); jeder andere Port (zum Beispiel <b>993</b>) = SSL/TLS von Anfang an.</li>
 * </ul>
 * Bestehende Einrichtungen (465 und 993) verhalten sich dadurch unverändert. Eine Stelle für alle Verbindungen, damit Versand, Empfang, Test und Sentkopie
 * nie unterschiedlich verschlüsseln.
 */
public final class MailSicherheit {

    public static final int SMTP_SSL_PORT = 465;
    public static final int IMAP_STARTTLS_PORT = 143;

    private MailSicherheit() {
    }

    /** SSL/TLS von Anfang an (jeder Port außer 587, 25 und 2525)? Sonst STARTTLS. */
    public static boolean smtpMitSsl(int port) {
        return port != 587 && port != 25 && port != 2525;
    }

    /** Setzt die Verschlüsselung des Versands nach dem Port (siehe Klassenbeschreibung). */
    public static void smtp(Properties props, int port) {
        if (smtpMitSsl(port)) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.socketFactory.port", String.valueOf(port));
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }
    }

    /** SSL/TLS von Anfang an (jeder Port außer 143)? Sonst STARTTLS. */
    public static boolean imapMitSsl(int port) {
        return port != IMAP_STARTTLS_PORT;
    }

    /** Das Protokoll für {@code session.getStore(...)}: {@code imaps} oder {@code imap} (mit STARTTLS). */
    public static String imapProtokoll(int port) {
        return imapMitSsl(port) ? "imaps" : "imap";
    }

    /** Einstellungen für eine Empfangs-Verbindung: Protokoll, Verschlüsselung nach dem Port und die Zeitgrenzen in Millisekunden. */
    public static Properties imap(int port, int verbindungMs, int lesenMs) {
        Properties props = new Properties();
        String protokoll = imapProtokoll(port);
        props.put("mail.store.protocol", protokoll);
        props.put("mail.mime.address.strict", "false");
        if (imapMitSsl(port)) {
            props.put("mail.imaps.ssl.enable", "true");
        } else {
            props.put("mail.imap.starttls.enable", "true");
            props.put("mail.imap.starttls.required", "true");
        }
        props.put("mail." + protokoll + ".connectiontimeout", String.valueOf(verbindungMs));
        props.put("mail." + protokoll + ".timeout", String.valueOf(lesenMs));
        return props;
    }

    /** Wie {@link #imap(int, int, int)} ohne eigene Zeitgrenzen (Standard der Bibliothek). */
    public static Properties imap(int port) {
        Properties props = imap(port, 0, 0);
        String protokoll = imapProtokoll(port);
        props.remove("mail." + protokoll + ".connectiontimeout");
        props.remove("mail." + protokoll + ".timeout");
        return props;
    }
}
