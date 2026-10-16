package org.example.kalkulationsprogramm.service.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MailSecretService {
    private static final Logger log = LoggerFactory.getLogger(MailSecretService.class);
    private static final String PREFIX = "v1:";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Dateiname in uploads/ – versteckter Name, kein üblicher Upload */
    private static final String SCHLUESSEL_DATEI = ".mail-encryption.key";
    private final byte[] key;

    public MailSecretService(@Value("${mail.credentials.encryption-key:}") String keyProperty,
                             @Value("${file.upload-dir:uploads}") String uploadDir) {
        this.key = ladeOderErzeugeSchluessel(keyProperty, uploadDir);
    }

    private static byte[] ladeOderErzeugeSchluessel(String keyProperty, String uploadDir) {
        // 1. Explizit gesetzter Property hat Vorrang
        if (keyProperty != null && !keyProperty.isBlank()) {
            byte[] k = keyProperty.getBytes(StandardCharsets.UTF_8);
            if (k.length == 16 || k.length == 24 || k.length == 32) return k;
            log.warn("mail.credentials.encryption-key hat ungültige Länge ({} Zeichen, erwartet 16/24/32) – wird ignoriert.", k.length);
        }
        // 2. Schlüsseldatei in uploads/ lesen oder anlegen
        Path dir = Path.of(uploadDir);
        Path datei = dir.resolve(SCHLUESSEL_DATEI);
        try {
            Files.createDirectories(dir);
            if (Files.exists(datei)) {
                byte[] k = Files.readAllBytes(datei);
                if (k.length == 32) return k;
                log.warn("Schlüsseldatei {} hat ungültige Länge – neuer Schlüssel wird erzeugt.", datei);
            }
            // Neuen 32-Byte-Schlüssel erzeugen und speichern
            byte[] neu = new byte[32];
            RANDOM.nextBytes(neu);
            Files.write(datei, neu, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            // Nur Eigentümer darf lesen/schreiben (best-effort, klappt auf Linux)
            try { datei.toFile().setReadable(false, false); datei.toFile().setReadable(true, true); datei.toFile().setWritable(false, false); datei.toFile().setWritable(true, true); } catch (Exception ignored) { }
            log.info("Neuer Mail-Verschlüsselungsschlüssel erzeugt und gespeichert: {}", datei);
            return neu;
        } catch (IOException e) {
            log.warn("Schlüsseldatei {} konnte nicht gelesen/geschrieben werden: {} – Mail-Passwörter nicht verschlüsselbar.", datei, e.getMessage());
            return new byte[0];
        }
    }

    public boolean isConfigured() {
        return key.length == 16 || key.length == 24 || key.length == 32;
    }

    public void ensureConfigured() {
        if (!isConfigured()) {
            throw new IllegalStateException("Der Schlüssel für geschützte Mailzugänge ist nicht eingerichtet.");
        }
    }

    public String encrypt(String plaintext) {
        ensureConfigured();
        if (plaintext == null || plaintext.isEmpty()) return null;
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(PREFIX.getBytes(StandardCharsets.US_ASCII));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(encrypted, 0, combined, nonce.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Mailzugang konnte nicht geschützt gespeichert werden.");
        }
    }

    public String decrypt(String cipherText) {
        ensureConfigured();
        if (cipherText == null || cipherText.isBlank()) return null;
        if (!cipherText.startsWith(PREFIX)) throw new IllegalStateException("Mailzugang ist nicht lesbar.");
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText.substring(PREFIX.length()));
            if (combined.length <= NONCE_LENGTH + 16) throw new IllegalArgumentException();
            byte[] nonce = java.util.Arrays.copyOfRange(combined, 0, NONCE_LENGTH);
            byte[] encrypted = java.util.Arrays.copyOfRange(combined, NONCE_LENGTH, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            cipher.updateAAD(PREFIX.getBytes(StandardCharsets.US_ASCII));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("Mailzugang konnte nicht entschlüsselt werden.");
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Mailzugang konnte nicht entschlüsselt werden.");
        }
    }
}
