package org.example.kalkulationsprogramm.service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Base64;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Schlüsselpaar (VAPID) für Push-Meldungen aufs Handy. Der Betrieb muss nichts einrichten: Beim ersten Bedarf
 * erzeugt das Programm das Paar selbst und legt es in den Einstellungen ab (#89). Umgebungsvariablen
 * {@code PUSH_VAPID_PUBLIC_KEY}/{@code PUSH_VAPID_PRIVATE_KEY} gehen vor (siehe {@link WebPushService}).
 *
 * <p>Der private Schlüssel gehört nur auf den Server und wird nie ausgeliefert. Wer ihn tauscht (Einträge
 * löschen), macht alle angemeldeten Geräte ungültig; sie melden sich beim nächsten Öffnen der App neu an.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VapidSchluesselService {

    static final String OEFFENTLICH = "push.vapid.public-key";
    static final String PRIVAT = "push.vapid.private-key";

    /** Beide Schlüssel Base64-URL-kodiert (öffentlich als X.509, privat als PKCS#8), wie {@link WebPushService} sie liest. */
    public record Schluessel(String oeffentlich, String privat) { }

    private final SystemSettingsService einstellungen;

    /** Der gespeicherte Schlüssel; gibt es noch keinen (oder nur die Hälfte), wird ein neuer erzeugt und gespeichert. */
    @Transactional
    public synchronized Schluessel ladeOderErzeuge() {
        String oeff = text(einstellungen.get(OEFFENTLICH, ""));
        String priv = text(einstellungen.get(PRIVAT, ""));
        if (!oeff.isEmpty() && !priv.isEmpty()) {
            return new Schluessel(oeff, priv);
        }
        Schluessel neu = erzeuge();
        einstellungen.save(OEFFENTLICH, neu.oeffentlich(), "Öffentlicher Schlüssel für Push-Meldungen (automatisch erzeugt)");
        einstellungen.save(PRIVAT, neu.privat(), "Privater Schlüssel für Push-Meldungen (automatisch erzeugt, geheim)");
        log.info("Push-Schlüssel automatisch erzeugt und gespeichert");
        return neu;
    }

    static Schluessel erzeuge() {
        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            ECNamedCurveParameterSpec kurve = ECNamedCurveTable.getParameterSpec("prime256v1");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("ECDSA", "BC");
            generator.initialize(kurve);
            KeyPair paar = generator.generateKeyPair();
            Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
            return new Schluessel(base64.encodeToString(paar.getPublic().getEncoded()),
                    base64.encodeToString(paar.getPrivate().getEncoded()));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("Das Push-Schlüsselpaar konnte nicht erzeugt werden.", e);
        }
    }

    private static String text(String wert) {
        return wert == null ? "" : wert.strip();
    }
}
