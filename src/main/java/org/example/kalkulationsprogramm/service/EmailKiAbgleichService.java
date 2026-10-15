package org.example.kalkulationsprogramm.service;

import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.domain.EmailDirection;
import org.example.kalkulationsprogramm.domain.EmailZuordnungTyp;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import lombok.RequiredArgsConstructor;

/**
 * Ausgewählte E-Mails mit der Zuordnungslogik (Lieferant, Kunde, Schlagwörter, KI) abgleichen, <b>ohne etwas zu speichern</b>: Das Ergebnis ist ein Vorschlag je Mail.
 * Erst {@link #uebernehme} schreibt die Zuordnungen, die der Benutzer bestätigt hat. Der Abgleich nutzt dieselbe Logik wie die automatische Zuordnung
 * ({@link EmailAutoAssignmentService#tryAutoAssign}) und nimmt sie in einer Transaktion vor, die immer zurückgerollt wird.
 */
@Service
@RequiredArgsConstructor
public class EmailKiAbgleichService {

    private static final Logger log = LoggerFactory.getLogger(EmailKiAbgleichService.class);
    static final int MAX_MAILS = 50;

    private final EmailRepository emails;
    private final EmailAutoAssignmentService zuordnung;
    private final ProjektRepository projekte;
    private final AnfrageRepository anfragen;
    private final LieferantenRepository lieferanten;
    private final PlatformTransactionManager transaktionen;

    /** Optional: markiert zugeordnete Mails im Postfach, wenn der Administrator das eingeschaltet hat. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PostfachSpiegelService postfachSpiegel;

    /** {@code art}: PROJEKT, ANFRAGE, LIEFERANT oder KEINE (dann sagt {@code hinweis} warum). */
    public record Vorschlag(Long emailId, String betreff, String von, String art, Long zielId, String ziel, String hinweis) { }

    public record Zuordnung(Long emailId, String art, Long zielId) { }

    public record Ergebnis(int zugeordnet, List<String> abgelehnt) { }

    public List<Vorschlag> vorschlaege(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("Bitte mindestens eine E-Mail auswählen.");
        }
        if (ids.size() > MAX_MAILS) {
            throw new IllegalArgumentException("Bitte höchstens " + MAX_MAILS + " E-Mails auf einmal abgleichen.");
        }
        TransactionTemplate probe = new TransactionTemplate(transaktionen);
        probe.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        List<Vorschlag> ergebnis = new ArrayList<>();
        for (Long id : ids.stream().distinct().toList()) {
            try {
                ergebnis.add(probe.execute(status -> {
                    // Alles in dieser Transaktion wird verworfen: Es geht nur um das Ergebnis.
                    status.setRollbackOnly();
                    return pruefe(id);
                }));
            } catch (RuntimeException e) {
                log.warn("[KI-Abgleich] E-Mail {} konnte nicht abgeglichen werden: {}", id, e.getMessage());
                ergebnis.add(new Vorschlag(id, null, null, "KEINE", null, null, "Der Abgleich ist fehlgeschlagen."));
            }
        }
        return ergebnis;
    }

    private Vorschlag pruefe(Long id) {
        Email e = emails.findById(id).orElse(null);
        if (e == null) {
            return new Vorschlag(id, null, null, "KEINE", null, null, "Die E-Mail gibt es nicht mehr.");
        }
        String betreff = e.getSubject();
        String von = e.getFromAddress();
        if (e.getDirection() != EmailDirection.IN) {
            return new Vorschlag(id, betreff, von, "KEINE", null, null, "Nur eingehende E-Mails lassen sich zuordnen.");
        }
        if (e.getZuordnungTyp() != EmailZuordnungTyp.KEINE) {
            return new Vorschlag(id, betreff, von, "KEINE", null, null, "Ist schon zugeordnet.");
        }
        boolean gefunden = zuordnung.tryAutoAssign(e);
        if (!gefunden) {
            return new Vorschlag(id, betreff, von, "KEINE", null, null, "Keine passende Zuordnung gefunden.");
        }
        return switch (e.getZuordnungTyp()) {
            case PROJEKT -> new Vorschlag(id, betreff, von, "PROJEKT", e.getProjekt().getId(), e.getProjekt().getBauvorhaben(), null);
            case ANFRAGE -> new Vorschlag(id, betreff, von, "ANFRAGE", e.getAnfrage().getId(), e.getAnfrage().getBauvorhaben(), null);
            case LIEFERANT -> new Vorschlag(id, betreff, von, "LIEFERANT", e.getLieferant().getId(), e.getLieferant().getLieferantenname(), null);
            default -> new Vorschlag(id, betreff, von, "KEINE", null, null, "Keine passende Zuordnung gefunden.");
        };
    }

    /** Schreibt die bestätigten Zuordnungen. Bereits zugeordnete Mails und unbekannte Ziele werden übersprungen und gemeldet. */
    @Transactional
    public Ergebnis uebernehme(List<Zuordnung> liste) {
        if (liste == null || liste.isEmpty()) {
            throw new IllegalArgumentException("Es gibt nichts zu übernehmen.");
        }
        if (liste.size() > MAX_MAILS) {
            throw new IllegalArgumentException("Bitte höchstens " + MAX_MAILS + " Zuordnungen auf einmal übernehmen.");
        }
        int ok = 0;
        List<String> abgelehnt = new ArrayList<>();
        for (Zuordnung z : liste) {
            if (z == null || z.emailId() == null || z.zielId() == null || z.art() == null) {
                abgelehnt.add("Unvollständige Angabe");
                continue;
            }
            Email e = emails.findById(z.emailId()).orElse(null);
            if (e == null) {
                abgelehnt.add("E-Mail " + z.emailId() + " gibt es nicht mehr");
                continue;
            }
            if (e.getZuordnungTyp() != EmailZuordnungTyp.KEINE) {
                abgelehnt.add("„" + kurz(e.getSubject()) + "“ wurde inzwischen zugeordnet");
                continue;
            }
            String[] marke = { null };
            boolean geschrieben = switch (z.art()) {
                case "PROJEKT" -> projekte.findById(z.zielId()).map(p -> {
                    e.assignToProjekt((Projekt) p);
                    marke[0] = p.getAuftragsnummer() != null && !p.getAuftragsnummer().isBlank() ? p.getAuftragsnummer() : "Projekt_" + p.getId();
                    return true;
                }).orElse(false);
                case "ANFRAGE" -> anfragen.findById(z.zielId()).map(a -> { e.assignToAnfrage((Anfrage) a); marke[0] = "Anfrage_" + a.getId(); return true; }).orElse(false);
                case "LIEFERANT" -> lieferanten.findById(z.zielId()).map(l -> { e.assignToLieferant((Lieferanten) l); return true; }).orElse(false);
                default -> false;
            };
            if (!geschrieben) {
                abgelehnt.add("Das Ziel für „" + kurz(e.getSubject()) + "“ gibt es nicht");
                continue;
            }
            emails.save(e);
            if (postfachSpiegel != null && marke[0] != null) {
                postfachSpiegel.markierenVormerken(e, marke[0]);
            }
            ok++;
        }
        return new Ergebnis(ok, abgelehnt);
    }

    private static String kurz(String s) {
        if (s == null || s.isBlank()) {
            return "(ohne Betreff)";
        }
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }
}
