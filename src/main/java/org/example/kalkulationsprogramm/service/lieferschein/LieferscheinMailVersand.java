package org.example.kalkulationsprogramm.service.lieferschein;

import java.util.List;
import java.util.Map;

import org.example.email.EmailService;
import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.example.kalkulationsprogramm.service.mail.SentMailArchiver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Verschickt die Kopie eines Lieferscheins über das Dokument-Postfach (wie Rechnungen und Angebote). */
@Slf4j
@Service
@RequiredArgsConstructor
public class LieferscheinMailVersand {

    private final SystemSettingsService systemSettingsService;
    private final SentMailArchiver sentMailArchiver;

    public void sende(String empfaenger, String betreff, String htmlBody, byte[] pdf, String dateiname) {
        SystemSettingsService.MailKonto konto = systemSettingsService.getDokumentMailKonto();
        if (konto == null || konto.host() == null || konto.host().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Das E-Mail-Postfach ist noch nicht eingerichtet (Einstellungen → E-Mail).");
        }
        try {
            EmailService emailService = new EmailService(konto.host(), konto.port(), konto.username(), konto.password())
                    .mitAbsenderName(konto.fromName())
                    .mitSentKopie(sentMailArchiver.fuerDokumentKonto());
            emailService.sendEmailWithMultipleAttachments(empfaenger, null, konto.fromAddress(), betreff, htmlBody, Map.of(),
                    List.of(new EmailService.Attachment(pdf, dateiname, "application/pdf")));
        } catch (Exception e) {
            log.warn("Lieferschein-Kopie konnte nicht gesendet werden: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Die E-Mail konnte nicht gesendet werden. Bitte Verbindung und Postfach prüfen.");
        }
    }
}
