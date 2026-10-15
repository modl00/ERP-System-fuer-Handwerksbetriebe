package org.example.kalkulationsprogramm.service.planung;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.example.kalkulationsprogramm.domain.Fremdleistung;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.domain.planung.PlanVorgangstyp;
import org.example.kalkulationsprogramm.repository.FremdleistungRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanProjektRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanVorgangRepository;
import org.example.kalkulationsprogramm.repository.planung.PlanVorgangstypRepository;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.AuftragDto;
import org.example.kalkulationsprogramm.service.planung.PlanungsService.KettenSchritt;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Das Fenster bei der Auftragsbestätigung (Issue #331, Abschnitt 5): Was die Planung braucht, steht vorausgefüllt da, „Übernehmen“ reicht meist.
 * Die Fremdleistungen kommen aus den Leistungen des Angebots bzw. der Auftragsbestätigung (Kalkulationszeile Typ Fremdleistung, kein Blindfragen); Aufwand je Abteilung
 * aus der Kalkulation. „Später“ legt die Standardkette mit geschätzten Werten an und hält die Auftragsbestätigung nie auf.
 */
@Service
@RequiredArgsConstructor
public class PlanungsAufnahme {

    private static final int MAX_FREMDLEISTUNGEN = 20;
    private static final String FENSTER_AUS = "planung.abFenster.aus.";

    private final PlanungsService planung;
    private final AngebotsZeiten angebotsZeiten;
    private final PlanVorgangRepository vorgaenge;
    private final PlanProjektRepository termine;
    private final PlanVorgangstypRepository typen;
    private final ProjektRepository projekte;
    private final FremdleistungRepository fremdleistungen;
    private final LieferantenRepository lieferanten;
    private final MitarbeiterRepository mitarbeiter;
    private final SystemSettingRepository einstellungen;
    private final PlanungsEinstellungen planungsEinstellungen;

    /** Eine erkannte Fremdleistung des Auftrags; {@code lieferzeitTage} = übliche Lieferzeit des Veredlers. */
    public record FremdleistungDto(Long fremdleistungId, String bezeichnung, Long lieferantId, String lieferantName, String einheit, BigDecimal menge, Integer lieferzeitTage) { }

    public record ArtDto(Long id, String name, boolean wartet, boolean buero) { }

    /** Das Fenster, vorausgefüllt. {@code quelle}: ANGEBOT, AUFTRAGSBESTAETIGUNG oder leer (keine Leistungen). */
    public record Aufnahme(Long projektId, String bezeichnung, String auftragsnummer, boolean bereitsGeplant, boolean offen, String quelle, List<FremdleistungDto> fremdleistungen,
            List<ArtDto> arten, List<String> hinweise, BigDecimal arbeitstagStunden, boolean hatArbeitszeit) { }

    public record FremdleistungEingabe(Long fremdleistungId, String bezeichnung, Long lieferantId, BigDecimal menge, String einheit) { }

    /** {@code spaeter}: überspringen, Standardkette mit geschätzten Werten. */
    public record Eingabe(boolean spaeter, LocalDate zielDatum, String prioritaet, List<FremdleistungEingabe> fremdleistungen, boolean montageVorOrt, Integer montageTage, List<Long> mitarbeiterIds) { }

    @Transactional(readOnly = true)
    public Aufnahme aufnahme(Long projektId) {
        Projekt p = projekt(projektId);
        AngebotsZeiten.Zeiten zeiten = angebotsZeiten.fuerProjekt(projektId);
        List<FremdleistungDto> fremd = new ArrayList<>();
        for (AngebotsZeiten.Fremd f : zeiten.fremdleistungen()) {
            Fremdleistung fl = fremdleistungen.findById(f.fremdleistungId()).orElse(null);
            Lieferanten l = fl == null ? null : fl.getLieferant();
            fremd.add(new FremdleistungDto(f.fremdleistungId(), f.bezeichnung(), l == null ? null : l.getId(), l == null ? f.veredler() : l.getLieferantenname(), f.einheit(),
                    f.menge().setScale(2, java.math.RoundingMode.HALF_UP), l == null ? null : l.getStandardLieferzeitTage()));
        }
        List<ArtDto> arten = typen.findAllByOrderBySortierungAscNameAsc().stream().filter(PlanVorgangstyp::isAktiv).map(t -> new ArtDto(t.getId(), t.getName(), t.isWartet(), t.isBuero())).toList();
        boolean geplant = !vorgaenge.findByProjektIdOrderBySortierungAscIdAsc(projektId).isEmpty();
        boolean offen = termine.findById(projektId).map(pt -> pt.isPlanungOffen()).orElse(false);
        String name = p.getBauvorhaben() != null && !p.getBauvorhaben().isBlank() ? p.getBauvorhaben() : "Auftrag " + p.getId();
        return new Aufnahme(projektId, name, p.getAuftragsnummer(), geplant && !offen, offen, zeiten.quelle(), fremd, arten, zeiten.hinweise(), planungsEinstellungen.rhythmusLesen().arbeitstagStunden(),
                zeiten.stundenJeAbteilung().values().stream().anyMatch(h -> h.signum() > 0));
    }

    /** Legt den Auftrag in der Plantafel an, wie im Fenster bestätigt (oder mit „Später“ mit geschätzten Werten). */
    @Transactional
    public AuftragDto planen(Long projektId, Eingabe e) {
        projekt(projektId);
        if (e == null) {
            throw new IllegalArgumentException("Es fehlen die Angaben.");
        }
        if (e.spaeter()) {
            return planung.planungSpaeter(projektId);
        }
        List<FremdleistungEingabe> fremd = e.fremdleistungen() == null ? List.of() : e.fremdleistungen();
        if (fremd.size() > MAX_FREMDLEISTUNGEN) {
            throw new IllegalArgumentException("Es können höchstens " + MAX_FREMDLEISTUNGEN + " Fremdleistungen geplant werden.");
        }
        if (e.montageTage() != null && (e.montageTage() < 1 || e.montageTage() > 365)) {
            throw new IllegalArgumentException("Die Montage dauert zwischen 1 und 365 Tage.");
        }
        List<Long> personen = e.mitarbeiterIds() == null ? List.of() : e.mitarbeiterIds().stream().distinct().toList();
        for (Long id : personen) {
            if (id == null || !mitarbeiter.existsById(id)) {
                throw new IllegalArgumentException("Einen der Mitarbeiter gibt es nicht.");
            }
        }
        for (FremdleistungEingabe f : fremd) {
            if (f.lieferantId() != null && !lieferanten.existsById(f.lieferantId())) {
                throw new IllegalArgumentException("Den Lieferanten einer Fremdleistung gibt es nicht.");
            }
            if (f.menge() != null && (f.menge().signum() < 0 || f.menge().compareTo(new BigDecimal("10000000")) > 0)) {
                throw new IllegalArgumentException("Die Menge einer Fremdleistung ist nicht gültig.");
            }
        }
        BigDecimal tag = planungsEinstellungen.rhythmusLesen().arbeitstagStunden();
        AngebotsZeiten.Zeiten zeiten = angebotsZeiten.fuerProjekt(projektId);
        List<KettenSchritt> schritte = new ArrayList<>();
        for (PlanVorgangstyp art : typen.findAllByOrderBySortierungAscNameAsc()) {
            if (!art.isAktiv()) {
                continue;
            }
            String name = art.getName().toLowerCase(Locale.ROOT);
            if (art.isWartet() && name.contains("veredel")) {
                for (FremdleistungEingabe f : fremd) {
                    Lieferanten l = f.lieferantId() == null ? null : lieferanten.findById(f.lieferantId()).orElse(null);
                    String bezeichnung = f.bezeichnung() == null || f.bezeichnung().isBlank() ? art.getName() : f.bezeichnung().strip();
                    schritte.add(new KettenSchritt(art.getId(), l == null ? bezeichnung : bezeichnung + " (" + l.getLieferantenname() + ")", null, null, f.lieferantId(), f.fremdleistungId(), f.menge(),
                            einheitKuerzel(f.einheit()), false));
                }
                continue;
            }
            if (!art.isWartet() && name.contains("montage")) {
                if (e.montageVorOrt()) {
                    int tage = e.montageTage() == null ? 1 : e.montageTage();
                    schritte.add(new KettenSchritt(art.getId(), null, BigDecimal.valueOf(tage).multiply(tag).multiply(BigDecimal.valueOf(Math.max(1, personen.size()))), null, null, null, null, null, true));
                }
                continue;
            }
            if (art.isWartet()) {
                schritte.add(new KettenSchritt(art.getId(), null, null, null, null, null, null, null, false));
            } else {
                // Aufwand aus der Kalkulation, soweit die Art eine Abteilung hat; sonst Schätzwert der Art
                BigDecimal stunden = art.getAbteilungId() == null ? null : zeiten.stundenJeAbteilung().get(art.getAbteilungId());
                schritte.add(new KettenSchritt(art.getId(), null, stunden != null && stunden.signum() > 0 ? stunden : null, null, null, null, null, null, !art.isBuero()));
            }
        }
        AuftragDto ergebnis = planung.planeKette(projektId, schritte, e.zielDatum(), e.prioritaet(), personen);
        // Der Aufwand aus der Kalkulation gilt als „aus der Kalkulation“ (nicht „von Hand“): über den Vorschlag setzen
        List<Long> ausKalkulation = planung.vorschlag(projektId).zeilen().stream().filter(z -> z.vorgeschlageneDauer() != null).map(PlanungsService.DauerVorschlag::vorgangId).toList();
        return ausKalkulation.isEmpty() ? ergebnis : planung.vorschlagUebernehmen(projektId, ausKalkulation);
    }

    private static String einheitKuerzel(String einheit) {
        if (einheit == null) {
            return null;
        }
        String e = einheit.strip().toLowerCase(Locale.ROOT);
        if (e.contains("kg")) {
            return "KG";
        }
        return e.contains("m²") || e.contains("m2") || e.contains("quadrat") ? "M2" : null;
    }

    // ───────────────────────────── Fenster abschalten ─────────────────────────────

    /** Zeigt das Fenster bei der Auftragsbestätigung für den angemeldeten Benutzer (Vorgabe: ja). */
    @Transactional(readOnly = true)
    public boolean fensterAktiv() {
        return einstellungen.findById(FENSTER_AUS + benutzer()).map(SystemSetting::getValue).filter("1"::equals).isEmpty();
    }

    @Transactional
    public boolean fensterSetzen(boolean aktiv) {
        String schluessel = FENSTER_AUS + benutzer();
        if (aktiv) {
            einstellungen.findById(schluessel).ifPresent(einstellungen::delete);
        } else {
            SystemSetting s = einstellungen.findById(schluessel).orElseGet(SystemSetting::new);
            s.setKey(schluessel);
            s.setValue("1");
            einstellungen.save(s);
        }
        return aktiv;
    }

    private static String benutzer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth == null ? null : auth.getName();
        return name == null || name.isBlank() || "anonymousUser".equals(name) ? "unbekannt" : name.length() > 80 ? name.substring(0, 80) : name;
    }

    private Projekt projekt(Long id) {
        return projekte.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Den Auftrag gibt es nicht."));
    }
}
