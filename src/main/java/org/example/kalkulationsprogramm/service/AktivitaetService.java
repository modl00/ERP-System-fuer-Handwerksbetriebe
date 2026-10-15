package org.example.kalkulationsprogramm.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.AktivitaetEreignis;
import org.example.kalkulationsprogramm.dto.Aktivitaet.AktivitaetDto;
import org.example.kalkulationsprogramm.dto.Aktivitaet.AktivitaetDto.Ereignis;
import org.example.kalkulationsprogramm.dto.Aktivitaet.AktivitaetDto.Einstellungen;
import org.example.kalkulationsprogramm.dto.Aktivitaet.AktivitaetDto.MeinTag;
import org.example.kalkulationsprogramm.repository.AnfrageRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.AktivitaetEreignisRepository;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.AktivitaetKatalog.Regel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Das Aktivitätsprotokoll (Issue #93, Stufe 1): ein gemeinsames Ereignisprotokoll, aus dem jede Person ihren Tagesverlauf sieht und Administratoren
 * nachvollziehen, wer wann was an einem Objekt getan hat. Gespeichert wird nur Art, Objekt und Uhrzeit, <b>nie ein Inhalt</b>.
 *
 * <p>Datenschutz: Administratoren werden immer protokolliert; für alle anderen Benutzer (Mitarbeiter) nur bei eingeschaltetem Schalter
 * ({@value #SCHLUESSEL_MITARBEITER}, Vorgabe aus), weil ein Tagesverlauf dort Arbeitszeiten ableiten lässt. Jede Person sieht nur den eigenen Verlauf; die Sicht
 * der Administratoren zeigt Ereignisse zu Objekten, keine Tagesauswertung je Mitarbeiter. Die Aufbewahrung ist einstellbar (Vorgabe 365 Tage, 0 = unbegrenzt).
 * Die GoBD-Prüfprotokolle sind davon unabhängig.
 */
@Service
@RequiredArgsConstructor
public class AktivitaetService {

    public static final String SCHLUESSEL_MITARBEITER = "aktivitaet.mitarbeiter-aktiv";
    public static final String SCHLUESSEL_TAGE = "aktivitaet.aufbewahrung-tage";
    public static final int TAGE_STANDARD = 365;
    public static final int TAGE_HOECHSTENS = 36_500;

    private static final Logger LOG = LoggerFactory.getLogger(AktivitaetService.class);
    private static final int LIMIT_STANDARD = 200;
    private static final int LIMIT_HOECHSTENS = 500;

    private final AktivitaetEreignisRepository ereignisse;
    private final SystemSettingsService einstellungen;
    private final ProjektRepository projekte;
    private final KundeRepository kunden;
    private final LieferantenRepository lieferanten;
    private final ArtikelRepository artikel;
    private final AnfrageRepository anfragen;
    private final MitarbeiterRepository mitarbeiter;

    // ------------------------------------------------------------------ Schreiben

    /** Wird für diese Person protokolliert? Administratoren immer, alle anderen nur bei eingeschaltetem Mitarbeiter-Schalter. */
    public boolean protokolliertFuer(boolean administrator) {
        return administrator || mitarbeiterAktiv();
    }

    /** Hält ein Ereignis fest. Fehler hier dürfen die eigentliche Aktion nie stören; der Aufrufer fängt sie ab. */
    @Transactional
    public void erfasse(Long profilId, String benutzer, boolean administrator, Regel regel, Long objektId) {
        if (!protokolliertFuer(administrator)) {
            return;
        }
        AktivitaetEreignis e = new AktivitaetEreignis();
        e.setProfilId(profilId);
        e.setBenutzer(benutzer == null ? null : kuerze(benutzer, 120));
        e.setZeitpunkt(LocalDateTime.now());
        e.setArt(regel.art());
        e.setBeschreibung(regel.text());
        e.setObjektTyp(regel.objektTyp());
        if (regel.objektTyp() != null && objektId != null) {
            e.setObjektId(objektId);
            e.setObjektName(name(regel.objektTyp(), objektId));
        }
        ereignisse.save(e);
    }

    // ------------------------------------------------------------------ Lesen

    @Transactional(readOnly = true)
    public MeinTag mein(Long profilId, boolean administrator, LocalDate tag) {
        LocalDate datum = tag == null ? LocalDate.now() : tag;
        List<Ereignis> liste = profilId == null ? List.of()
                : ereignisse.findByProfilIdAndZeitpunktGreaterThanEqualAndZeitpunktLessThanOrderByZeitpunktAsc(profilId, datum.atStartOfDay(), datum.plusDays(1).atStartOfDay())
                        .stream().map(e -> dto(e, false)).toList();
        return new MeinTag(datum, protokolliertFuer(administrator), aufbewahrungTage(), liste);
    }

    /** Aktivitäten im System für Administratoren: die letzten {@code tage} Tage, optional zu einer Objektart, einem Objekt oder einer Art von Ereignis. */
    @Transactional(readOnly = true)
    public List<Ereignis> imSystem(String objektTyp, Long objektId, String art, Integer tage, Integer limit) {
        int t = tage == null ? 30 : Math.max(1, Math.min(tage, TAGE_HOECHSTENS));
        int l = limit == null ? LIMIT_STANDARD : Math.max(1, Math.min(limit, LIMIT_HOECHSTENS));
        return ereignisse.suche(LocalDateTime.now().minusDays(t), leer(objektTyp), objektId, leer(art) == null ? null : art.strip().toUpperCase(Locale.ROOT), PageRequest.of(0, l))
                .stream().map(e -> dto(e, true)).toList();
    }

    /** Auskunft: alle Ereignisse einer Person (Art 15 DSGVO). */
    @Transactional(readOnly = true)
    public List<Ereignis> auskunft(Long profilId) {
        return ereignisse.findByProfilIdOrderByZeitpunktAsc(profilId).stream().map(e -> dto(e, true)).toList();
    }

    /** Löschung auf Wunsch der Person (Art 17 DSGVO); gibt die Zahl der gelöschten Ereignisse zurück. */
    @Transactional
    public int loescheVon(Long profilId) {
        int n = ereignisse.loescheVon(profilId);
        LOG.info("Aktivitätsprotokoll: {} Ereignisse einer Person gelöscht.", n);
        return n;
    }

    // ------------------------------------------------------------------ Einstellungen

    public boolean mitarbeiterAktiv() {
        return "true".equalsIgnoreCase(einstellungen.get(SCHLUESSEL_MITARBEITER, "false"));
    }

    /** Aufbewahrung in Tagen; 0 = unbegrenzt. Ungültige gespeicherte Werte fallen auf die Vorgabe zurück. */
    public int aufbewahrungTage() {
        try {
            int t = Integer.parseInt(einstellungen.get(SCHLUESSEL_TAGE, String.valueOf(TAGE_STANDARD)).strip());
            return t >= 0 && t <= TAGE_HOECHSTENS ? t : TAGE_STANDARD;
        } catch (NumberFormatException | NullPointerException e) {
            return TAGE_STANDARD;
        }
    }

    @Transactional(readOnly = true)
    public Einstellungen einstellungen() {
        return new Einstellungen(mitarbeiterAktiv(), aufbewahrungTage(), ereignisse.count());
    }

    public Einstellungen speichereEinstellungen(Boolean mitarbeiterAktiv, Integer tage) {
        if (tage != null) {
            if (tage < 0 || tage > TAGE_HOECHSTENS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Aufbewahrung muss zwischen 0 (unbegrenzt) und " + TAGE_HOECHSTENS + " Tagen liegen.");
            }
            einstellungen.save(SCHLUESSEL_TAGE, String.valueOf(tage), "Aktivitätsprotokoll: Aufbewahrung in Tagen (0 = unbegrenzt)");
        }
        if (mitarbeiterAktiv != null) {
            einstellungen.save(SCHLUESSEL_MITARBEITER, String.valueOf(mitarbeiterAktiv), "Aktivitätsprotokoll auch für Mitarbeiter (Tagesverlauf)");
        }
        return einstellungen();
    }

    /** Löscht jede Nacht Ereignisse, die älter als die Aufbewahrungsfrist sind (bei "unbegrenzt" nichts). */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void bereinige() {
        int tage = aufbewahrungTage();
        if (tage == 0) {
            return;
        }
        int n = ereignisse.loescheAelterAls(LocalDateTime.now().minusDays(tage));
        if (n > 0) {
            LOG.info("Aktivitätsprotokoll: {} Ereignisse nach {} Tagen Aufbewahrung gelöscht.", n, tage);
        }
    }

    // ------------------------------------------------------------------ intern

    private Ereignis dto(AktivitaetEreignis e, boolean mitBenutzer) {
        return new Ereignis(e.getId(), e.getZeitpunkt(), e.getArt(), e.getBeschreibung(), e.getObjektTyp(), e.getObjektId(), e.getObjektName(), mitBenutzer ? e.getBenutzer() : null);
    }

    /** Der Name des Objekts zum Zeitpunkt der Aktion; leer, wenn es das Objekt nicht (mehr) gibt oder die Art keinen Namen hat. */
    private String name(String typ, Long id) {
        try {
            return switch (typ) {
                case "Projekt" -> projekte.findById(id).map(p -> verbinde(p.getAuftragsnummer(), p.getBauvorhaben())).orElse(null);
                case "Kunde" -> kunden.findById(id).map(k -> verbinde(k.getName(), null)).orElse(null);
                case "Lieferant" -> lieferanten.findById(id).map(l -> verbinde(l.getLieferantenname(), null)).orElse(null);
                case "Artikel" -> artikel.findById(id).map(a -> verbinde(LagerService.bezeichnung(a), null)).orElse(null);
                case "Anfrage" -> anfragen.findById(id).map(a -> verbinde(a.getBauvorhaben(), null)).orElse(null);
                case "Mitarbeiter" -> mitarbeiter.findById(id).map(m -> verbinde(m.getVorname(), m.getNachname())).orElse(null);
                default -> null;
            };
        } catch (RuntimeException e) {
            LOG.debug("Name zu {} {} nicht lesbar: {}", typ, id, e.getMessage());
            return null;
        }
    }

    private static String verbinde(String a, String b) {
        String text = ((a == null ? "" : a.strip()) + " " + (b == null ? "" : b.strip())).strip();
        return text.isEmpty() ? null : kuerze(text, 200);
    }

    private static String leer(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String kuerze(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
