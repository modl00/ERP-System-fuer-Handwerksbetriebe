package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailAbsender;
import org.example.kalkulationsprogramm.domain.EmailPostfachZuordnung;
import org.example.kalkulationsprogramm.repository.EmailAbsenderRepository;
import org.example.kalkulationsprogramm.repository.EmailPostfachZuordnungRepository;
import org.example.kalkulationsprogramm.service.mail.PostfachZugang;
import org.example.kalkulationsprogramm.service.mail.PostfachZugangService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sun.mail.imap.IMAPFolder;

import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;

/**
 * Synchronisiert Aktionen des Nutzers (Papierkorb, Spam) mit dem IMAP-Server:
 * verschiebt Nachrichten in den konfigurierten Papierkorb- bzw. Spam-Ordner,
 * statt sie nur lokal zu markieren.
 */
@Service
public class ImapSyncService {

    private static final Logger log = LoggerFactory.getLogger(ImapSyncService.class);

    /** system_setting-Schlüssel für den konfigurierten Papierkorb-Ordner. */
    public static final String KEY_TRASH = "email.folder.trash";
    /** system_setting-Schlüssel für den konfigurierten Spam/Junk-Ordner. */
    public static final String KEY_SPAM = "email.folder.spam";

    private final SystemSettingsService systemSettingsService;
    private final PostfachZugangService postfachZugangService;
    private final EmailPostfachZuordnungRepository emailPostfachZuordnungRepository;
    private final EmailAbsenderRepository emailAbsenderRepository;

    public ImapSyncService(SystemSettingsService systemSettingsService,
            PostfachZugangService postfachZugangService,
            EmailPostfachZuordnungRepository emailPostfachZuordnungRepository,
            EmailAbsenderRepository emailAbsenderRepository) {
        this.systemSettingsService = systemSettingsService;
        this.postfachZugangService = postfachZugangService;
        this.emailPostfachZuordnungRepository = emailPostfachZuordnungRepository;
        this.emailAbsenderRepository = emailAbsenderRepository;
    }

    /** Verschiebt die E-Mail serverseitig in den konfigurierten Papierkorb-Ordner. */
    public void verschiebeInPapierkorb(Email email) {
        verschiebeAufServer(email, KEY_TRASH, EmailAbsender::getImapOrdnerPapierkorb);
    }

    /** Verschiebt die E-Mail serverseitig in den konfigurierten Spam/Junk-Ordner. */
    public void verschiebeInSpam(Email email) {
        verschiebeAufServer(email, KEY_SPAM, EmailAbsender::getImapOrdnerSpam);
    }

    /**
     * Gibt alle IMAP-Ordner des angegebenen Postfachs zurück (vollständige Namen,
     * z. B. "INBOX", "INBOX.Spam", "Trash"). {@code postfachId == null} → Hauptpostfach.
     */
    public List<String> listeImapOrdner(Long postfachId) {
        SystemSettingsService.ImapZugang zugang = imapZugangFuer(postfachId);
        if (!hatZugang(zugang)) {
            return List.of();
        }
        return listeOrdnerVonZugang(zugang);
    }

    /**
     * Erkennt Papierkorb- und Spam-Ordner automatisch anhand der IMAP-Special-Use-
     * Attribute (\Trash, \Junk). {@code postfachId == null} → Hauptpostfach.
     */
    public SpezialOrdnerErgebnis erkenneSpezialOrdner(Long postfachId) {
        SystemSettingsService.ImapZugang zugang = imapZugangFuer(postfachId);
        if (!hatZugang(zugang)) {
            return new SpezialOrdnerErgebnis("", "");
        }
        return erkenneSpezialOrdnerVonZugang(zugang);
    }

    /** Liefert die gespeicherten Ordner-Einstellungen für ein Postfach oder das globale Fallback. */
    public OrdnerEinstellungenErgebnis getOrdnerEinstellungen(Long postfachId) {
        if (postfachId != null) {
            return emailAbsenderRepository.findById(postfachId)
                    .map(p -> new OrdnerEinstellungenErgebnis(
                            nullToLeer(p.getImapOrdnerPapierkorb()),
                            nullToLeer(p.getImapOrdnerSpam())))
                    .orElse(new OrdnerEinstellungenErgebnis("", ""));
        }
        return new OrdnerEinstellungenErgebnis(
                systemSettingsService.get(KEY_TRASH, ""),
                systemSettingsService.get(KEY_SPAM, ""));
    }

    /** Speichert Ordner-Einstellungen für ein Postfach oder global. */
    public void saveOrdnerEinstellungen(Long postfachId, String papierkorb, String spam) {
        if (postfachId != null) {
            emailAbsenderRepository.findById(postfachId).ifPresent(p -> {
                p.setImapOrdnerPapierkorb(papierkorb.isBlank() ? null : papierkorb.trim());
                p.setImapOrdnerSpam(spam.isBlank() ? null : spam.trim());
                emailAbsenderRepository.save(p);
            });
        } else {
            systemSettingsService.save(KEY_TRASH, papierkorb.trim());
            systemSettingsService.save(KEY_SPAM, spam.trim());
        }
    }

    public record SpezialOrdnerErgebnis(String papierkorb, String spam) {}
    public record OrdnerEinstellungenErgebnis(String papierkorb, String spam) {}

    // ─── private helpers ───────────────────────────────────────────────────

    @FunctionalInterface
    private interface OrdnerGetter {
        String get(EmailAbsender postfach);
    }

    private void verschiebeAufServer(Email email, String globalKey, OrdnerGetter postfachOrdner) {
        if (email.getMessageId() == null) {
            log.debug("[ImapSync] Email {} hat keine Message-ID – überspringe", email.getId());
            return;
        }

        List<EmailPostfachZuordnung> zuordnungen = email.getId() == null
                ? List.of()
                : emailPostfachZuordnungRepository.findByEmailId(email.getId());

        if (zuordnungen.isEmpty()) {
            String ziel = systemSettingsService.get(globalKey, "");
            if (ziel.isBlank()) {
                log.debug("[ImapSync] Kein Ordner konfiguriert – überspringe Server-Verschiebung für Email {}", email.getId());
                return;
            }
            verschiebeInZugang(email, systemSettingsService.getStandardImapZugang(),
                    email.getImapFolder(), email.getImapUid(), ziel);
            return;
        }

        for (var zuordnung : zuordnungen) {
            PostfachZugang pz = postfachZugangService.zugangVon(zuordnung.getPostfach());
            if (!pz.hatAbrufZugang()) continue;
            String ziel = postfachOrdner.get(zuordnung.getPostfach());
            if (ziel == null || ziel.isBlank()) {
                ziel = systemSettingsService.get(globalKey, "");
            }
            if (ziel.isBlank()) {
                log.debug("[ImapSync] Kein Ordner für Postfach {} konfiguriert – überspringe Email {}",
                        zuordnung.getPostfach().getId(), email.getId());
                continue;
            }
            String quellOrdner = zuordnung.getImapOrdner() != null ? zuordnung.getImapOrdner() : email.getImapFolder();
            Long uid = zuordnung.getImapUid() != null ? zuordnung.getImapUid() : email.getImapUid();
            verschiebeInZugang(email, pz.imapZugang(), quellOrdner, uid, ziel);
        }
    }

    private SystemSettingsService.ImapZugang imapZugangFuer(Long postfachId) {
        if (postfachId == null) {
            return systemSettingsService.getStandardImapZugang();
        }
        return emailAbsenderRepository.findById(postfachId)
                .map(postfachZugangService::zugangVon)
                .filter(PostfachZugang::hatAbrufZugang)
                .map(PostfachZugang::imapZugang)
                .orElse(null);
    }

    private static String nullToLeer(String s) {
        return s == null ? "" : s;
    }

    private void verschiebeInZugang(Email email, SystemSettingsService.ImapZugang zugang,
            String quellOrdner, Long uid, String zielOrdner) {
        if (!hatZugang(zugang)) return;
        if (quellOrdner == null || uid == null) {
            log.debug("[ImapSync] Quell-Ordner oder UID unbekannt für Email {} – überspringe", email.getId());
            return;
        }
        if (quellOrdner.equalsIgnoreCase(zielOrdner)) {
            log.debug("[ImapSync] Email {} liegt bereits in Zielordner '{}' – überspringe", email.getId(), zielOrdner);
            return;
        }

        try {
            Session session = Session.getInstance(imapProperties());
            try (Store store = session.getStore("imaps")) {
                store.connect(zugang.host(), zugang.port(), zugang.username(), zugang.password());

                Folder quell = store.getFolder(quellOrdner);
                if (!quell.exists()) {
                    log.warn("[ImapSync] Quell-Ordner '{}' existiert nicht – überspringe Email {}", quellOrdner, email.getId());
                    return;
                }
                quell.open(Folder.READ_WRITE);

                Folder ziel = store.getFolder(zielOrdner);
                if (!ziel.exists()) {
                    log.warn("[ImapSync] Ziel-Ordner '{}' existiert nicht – überspringe Email {}", zielOrdner, email.getId());
                    quell.close(false);
                    return;
                }

                if (quell instanceof IMAPFolder imapQuell) {
                    var msg = imapQuell.getMessageByUID(uid);
                    if (msg == null) {
                        log.debug("[ImapSync] Nachricht mit UID {} nicht in Ordner '{}' – überspringe", uid, quellOrdner);
                        quell.close(false);
                        return;
                    }
                    // Message-ID prüfen, um nach UID-Recycling keine falsche Mail zu verschieben
                    String[] ids = msg.getHeader("Message-ID");
                    String msgId = (ids != null && ids.length > 0) ? ids[0] : null;
                    if (msgId != null && !msgId.equals(email.getMessageId())) {
                        log.warn("[ImapSync] Message-ID-Mismatch für UID {} – überspringe", uid);
                        quell.close(false);
                        return;
                    }

                    quell.copyMessages(new jakarta.mail.Message[]{msg}, ziel);
                    msg.setFlag(Flags.Flag.DELETED, true);
                    quell.close(true); // expunge
                    log.info("[ImapSync] Email {} (UID {}) von '{}' nach '{}' verschoben",
                            email.getId(), uid, quellOrdner, zielOrdner);
                } else {
                    quell.close(false);
                    log.debug("[ImapSync] Ordner '{}' ist kein IMAPFolder – überspringe", quellOrdner);
                }
            }
        } catch (Exception e) {
            log.warn("[ImapSync] Fehler beim Verschieben von Email {}: {}", email.getId(), e.getMessage());
        }
    }

    private List<String> listeOrdnerVonZugang(SystemSettingsService.ImapZugang zugang) {
        List<String> result = new ArrayList<>();
        try {
            Session session = Session.getInstance(imapProperties());
            try (Store store = session.getStore("imaps")) {
                store.connect(zugang.host(), zugang.port(), zugang.username(), zugang.password());
                Folder[] folders = store.getDefaultFolder().list("*");
                for (Folder f : folders) {
                    // Nur echte Nachrichten-Ordner (kein reiner Container)
                    if ((f.getType() & Folder.HOLDS_MESSAGES) != 0) {
                        result.add(f.getFullName());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ImapSync] Fehler beim Auflisten der IMAP-Ordner: {}", e.getMessage());
        }
        return result;
    }

    private SpezialOrdnerErgebnis erkenneSpezialOrdnerVonZugang(SystemSettingsService.ImapZugang zugang) {
        String papierkorb = "";
        String spam = "";
        try {
            Session session = Session.getInstance(imapProperties());
            try (Store store = session.getStore("imaps")) {
                store.connect(zugang.host(), zugang.port(), zugang.username(), zugang.password());
                Folder[] folders = store.getDefaultFolder().list("*");
                for (Folder f : folders) {
                    if (f instanceof IMAPFolder imapF) {
                        try {
                            String[] attrs = imapF.getAttributes();
                            for (String attr : attrs) {
                                if ("\\Trash".equalsIgnoreCase(attr) && papierkorb.isEmpty()) {
                                    papierkorb = f.getFullName();
                                }
                                if (("\\Junk".equalsIgnoreCase(attr) || "\\Spam".equalsIgnoreCase(attr))
                                        && spam.isEmpty()) {
                                    spam = f.getFullName();
                                }
                            }
                        } catch (MessagingException ignored) {
                            // getAttributes() kann für einzelne Ordner fehlschlagen
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ImapSync] Fehler beim Erkennen der Spezialordner: {}", e.getMessage());
        }
        return new SpezialOrdnerErgebnis(papierkorb, spam);
    }

    private static Properties imapProperties() {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");
        return props;
    }

    private static boolean hatZugang(SystemSettingsService.ImapZugang z) {
        return z != null && z.host() != null && !z.host().isBlank()
                && z.username() != null && !z.username().isBlank()
                && z.password() != null && !z.password().isBlank();
    }
}
