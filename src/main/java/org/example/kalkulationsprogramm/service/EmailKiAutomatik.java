package org.example.kalkulationsprogramm.service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Vollautomatische Zuordnung neuer E-Mails (Option, standardmäßig <b>aus</b>). Ist sie eingeschaltet, ordnet das Programm alle fünf Minuten bis zu 20 noch nicht zugeordnete,
 * eingehende E-Mails zu, die <b>nach dem Einschalten</b> angekommen sind (Lieferant, Kunde, Schlagwörter, KI wie im E-Mail-Center). Ältere Mails werden nicht angefasst.
 * Jede Mail wird je Programmstart höchstens einmal versucht, damit die KI nicht ständig dasselbe fragt.
 */
@Service
@RequiredArgsConstructor
public class EmailKiAutomatik {

    private static final Logger log = LoggerFactory.getLogger(EmailKiAutomatik.class);
    static final String AN = "email.ki.vollautomatisch";
    static final String SEIT = "email.ki.vollautomatisch.seit";
    private static final int PRO_LAUF = 20;
    private static final int MERKE_MAX = 5_000;

    private final SystemSettingsService einstellungen;
    private final EmailRepository emails;
    private final EmailAutoAssignmentService zuordnung;

    private final Set<Long> versucht = new LinkedHashSet<>();

    public record Status(boolean vollautomatisch, String seit) { }

    public Status status() {
        return new Status(istAn(), einstellungen.get(SEIT, ""));
    }

    boolean istAn() {
        return "true".equalsIgnoreCase(einstellungen.get(AN, "false"));
    }

    /** Schaltet um. Beim Einschalten gilt ab jetzt: Nur Mails, die danach ankommen, werden automatisch zugeordnet. */
    public Status setze(boolean an) {
        if (an && !istAn()) {
            einstellungen.save(SEIT, LocalDateTime.now().toString(), "Vollautomatische E-Mail-Zuordnung gilt für Mails ab diesem Zeitpunkt");
            synchronized (versucht) {
                versucht.clear();
            }
        }
        einstellungen.save(AN, String.valueOf(an), "E-Mails vollautomatisch mit KI zuordnen");
        return status();
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void lauf() {
        if (!istAn()) {
            return;
        }
        LocalDateTime seit;
        try {
            seit = LocalDateTime.parse(einstellungen.get(SEIT, ""));
        } catch (RuntimeException e) {
            return;
        }
        try {
            List<Email> offen = emails.findByZuordnungTypOrderBySentAtDesc(EmailZuordnungTyp.KEINE).stream()
                    .filter(e -> e.getDirection() == EmailDirection.IN && e.getDeletedAt() == null && !e.isSpam() && !e.isNewsletter())
                    .filter(e -> e.getSentAt() != null && !e.getSentAt().isBefore(seit))
                    .filter(e -> {
                        synchronized (versucht) {
                            return !versucht.contains(e.getId());
                        }
                    })
                    .limit(PRO_LAUF)
                    .toList();
            int zugeordnet = 0;
            for (Email e : offen) {
                synchronized (versucht) {
                    versucht.add(e.getId());
                    if (versucht.size() > MERKE_MAX) {
                        versucht.remove(versucht.iterator().next());
                    }
                }
                try {
                    if (zuordnung.tryAutoAssign(e)) {
                        zugeordnet++;
                    }
                } catch (RuntimeException ex) {
                    log.warn("[KI-Automatik] E-Mail {} fehlgeschlagen: {}", e.getId(), ex.getMessage());
                }
            }
            if (!offen.isEmpty()) {
                log.info("[KI-Automatik] {} von {} neuen E-Mails zugeordnet", zugeordnet, offen.size());
            }
        } catch (RuntimeException ex) {
            log.warn("[KI-Automatik] Lauf fehlgeschlagen: {}", ex.getMessage());
        }
    }
}
