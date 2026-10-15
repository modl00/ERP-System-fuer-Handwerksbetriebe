package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.Maschine;
import org.example.kalkulationsprogramm.domain.MaschineSatzVerlauf;
import org.example.kalkulationsprogramm.dto.Maschine.MaschineDto;
import org.example.kalkulationsprogramm.dto.Maschine.MaschineDto.ArbeitsgangKurz;
import org.example.kalkulationsprogramm.dto.Maschine.MaschineEingabeDto;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.MaschineRepository;
import org.example.kalkulationsprogramm.repository.MaschineSatzVerlaufRepository;
import org.example.kalkulationsprogramm.service.MaschinenKostenRechner.Aufstellung;
import org.example.kalkulationsprogramm.service.MaschinenKostenRechner.Eingabe;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Die Maschinenliste (Stufe 2 von #166): Maschinen anlegen und ändern, Kostensatz errechnen oder von Hand festlegen, Arbeitsgänge zuordnen, Änderungen am Satz
 * festhalten. Die Kalkulation liest den Satz über {@link Arbeitsgang#effektiverMaschinenSatz()}; für Arbeitsgänge ohne Maschine aus der Liste gilt der alte Wert.
 */
@Service
@RequiredArgsConstructor
public class MaschineService {

    static final BigDecimal MAX_SATZ = new BigDecimal("1000");

    private final MaschineRepository maschinen;
    private final MaschineSatzVerlaufRepository verlauf;
    private final ArbeitsgangRepository arbeitsgaenge;

    @Transactional(readOnly = true)
    public List<MaschineDto> alle() {
        Map<Long, List<ArbeitsgangKurz>> nachMaschine = new HashMap<>();
        for (Arbeitsgang a : arbeitsgaenge.findByMaschineIsNotNull()) {
            nachMaschine.computeIfAbsent(a.getMaschine().getId(), k -> new ArrayList<>()).add(new ArbeitsgangKurz(a.getId(), a.getBeschreibung()));
        }
        nachMaschine.values().forEach(l -> l.sort((x, y) -> x.beschreibung().compareToIgnoreCase(y.beschreibung())));
        return maschinen.findAllByOrderByNameAsc().stream().map(m -> alsDto(m, nachMaschine.getOrDefault(m.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public List<MaschineDto.Verlauf> verlauf(Long id) {
        lade(id);
        return verlauf.findByMaschineIdOrderByGeaendertAmDescIdDesc(id).stream()
                .map(v -> new MaschineDto.Verlauf(v.getSatz(), v.isVonHand(), v.getGueltigAb(), v.getGeaendertVon(), v.getGeaendertAm())).toList();
    }

    /** Die Rechnung für den Rechner im Dialog, ohne etwas zu speichern. */
    public Aufstellung rechne(Eingabe eingabe) {
        return MaschinenKostenRechner.rechne(eingabe);
    }

    @Transactional
    public MaschineDto anlegen(MaschineEingabeDto e, String benutzer) {
        Maschine m = new Maschine();
        uebernehmen(m, e);
        speichereMitVerlauf(m, null, false, benutzer);
        ordneZu(m, e.arbeitsgangIds());
        return alsDto(m);
    }

    @Transactional
    public MaschineDto aendern(Long id, MaschineEingabeDto e, String benutzer) {
        Maschine m = lade(id);
        BigDecimal altSatz = m.getKostensatz();
        boolean altHand = m.getSatzVonHand() != null;
        uebernehmen(m, e);
        speichereMitVerlauf(m, altSatz, altHand, benutzer);
        ordneZu(m, e.arbeitsgangIds());
        return alsDto(m);
    }

    @Transactional
    public void loeschen(Long id) {
        Maschine m = lade(id);
        List<Arbeitsgang> genutzt = arbeitsgaenge.findByMaschineId(id);
        if (!genutzt.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Maschine \"" + m.getName() + "\" wird von " + genutzt.size()
                    + (genutzt.size() == 1 ? " Arbeitsgang" : " Arbeitsgängen") + " genutzt. Lösen Sie zuerst die Zuordnung, oder blenden Sie die Maschine aus.");
        }
        maschinen.delete(m);
    }

    /**
     * Übernimmt den alten Stand: Jeder Arbeitsgang mit einem Maschinensatz, aber ohne Maschine aus der Liste, bekommt eine Maschine mit demselben Satz (von Hand).
     * Dadurch bleiben die Preise der Leistungen gleich. Mehrfach ausführbar: Was schon eine Maschine hat, wird nicht noch einmal angefasst.
     *
     * @return wie viele Maschinen angelegt wurden
     */
    @Transactional
    public int uebernehmeAltbestand() {
        int neu = 0;
        for (Arbeitsgang a : arbeitsgaenge.findAll()) {
            BigDecimal satz = a.getMaschinenStundensatz();
            if (a.getMaschine() != null || satz == null || satz.signum() <= 0) {
                continue;
            }
            Maschine m = new Maschine();
            m.setName(freierName(a.getMaschinenBezeichnung() != null && !a.getMaschinenBezeichnung().isBlank() ? a.getMaschinenBezeichnung().strip() : a.getBeschreibung()));
            m.setSatzVonHand(satz.setScale(2, RoundingMode.HALF_UP));
            m.setKostensatz(satz.setScale(2, RoundingMode.HALF_UP));
            m.setBemerkung("Aus dem Arbeitsgang \"" + a.getBeschreibung() + "\" übernommen");
            m = maschinen.save(m);
            festhalten(m, "Übernahme", true);
            a.setMaschine(m);
            arbeitsgaenge.save(a);
            neu++;
        }
        return neu;
    }

    // ------------------------------------------------------------------ innen

    private Maschine lade(Long id) {
        return maschinen.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Maschine wurde nicht gefunden."));
    }

    private String freierName(String wunsch) {
        String basis = wunsch.length() > 100 ? wunsch.substring(0, 100) : wunsch;
        String name = basis;
        int n = 2;
        while (maschinen.findByNameIgnoreCase(name).isPresent()) {
            String zusatz = " (" + n++ + ")";
            name = (basis.length() + zusatz.length() > 100 ? basis.substring(0, 100 - zusatz.length()) : basis) + zusatz;
        }
        return name;
    }

    private void uebernehmen(Maschine m, MaschineEingabeDto e) {
        String name = e.name() == null ? "" : e.name().strip();
        maschinen.findByNameIgnoreCase(name).filter(v -> !v.getId().equals(m.getId())).ifPresent(v -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Eine Maschine mit dem Namen \"" + v.getName() + "\" gibt es schon.");
        });
        m.setName(name);
        m.setAnschaffungswert(geld(e.anschaffungswert()));
        m.setRestwert(geld(e.restwert()));
        m.setNutzungsdauerJahre(e.nutzungsdauerJahre() == null ? null : e.nutzungsdauerJahre().setScale(2, RoundingMode.HALF_UP));
        m.setZinssatzProzent(e.zinssatzProzent() == null ? null : e.zinssatzProzent().setScale(2, RoundingMode.HALF_UP));
        m.setWartungJeJahr(geld(e.wartungJeJahr()));
        m.setPlatzJeJahr(geld(e.platzJeJahr()));
        m.setVersicherungJeJahr(geld(e.versicherungJeJahr()));
        m.setLeistungKw(e.leistungKw() == null ? null : e.leistungKw().setScale(2, RoundingMode.HALF_UP));
        m.setStrompreisJeKwh(e.strompreisJeKwh() == null ? null : e.strompreisJeKwh().setScale(4, RoundingMode.HALF_UP));
        m.setAuslastungProzent(e.auslastungProzent() == null ? null : e.auslastungProzent().setScale(2, RoundingMode.HALF_UP));
        m.setLaufstundenJeJahr(e.laufstundenJeJahr() == null ? null : e.laufstundenJeJahr().setScale(1, RoundingMode.HALF_UP));
        m.setSatzVonHand(geld(e.satzVonHand()));
        m.setAktiv(e.aktiv() == null || e.aktiv());
        m.setBemerkung(e.bemerkung() == null || e.bemerkung().isBlank() ? null : e.bemerkung().strip());
        Aufstellung a = MaschinenKostenRechner.rechne(eingabe(m));
        BigDecimal satz = m.getSatzVonHand() != null ? m.getSatzVonHand() : a.summe();
        if (satz == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, a.hinweis() + " Oder legen Sie den Satz von Hand fest.");
        }
        if (satz.signum() <= 0 || satz.compareTo(MAX_SATZ) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Satz muss größer als 0 sein und darf höchstens 1.000 Euro je Stunde betragen (errechnet: "
                    + satz.toPlainString() + " Euro). Bitte die Angaben prüfen.");
        }
        m.setKostensatz(satz);
    }

    private void speichereMitVerlauf(Maschine m, BigDecimal altSatz, boolean altHand, String benutzer) {
        boolean neu = m.getId() == null;
        Maschine gespeichert = maschinen.save(m);
        boolean hand = gespeichert.getSatzVonHand() != null;
        if (neu || altSatz == null || altSatz.compareTo(gespeichert.getKostensatz()) != 0 || altHand != hand) {
            festhalten(gespeichert, benutzer, hand);
        }
    }

    private void festhalten(Maschine m, String benutzer, boolean vonHand) {
        MaschineSatzVerlauf v = new MaschineSatzVerlauf();
        v.setMaschine(m);
        v.setSatz(m.getKostensatz());
        v.setVonHand(vonHand);
        v.setGueltigAb(LocalDate.now());
        v.setGeaendertVon(benutzer == null || benutzer.isBlank() ? null : (benutzer.length() > 120 ? benutzer.substring(0, 120) : benutzer));
        v.setGeaendertAm(LocalDateTime.now());
        verlauf.save(v);
    }

    /** Setzt die Zuordnung der Arbeitsgänge; {@code null} lässt sie, wie sie ist. Wer entfällt, hat danach keine Maschine mehr (auch nicht den alten Wert). */
    private void ordneZu(Maschine m, List<Long> ids) {
        if (ids == null) {
            return;
        }
        Set<Long> gewuenscht = new HashSet<>(ids);
        for (Arbeitsgang a : arbeitsgaenge.findByMaschineId(m.getId())) {
            if (!gewuenscht.remove(a.getId())) {
                a.setMaschine(null);
                a.setMaschinenStundensatz(null);
                a.setMaschinenBezeichnung(null);
                arbeitsgaenge.save(a);
            }
        }
        for (Long id : gewuenscht) {
            Arbeitsgang a = arbeitsgaenge.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ein gewählter Arbeitsgang gibt es nicht mehr."));
            a.setMaschine(m);
            arbeitsgaenge.save(a);
        }
    }

    private static BigDecimal geld(BigDecimal wert) {
        return wert == null ? null : wert.setScale(2, RoundingMode.HALF_UP);
    }

    private static Eingabe eingabe(Maschine m) {
        return new Eingabe(m.getAnschaffungswert(), m.getRestwert(), m.getNutzungsdauerJahre(), m.getZinssatzProzent(), m.getWartungJeJahr(), m.getPlatzJeJahr(),
                m.getVersicherungJeJahr(), m.getLeistungKw(), m.getStrompreisJeKwh(), m.getAuslastungProzent(), m.getLaufstundenJeJahr());
    }

    private MaschineDto alsDto(Maschine m) {
        return alsDto(m, arbeitsgaenge.findByMaschineId(m.getId()).stream().map(a -> new ArbeitsgangKurz(a.getId(), a.getBeschreibung()))
                .sorted((x, y) -> x.beschreibung().compareToIgnoreCase(y.beschreibung())).toList());
    }

    private MaschineDto alsDto(Maschine m, List<ArbeitsgangKurz> genutzt) {
        return new MaschineDto(m.getId(), m.getName(), m.getAnschaffungswert(), m.getRestwert(), m.getNutzungsdauerJahre(), m.getZinssatzProzent(), m.getWartungJeJahr(),
                m.getPlatzJeJahr(), m.getVersicherungJeJahr(), m.getLeistungKw(), m.getStrompreisJeKwh(), m.getAuslastungProzent(), m.getLaufstundenJeJahr(), m.getSatzVonHand(),
                m.getKostensatz(), m.getSatzVonHand() != null, m.isAktiv(), m.getBemerkung(), MaschinenKostenRechner.rechne(eingabe(m)), genutzt);
    }
}
