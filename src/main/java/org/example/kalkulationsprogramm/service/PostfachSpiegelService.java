package org.example.kalkulationsprogramm.service;

import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.example.email.MailSicherheit;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sun.mail.imap.IMAPFolder;

import jakarta.annotation.PreDestroy;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import lombok.RequiredArgsConstructor;

/**
 * Führt Änderungen aus dem E-Mail-Center im Postfach beim Provider nach (Option, standardmäßig <b>aus</b>, weil sie das Postfach verändert):
 * <ul>
 *   <li><b>Löschen</b> im Programm → die Mail wird im Postfach in den <b>Papierkorb</b> verschoben (nicht endgültig gelöscht),</li>
 *   <li><b>Zuordnen</b> zu Projekt oder Anfrage → die Mail bekommt im Postfach eine <b>Markierung</b> (Schlagwort), sie bleibt am Ort.</li>
 * </ul>
 * Das geschieht erst nach dem Speichern im Programm und im Hintergrund; ein Fehler beim Provider (nicht erreichbar, Mail dort schon weg, Server ohne Schlagwörter) ändert nichts
 * an der Aktion im Programm. Betroffen ist das Haupt-Postfach und nur Mails mit bekanntem Ordner und bekannter Nummer auf dem Server.
 */
@Service
@RequiredArgsConstructor
public class PostfachSpiegelService {

    private static final Logger log = LoggerFactory.getLogger(PostfachSpiegelService.class);
    public static final String AN = "email.spiegeln.aktiv";
    private static final Set<String> PAPIERKORB_NAMEN = Set.of("trash", "papierkorb", "gelöscht", "gelöschte elemente", "gelöschte objekte", "deleted items", "deleted messages", "bin");

    private final SystemSettingsService einstellungen;
    private final EmailRepository emails;
    private final PlatformTransactionManager transaktionen;
    private final ExecutorService hintergrund = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "postfach-spiegeln");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    void beenden() {
        hintergrund.shutdown();
    }

    public boolean istAktiv() {
        return "true".equalsIgnoreCase(einstellungen.get(AN, "false"));
    }

    public boolean setzeAktiv(boolean aktiv) {
        einstellungen.save(AN, String.valueOf(aktiv), "Änderungen aus dem E-Mail-Center im Postfach nachführen (Papierkorb, Markierung)");
        return aktiv;
    }

    /** Die Mail wurde im Programm gelöscht (Papierkorb): im Postfach ebenfalls in den Papierkorb verschieben. */
    public void loeschenVormerken(Email mail) {
        if (!bereit(mail)) {
            return;
        }
        Long id = mail.getId();
        String ordner = mail.getImapFolder();
        long uid = mail.getImapUid();
        planen(() -> verschiebeInPapierkorb(id, ordner, uid));
    }

    /** Die Mail wurde einem Projekt oder einer Anfrage zugeordnet: im Postfach mit einem Schlagwort markieren. */
    public void markierenVormerken(Email mail, String bezeichnung) {
        if (!bereit(mail) || bezeichnung == null || bezeichnung.isBlank()) {
            return;
        }
        String ordner = mail.getImapFolder();
        long uid = mail.getImapUid();
        String schlagwort = schlagwort(bezeichnung);
        planen(() -> setzeSchlagwort(ordner, uid, schlagwort));
    }

    private boolean bereit(Email mail) {
        return mail != null && mail.getId() != null && mail.getImapFolder() != null && !mail.getImapFolder().isBlank() && mail.getImapUid() != null && istAktiv();
    }

    /** Schlagwörter (IMAP-Flags) dürfen nur Buchstaben, Ziffern, Unter- und Bindestrich enthalten. */
    static String schlagwort(String bezeichnung) {
        String s = bezeichnung.strip().replaceAll("[^A-Za-z0-9_-]", "_");
        if (s.length() > 40) {
            s = s.substring(0, 40);
        }
        return "ERP_" + s;
    }

    static boolean istPapierkorbName(String vollerName) {
        if (vollerName == null) {
            return false;
        }
        int i = Math.max(vollerName.lastIndexOf('.'), Math.max(vollerName.lastIndexOf('/'), vollerName.lastIndexOf('\\')));
        return PAPIERKORB_NAMEN.contains(vollerName.substring(i + 1).trim().toLowerCase(Locale.ROOT));
    }

    private void planen(Runnable aufgabe) {
        Runnable sicher = () -> {
            try {
                aufgabe.run();
            } catch (RuntimeException e) {
                log.warn("[Postfach-Spiegeln] fehlgeschlagen: {}", e.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    hintergrund.submit(sicher);
                }
            });
        } else {
            hintergrund.submit(sicher);
        }
    }

    private Store verbinde() throws MessagingException {
        var zugang = einstellungen.getStandardImapZugang();
        if (zugang.username() == null || zugang.username().isBlank() || zugang.password() == null || zugang.password().isBlank()) {
            throw new MessagingException("Postfach nicht eingerichtet");
        }
        Properties props = MailSicherheit.imap(zugang.port(), 10000, 20000);
        Store store = Session.getInstance(props).getStore(MailSicherheit.imapProtokoll(zugang.port()));
        store.connect(zugang.host(), zugang.port(), zugang.username(), zugang.password());
        return store;
    }

    void verschiebeInPapierkorb(Long emailId, String ordner, long uid) {
        try (Store store = verbinde()) {
            Folder quelle = store.getFolder(ordner);
            if (!(quelle instanceof IMAPFolder von) || !von.exists()) {
                return;
            }
            if (istPapierkorbName(ordner)) {
                return; // liegt schon im Papierkorb
            }
            IMAPFolder papierkorb = findePapierkorb(store);
            if (papierkorb == null) {
                log.info("[Postfach-Spiegeln] Kein Papierkorb-Ordner im Postfach gefunden; E-Mail {} bleibt dort liegen", emailId);
                return;
            }
            von.open(Folder.READ_WRITE);
            try {
                Message m = von.getMessageByUID(uid);
                if (m == null) {
                    return; // dort schon nicht mehr vorhanden
                }
                von.moveMessages(new Message[] { m }, papierkorb);
            } finally {
                try {
                    von.close(true);
                } catch (MessagingException ignored) {
                    // schließen ist Nebensache
                }
            }
            // Die Mail liegt nun im Papierkorb: Ordner merken, die alte Nummer gilt nicht mehr
            String ziel = papierkorb.getFullName();
            new TransactionTemplate(transaktionen).executeWithoutResult(s -> emails.findById(emailId).ifPresent(e -> {
                e.setImapFolder(ziel);
                e.setImapUid(null);
                emails.save(e);
            }));
            log.info("[Postfach-Spiegeln] E-Mail {} im Postfach in den Papierkorb verschoben", emailId);
        } catch (MessagingException e) {
            log.warn("[Postfach-Spiegeln] Papierkorb-Verschiebung fehlgeschlagen: {}", e.getClass().getSimpleName());
        }
    }

    void setzeSchlagwort(String ordner, long uid, String schlagwort) {
        try (Store store = verbinde()) {
            Folder f = store.getFolder(ordner);
            if (!(f instanceof IMAPFolder imap) || !imap.exists()) {
                return;
            }
            imap.open(Folder.READ_WRITE);
            try {
                Flags erlaubt = imap.getPermanentFlags();
                if (erlaubt == null || !erlaubt.contains(Flags.Flag.USER)) {
                    log.info("[Postfach-Spiegeln] Der Server erlaubt keine eigenen Schlagwörter; die Mail wird nicht markiert");
                    return;
                }
                Message m = imap.getMessageByUID(uid);
                if (m != null) {
                    m.setFlags(new Flags(schlagwort), true);
                }
            } finally {
                try {
                    imap.close(false);
                } catch (MessagingException ignored) {
                    // schließen ist Nebensache
                }
            }
        } catch (MessagingException e) {
            log.warn("[Postfach-Spiegeln] Markierung fehlgeschlagen: {}", e.getClass().getSimpleName());
        }
    }

    private static IMAPFolder findePapierkorb(Store store) throws MessagingException {
        IMAPFolder nachName = null;
        for (Folder f : store.getDefaultFolder().list("*")) {
            if (f instanceof IMAPFolder imap) {
                for (String a : imap.getAttributes()) {
                    if ("\\Trash".equalsIgnoreCase(a)) {
                        return imap;
                    }
                }
                if (nachName == null && istPapierkorbName(imap.getFullName())) {
                    nachName = imap;
                }
            }
        }
        return nachName;
    }
}
