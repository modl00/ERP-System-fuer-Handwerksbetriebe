package org.example.kalkulationsprogramm.service.ablage;

/**
 * Zugang zur Netzwerkfreigabe. Benutzer und Passwort dürfen leer sein (Gastzugang). {@code unterordner} ist der Ordner in der Freigabe, in dem das Programm
 * seine Struktur anlegt; leer = die Freigabe selbst.
 */
public record SmbAbgleichEinstellungen(boolean aktiv, String server, String freigabe, String unterordner, String benutzer, String passwort, String domaene) {

    public boolean vollstaendig() {
        return server != null && !server.isBlank() && freigabe != null && !freigabe.isBlank();
    }

    public SmbAbgleichEinstellungen mitPasswort(String neu) {
        return new SmbAbgleichEinstellungen(aktiv, server, freigabe, unterordner, benutzer, neu, domaene);
    }

    /** Fehlertext, wenn die Angaben unbrauchbar sind, sonst {@code null}. */
    public String fehler() {
        if (server == null || !server.trim().matches("[A-Za-z0-9._-]{1,253}")) {
            return "Bitte den Namen oder die IP-Adresse des Geräts eintragen, zum Beispiel 192.168.178.21 (ohne smb:// und ohne Schrägstriche).";
        }
        if (freigabe == null || !freigabe.trim().matches("[^\\\\/:*?\"<>|]{1,80}")) {
            return "Bitte den Namen der Freigabe eintragen, zum Beispiel volume2 (nur der Name, ohne Schrägstriche).";
        }
        if (unterordner != null && !unterordner.isBlank()) {
            String u = unterordner.trim().replace('\\', '/');
            if (u.length() > 200 || u.contains("..") || u.matches(".*[:*?\"<>|].*")) {
                return "Der Unterordner enthält ungültige Zeichen.";
            }
        }
        if (benutzer != null && benutzer.length() > 120 || domaene != null && domaene.length() > 120) {
            return "Benutzer oder Domäne sind zu lang.";
        }
        return null;
    }

    /** Der Unterordner mit Schrägstrichen, ohne Anfang und Ende. */
    public String unterordnerSauber() {
        if (unterordner == null) {
            return "";
        }
        return unterordner.trim().replace('\\', '/').replaceAll("^/+|/+$", "");
    }
}
