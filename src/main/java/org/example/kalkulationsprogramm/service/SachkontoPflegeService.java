package org.example.kalkulationsprogramm.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.Sachkonto;
import org.example.kalkulationsprogramm.domain.SachkontoTyp;
import org.example.kalkulationsprogramm.dto.SachkontoDto;
import org.example.kalkulationsprogramm.repository.SachkontoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Konten anlegen, umbenennen und ausblenden (Issue #98). Prüft in Klartext, was das Programm sonst still annähme:
 * Ziffern in der Nummer, keine doppelten Nummern oder Namen, und die Konten, die das Programm selbst braucht, bleiben unangetastet.
 *
 * <p>Fehler sind {@link IllegalArgumentException} mit einem Satz für den Anwender; der Controller macht daraus eine 400.</p>
 */
@Service
@RequiredArgsConstructor
public class SachkontoPflegeService {

    /** Kasse, Privatentnahme, Privateinlage, Lohn, Einnahmen und Bank sucht der Code über die Nummer ({@code findByNummer}). */
    static final Set<String> SYSTEMKONTEN = Set.of("1200", "1800", "1890", "4120", "8400");

    private static final Pattern NUMMER = Pattern.compile("\\d{3,8}");
    private static final int MAX_BEZEICHNUNG = 120;
    private static final int MAX_BESCHREIBUNG = 500;
    private static final int STANDARD_SORTIERUNG = 900;

    private final SachkontoRepository repository;

    @Transactional
    public Sachkonto anlegen(SachkontoDto.UpsertRequest req) {
        String bezeichnung = bezeichnung(req.getBezeichnung());
        SachkontoTyp typ = typ(req.getKontoTyp(), true);
        String nummer = nummer(req.getNummer());
        List<Sachkonto> alle = repository.findAll();
        pruefeEindeutig(alle, null, nummer, bezeichnung);

        Sachkonto neu = new Sachkonto();
        neu.setNummer(nummer);
        neu.setBezeichnung(bezeichnung);
        neu.setKontoTyp(typ);
        neu.setBeschreibung(beschreibung(req.getBeschreibung()));
        neu.setAktiv(req.getAktiv() == null || req.getAktiv());
        neu.setSortierung(req.getSortierung() != null ? req.getSortierung() : STANDARD_SORTIERUNG);
        return repository.save(neu);
    }

    @Transactional
    public Sachkonto aendern(Long id, SachkontoDto.UpsertRequest req) {
        Sachkonto konto = repository.findById(id).orElseThrow(() -> new java.util.NoSuchElementException("Das Konto gibt es nicht."));
        boolean systemkonto = konto.getNummer() != null && SYSTEMKONTEN.contains(konto.getNummer());

        String bezeichnung = req.getBezeichnung() != null ? bezeichnung(req.getBezeichnung()) : konto.getBezeichnung();
        String nummer = req.getNummer() != null ? nummer(req.getNummer()) : konto.getNummer();
        SachkontoTyp typ = req.getKontoTyp() != null ? typ(req.getKontoTyp(), false) : konto.getKontoTyp();
        boolean aktiv = req.getAktiv() != null ? req.getAktiv() : konto.isAktiv();

        if (systemkonto) {
            if (!konto.getNummer().equals(nummer) || typ != konto.getKontoTyp() || !aktiv) {
                throw new IllegalArgumentException("Das Konto " + konto.getNummer() + " braucht das Programm für die Kasse. Nummer, Art und Sichtbarkeit lassen sich nicht ändern, Name und Beschreibung schon.");
            }
        }
        pruefeEindeutig(repository.findAll(), id, nummer, bezeichnung);

        konto.setBezeichnung(bezeichnung);
        konto.setNummer(nummer);
        konto.setKontoTyp(typ);
        if (req.getBeschreibung() != null) konto.setBeschreibung(beschreibung(req.getBeschreibung()));
        konto.setAktiv(aktiv);
        if (req.getSortierung() != null) konto.setSortierung(req.getSortierung());
        return repository.save(konto);
    }

    public static boolean istSystemkonto(String nummer) {
        return nummer != null && SYSTEMKONTEN.contains(nummer);
    }

    private static String bezeichnung(String roh) {
        String b = roh == null ? "" : roh.strip();
        if (b.length() < 2) {
            throw new IllegalArgumentException("Bitte einen Namen für das Konto eintragen.");
        }
        if (b.length() > MAX_BEZEICHNUNG) {
            throw new IllegalArgumentException("Der Name ist zu lang (höchstens " + MAX_BEZEICHNUNG + " Zeichen).");
        }
        return b;
    }

    private static String nummer(String roh) {
        if (roh == null || roh.isBlank()) return null;
        String n = roh.strip();
        if (!NUMMER.matcher(n).matches()) {
            throw new IllegalArgumentException("Die Kontonummer besteht nur aus Ziffern (3 bis 8 Stellen).");
        }
        return n;
    }

    private static SachkontoTyp typ(String roh, boolean pflicht) {
        if (roh == null || roh.isBlank()) {
            if (pflicht) throw new IllegalArgumentException("Bitte die Art des Kontos wählen: Aufwand, Ertrag, Privat oder Neutral.");
            return null;
        }
        try {
            return SachkontoTyp.valueOf(roh.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Die Art des Kontos ist unbekannt. Möglich sind Aufwand, Ertrag, Privat und Neutral.");
        }
    }

    private static String beschreibung(String roh) {
        if (roh == null) return null;
        String b = roh.strip();
        if (b.length() > MAX_BESCHREIBUNG) {
            throw new IllegalArgumentException("Die Beschreibung ist zu lang (höchstens " + MAX_BESCHREIBUNG + " Zeichen).");
        }
        return b.isEmpty() ? null : b;
    }

    private static void pruefeEindeutig(List<Sachkonto> alle, Long eigeneId, String nummer, String bezeichnung) {
        for (Sachkonto k : alle) {
            if (k.getId() != null && k.getId().equals(eigeneId)) continue;
            if (k.getBezeichnung() != null && k.getBezeichnung().equalsIgnoreCase(bezeichnung)) {
                throw new IllegalArgumentException("Ein Konto mit dem Namen „" + k.getBezeichnung() + "“ gibt es schon.");
            }
            if (nummer != null && nummer.equals(k.getNummer())) {
                throw new IllegalArgumentException("Die Nummer " + nummer + " ist schon vergeben (" + k.getBezeichnung() + ").");
            }
        }
    }
}
