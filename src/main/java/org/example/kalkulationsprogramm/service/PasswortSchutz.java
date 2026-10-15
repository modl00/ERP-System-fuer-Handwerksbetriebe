package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Verschlüsselt fremde Zugangsdaten (Passwörter persönlicher Postfächer) für die Datenbank: AES-256-GCM mit zufälligem Schlüssel, der einmal erzeugt und in einer Datei im Datenordner
 * abgelegt wird, <b>nicht</b> in der Datenbank. Wer nur die Datenbanksicherung hat, kommt so nicht an die Passwörter. Die Schlüsseldatei gehört in dieselbe Sicherung wie der Datenordner;
 * geht sie verloren, müssen die Passwörter neu eingegeben werden.
 */
@Service
public class PasswortSchutz {

    private static final String PRAEFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecureRandom zufall = new SecureRandom();
    private final Path schluesselDatei;
    private volatile SecretKeySpec schluessel;

    @org.springframework.beans.factory.annotation.Autowired
    public PasswortSchutz(@Value("${file.upload-dir:uploads}") String datenordner) {
        this.schluesselDatei = Path.of(datenordner).resolve(".postfach-schluessel");
    }

    /** Für Tests: eigener Schlüssel statt Datei. */
    PasswortSchutz(byte[] schluessel) {
        this.schluesselDatei = null;
        this.schluessel = new SecretKeySpec(schluessel, "AES");
    }

    public String verschluessele(String klartext) {
        if (klartext == null) {
            throw new IllegalArgumentException("Kein Passwort angegeben.");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            zufall.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(klartext.getBytes(StandardCharsets.UTF_8));
            byte[] alles = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, alles, 0, iv.length);
            System.arraycopy(ct, 0, alles, iv.length, ct.length);
            return PRAEFIX + Base64.getEncoder().encodeToString(alles);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Das Passwort konnte nicht verschlüsselt werden.");
        }
    }

    /** @throws IllegalStateException wenn der Text nicht von diesem Schlüssel stammt (zum Beispiel Schlüsseldatei fehlt) */
    public String entschluessele(String text) {
        if (text == null || !text.startsWith(PRAEFIX)) {
            throw new IllegalStateException("Das gespeicherte Passwort ist nicht lesbar. Bitte das Passwort neu eingeben.");
        }
        try {
            byte[] alles = Base64.getDecoder().decode(text.substring(PRAEFIX.length()));
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, alles, 0, IV_BYTES));
            return new String(c.doFinal(alles, IV_BYTES, alles.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Das gespeicherte Passwort ist nicht lesbar. Bitte das Passwort neu eingeben.");
        }
    }

    private SecretKeySpec key() {
        SecretKeySpec k = schluessel;
        if (k == null) {
            synchronized (this) {
                if (schluessel == null) {
                    schluessel = ladeOderErzeuge();
                }
                k = schluessel;
            }
        }
        return k;
    }

    private SecretKeySpec ladeOderErzeuge() {
        try {
            if (Files.exists(schluesselDatei)) {
                byte[] roh = Base64.getDecoder().decode(Files.readString(schluesselDatei).trim());
                if (roh.length != 32) {
                    throw new IllegalStateException("Die Schlüsseldatei für Postfach-Passwörter ist beschädigt.");
                }
                return new SecretKeySpec(roh, "AES");
            }
            byte[] neu = new byte[32];
            zufall.nextBytes(neu);
            Files.createDirectories(schluesselDatei.getParent());
            Files.writeString(schluesselDatei, Base64.getEncoder().encodeToString(neu), StandardOpenOption.CREATE_NEW);
            try {
                Files.setPosixFilePermissions(schluesselDatei, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Windows: keine POSIX-Rechte
            }
            return new SecretKeySpec(neu, "AES");
        } catch (IOException e) {
            throw new IllegalStateException("Die Schlüsseldatei für Postfach-Passwörter ist nicht les- oder schreibbar.");
        }
    }
}
