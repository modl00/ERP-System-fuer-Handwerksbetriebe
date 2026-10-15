/**
 * Vorlagen für die häufigsten Mail-Anbieter und die Regel, wie die Verbindung verschlüsselt wird: Das ergibt sich aus dem Port, so wie die Anbieter es
 * vorgeben (Versand 465 = SSL/TLS, 587 = STARTTLS; Empfang 993 = SSL/TLS, 143 = STARTTLS; siehe {@code MailSicherheit} im Server).
 */

export interface ServerAngabe {
    host: string;
    port: number;
}

export interface Anbieter {
    id: string;
    name: string;
    smtp: ServerAngabe;
    imap: ServerAngabe;
    /** Kurzer Hinweis für den Anwender zu diesem Anbieter (zum Beispiel App-Passwort) */
    hinweis?: string;
}

/** Angaben der Anbieter können sich ändern; im Zweifel gilt die Anleitung des Anbieters. */
export const ANBIETER: Anbieter[] = [
    { id: 'tonline', name: 'T-Online', smtp: { host: 'securesmtp.t-online.de', port: 465 }, imap: { host: 'secureimap.t-online.de', port: 993 } },
    { id: 'gmx', name: 'GMX', smtp: { host: 'mail.gmx.net', port: 465 }, imap: { host: 'imap.gmx.net', port: 993 },
        hinweis: 'Bei GMX muss der Zugriff für externe Programme (POP3/IMAP) in den Einstellungen des Postfachs erlaubt sein.' },
    { id: 'webde', name: 'Web.de', smtp: { host: 'smtp.web.de', port: 587 }, imap: { host: 'imap.web.de', port: 993 },
        hinweis: 'Bei Web.de muss der Zugriff für externe Programme (POP3/IMAP) in den Einstellungen des Postfachs erlaubt sein.' },
    { id: 'gmail', name: 'Gmail', smtp: { host: 'smtp.gmail.com', port: 465 }, imap: { host: 'imap.gmail.com', port: 993 },
        hinweis: 'Gmail verlangt ein App-Passwort (Google-Konto, Sicherheit, Bestätigung in zwei Schritten), nicht das normale Passwort.' },
    { id: 'microsoft', name: 'Microsoft 365 / Outlook', smtp: { host: 'smtp.office365.com', port: 587 }, imap: { host: 'outlook.office365.com', port: 993 },
        hinweis: 'Microsoft sperrt die Anmeldung mit Passwort bei vielen Konten. Dann geht es nur mit einem App-Passwort oder dem Hinweis Ihres IT-Betreuers.' },
    { id: 'ionos', name: 'IONOS (1&1)', smtp: { host: 'smtp.ionos.de', port: 465 }, imap: { host: 'imap.ionos.de', port: 993 } },
    { id: 'strato', name: 'Strato', smtp: { host: 'smtp.strato.de', port: 465 }, imap: { host: 'imap.strato.de', port: 993 } },
];

export const EIGENER_SERVER = 'eigener';

/** Welcher Anbieter passt zu diesen Servernamen? Sonst „eigener Server". */
export function anbieterFuer(smtpHost: string, imapHost?: string): string {
    const smtp = smtpHost.trim().toLowerCase();
    const treffer = ANBIETER.find((a) => a.smtp.host === smtp && (imapHost === undefined || imapHost.trim() === '' || a.imap.host === imapHost.trim().toLowerCase()));
    return treffer ? treffer.id : EIGENER_SERVER;
}

export type Art = 'smtp' | 'imap';

export const SSL_PORT: Record<Art, number> = { smtp: 465, imap: 993 };
export const STARTTLS_PORT: Record<Art, number> = { smtp: 587, imap: 143 };

/** Wird beim Port so verschlüsselt? Versand: nur 465 mit SSL/TLS, sonst STARTTLS; Empfang: nur 143 mit STARTTLS, sonst SSL/TLS. */
export function verschluesselungFuer(art: Art, port: number): 'SSL' | 'STARTTLS' {
    if (art === 'smtp') return port === 465 ? 'SSL' : 'STARTTLS';
    return port === 143 ? 'STARTTLS' : 'SSL';
}
