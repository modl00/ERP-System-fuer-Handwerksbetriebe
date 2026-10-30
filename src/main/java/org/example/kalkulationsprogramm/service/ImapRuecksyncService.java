package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.service.mail.PostfachZugang;
import org.example.kalkulationsprogramm.service.mail.PostfachZugangService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sun.mail.imap.IMAPFolder;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.ReceivedDateTerm;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gleicht Verschiebungen auf dem IMAP-Server (Papierkorb, Spam) zurück ins ERP.
 *
 * Läuft alle 5 Minuten. Öffnet die konfigurierten Papierkorb- und Spam-Ordner,
 * liest dort die Message-IDs der letzten 90 Tage aus und markiert übereinstimmende
 * E-Mails in der lokalen DB entsprechend – falls das noch nicht geschehen ist.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImapRuecksyncService {

    private final PostfachZugangService postfachZugangService;
    private final ImapSyncService imapSyncService;
    private final EmailRepository emailRepository;
    private final SpamBayesService spamBayesService;

    @Value("${email.features.enabled:true}")
    private boolean emailFeaturesEnabled;

    /** Wie viele Tage rückwirkend Papierkorb und Spam gescannt werden. */
    private static final int SCAN_TAGE = 90;

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void syncServerAenderungen() {
        if (!emailFeaturesEnabled) {
            return;
        }
        for (PostfachZugang zugang : postfachZugangService.abrufbarePostfaecher()) {
            syncPostfach(zugang);
        }
    }

    private void syncPostfach(PostfachZugang zugang) {
        SystemSettingsService.ImapZugang imap = zugang.imapZugang();
        ImapSyncService.OrdnerEinstellungenErgebnis ordner =
                imapSyncService.getOrdnerEinstellungen(zugang.postfachId());

        if (ordner.papierkorb().isBlank() && ordner.spam().isBlank()) {
            return;
        }

        Date seit = java.util.Date.from(
                LocalDateTime.now().minusDays(SCAN_TAGE)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant());

        try {
            Session session = Session.getInstance(imapProperties());
            try (Store store = session.getStore("imaps")) {
                store.connect(imap.host(), imap.port(), imap.username(), imap.password());

                if (!ordner.papierkorb().isBlank()) {
                    Set<String> ids = messageIdsAusOrdner(store, ordner.papierkorb(), seit);
                    if (!ids.isEmpty()) {
                        aktualisiereAlsPapierkorb(ids);
                    }
                }

                if (!ordner.spam().isBlank()) {
                    Set<String> ids = messageIdsAusOrdner(store, ordner.spam(), seit);
                    if (!ids.isEmpty()) {
                        aktualisiereAlsSpam(ids);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ImapRuecksync] Fehler bei Postfach {}: {}", zugang.emailAdresse(), e.getMessage());
        }
    }

    @Transactional
    public void aktualisiereAlsPapierkorb(Set<String> messageIds) {
        List<Email> treffer = emailRepository.findByMessageIdIn(messageIds);
        for (Email email : treffer) {
            if (email.getDeletedAt() != null) {
                continue;
            }
            email.setDeletedAt(LocalDateTime.now());
            emailRepository.save(email);
            log.info("[ImapRuecksync] E-Mail {} (Message-ID {}) serverseitig in Papierkorb – lokal nachgezogen",
                    email.getId(), email.getMessageId());
        }
    }

    @Transactional
    public void aktualisiereAlsSpam(Set<String> messageIds) {
        List<Email> treffer = emailRepository.findByMessageIdIn(messageIds);
        for (Email email : treffer) {
            if (email.isSpam()) {
                continue;
            }
            email.setSpam(true);
            email.setSpamScore(100);
            emailRepository.save(email);
            if (spamBayesService.isModelReady()) {
                spamBayesService.train(email, true);
            }
            log.info("[ImapRuecksync] E-Mail {} (Message-ID {}) serverseitig in Spam – lokal nachgezogen",
                    email.getId(), email.getMessageId());
        }
    }

    private Set<String> messageIdsAusOrdner(Store store, String ordnerName, Date seit) {
        Set<String> ids = new HashSet<>();
        Folder genericFolder;
        try {
            genericFolder = store.getFolder(ordnerName);
        } catch (MessagingException e) {
            log.debug("[ImapRuecksync] Ordner '{}' nicht gefunden: {}", ordnerName, e.getMessage());
            return ids;
        }
        if (!(genericFolder instanceof IMAPFolder folder) || !folder.exists()) {
            return ids;
        }
        try {
            folder.open(Folder.READ_ONLY);
            Message[] nachrichten = folder.search(new ReceivedDateTerm(ComparisonTerm.GT, seit));
            for (Message msg : nachrichten) {
                try {
                    String[] headerWerte = msg.getHeader("Message-ID");
                    if (headerWerte != null && headerWerte.length > 0 && headerWerte[0] != null) {
                        ids.add(headerWerte[0].trim());
                    }
                } catch (MessagingException e) {
                    // einzelne Nachricht überspringen
                }
            }
        } catch (MessagingException e) {
            log.warn("[ImapRuecksync] Fehler beim Lesen von Ordner '{}': {}", ordnerName, e.getMessage());
        } finally {
            try {
                if (folder.isOpen()) {
                    folder.close(false);
                }
            } catch (MessagingException ignored) { }
        }
        return ids;
    }

    private static Properties imapProperties() {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "15000");
        return props;
    }
}
