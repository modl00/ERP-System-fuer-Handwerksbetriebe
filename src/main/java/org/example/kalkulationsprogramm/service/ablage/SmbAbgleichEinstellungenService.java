package org.example.kalkulationsprogramm.service.ablage;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.mail.MailSecretService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Liest und speichert den Zugang zur Netzwerkfreigabe. Das Passwort wird nie zurückgegeben oder protokolliert; ist {@code mail.credentials.encryption-key} gesetzt, liegt es
 * verschlüsselt in der Datenbank, sonst im Klartext in den Einstellungen (wie die anderen Zugangsdaten, die Oberfläche sagt es).
 */
@Service
@RequiredArgsConstructor
public class SmbAbgleichEinstellungenService {

    static final String AKTIV = "ablage.smb.aktiv";
    static final String SERVER = "ablage.smb.server";
    static final String FREIGABE = "ablage.smb.freigabe";
    static final String UNTERORDNER = "ablage.smb.unterordner";
    static final String BENUTZER = "ablage.smb.benutzer";
    static final String PASSWORT = "ablage.smb.passwort";
    static final String DOMAENE = "ablage.smb.domaene";
    static final String INTERVALL = "ablage.smb.intervall-minuten";
    static final String LETZTER_LAUF = "ablage.smb.letzter-lauf";
    static final String LETZTES_ERGEBNIS = "ablage.smb.letztes-ergebnis";
    /** Erlaubte Abstände des automatischen Abgleichs in Minuten (0 = nur auf Knopfdruck). */
    public static final java.util.List<Integer> INTERVALLE = java.util.List.of(0, 5, 15, 60);

    private final SystemSettingsService einstellungen;
    private final MailSecretService geheim;

    public SmbAbgleichEinstellungen lade() {
        return new SmbAbgleichEinstellungen("true".equals(wert(AKTIV)), wert(SERVER), wert(FREIGABE), wert(UNTERORDNER), wert(BENUTZER), passwort(), wert(DOMAENE));
    }

    public boolean passwortGesetzt() {
        return !wert(PASSWORT).isEmpty();
    }

    public boolean verschluesselt() {
        return geheim.isConfigured();
    }

    /** Speichert die Angaben. Ein leeres {@code passwort} lässt das gespeicherte unverändert; {@code passwortLoeschen} entfernt es (Gastzugang). */
    public void speichere(SmbAbgleichEinstellungen neu, boolean passwortLoeschen) {
        String fehler = neu.fehler();
        if (fehler != null) {
            throw new IllegalArgumentException(fehler);
        }
        einstellungen.save(AKTIV, String.valueOf(neu.aktiv()), "Abgleich mit einer Netzwerkfreigabe (SMB) eingeschaltet");
        einstellungen.save(SERVER, neu.server().trim(), "Netzwerkfreigabe: Gerät");
        einstellungen.save(FREIGABE, neu.freigabe().trim(), "Netzwerkfreigabe: Name der Freigabe");
        einstellungen.save(UNTERORDNER, neu.unterordnerSauber(), "Netzwerkfreigabe: Unterordner");
        einstellungen.save(BENUTZER, neu.benutzer() == null ? "" : neu.benutzer().trim(), "Netzwerkfreigabe: Benutzer");
        einstellungen.save(DOMAENE, neu.domaene() == null ? "" : neu.domaene().trim(), "Netzwerkfreigabe: Domäne");
        if (passwortLoeschen) {
            einstellungen.save(PASSWORT, "", "Netzwerkfreigabe: Passwort");
        } else if (neu.passwort() != null && !neu.passwort().isEmpty()) {
            einstellungen.save(PASSWORT, geheim.isConfigured() ? geheim.encrypt(neu.passwort()) : neu.passwort(), "Netzwerkfreigabe: Passwort");
        }
    }

    public int intervallMinuten() {
        try {
            int m = Integer.parseInt(wert(INTERVALL));
            return INTERVALLE.contains(m) ? m : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void speichereIntervall(int minuten) {
        if (!INTERVALLE.contains(minuten)) {
            throw new IllegalArgumentException("Der Abstand muss 0 (nur auf Knopfdruck), 5, 15 oder 60 Minuten sein.");
        }
        einstellungen.save(INTERVALL, String.valueOf(minuten), "Netzwerkfreigabe: automatischer Abgleich alle n Minuten (0 = aus)");
    }

    /** Wann zuletzt abgeglichen wurde (Zeitpunkt des Versuchs, auch bei einem Fehler), oder {@code null}. */
    public java.time.LocalDateTime letzterLauf() {
        try {
            return java.time.LocalDateTime.parse(wert(LETZTER_LAUF));
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    public String letztesErgebnis() {
        return wert(LETZTES_ERGEBNIS);
    }

    /** Merkt sich Zeitpunkt und Kurzergebnis des letzten Abgleichs (nur Zahlen oder der Fehlertext, keine Dateinamen). */
    // Eigene Transaktion: auch ein fehlgeschlagener Abgleich (der seine Transaktion zurückrollt) soll sich den Fehler merken
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void merkeLauf(java.time.LocalDateTime zeitpunkt, String ergebnis) {
        einstellungen.save(LETZTER_LAUF, zeitpunkt.toString(), "Netzwerkfreigabe: Zeitpunkt des letzten Abgleichs");
        einstellungen.save(LETZTES_ERGEBNIS, ergebnis == null ? "" : (ergebnis.length() > 300 ? ergebnis.substring(0, 300) : ergebnis), "Netzwerkfreigabe: Ergebnis des letzten Abgleichs");
    }

    private String passwort() {
        String roh = wert(PASSWORT);
        return roh.startsWith("v1:") ? geheim.decrypt(roh) : roh;
    }

    private String wert(String schluessel) {
        String w = einstellungen.get(schluessel, "");
        return w == null ? "" : w.strip();
    }
}
