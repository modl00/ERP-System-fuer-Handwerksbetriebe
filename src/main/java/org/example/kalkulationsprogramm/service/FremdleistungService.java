package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Fremdleistung;
import org.example.kalkulationsprogramm.domain.Fremdleistung.Einheit;
import org.example.kalkulationsprogramm.domain.Fremdleistung.TransportArt;
import org.example.kalkulationsprogramm.dto.Fremdleistung.FremdleistungDto;
import org.example.kalkulationsprogramm.dto.Fremdleistung.FremdleistungEingabeDto;
import org.example.kalkulationsprogramm.dto.Fremdleistung.FremdleistungVergleichDto;
import org.example.kalkulationsprogramm.repository.FremdleistungRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Die Preisliste der Veredler und Fremdbetriebe (Stufe 4 von #135): je Veredler (Lieferant) mehrere Fremdleistungen mit Preisstand und den Kostenarten
 * Einheitspreis, Mindestpreis, einmalig, anteilig, Prozent, Transport und Sonstiges. Dazu der Vergleich: dieselbe Leistung bei mehreren Veredlern für eine Menge,
 * der günstigste zuerst.
 */
@Service
@RequiredArgsConstructor
public class FremdleistungService {

    private static final BigDecimal MAX_GELD = new BigDecimal("1000000");
    private static final BigDecimal MAX_MENGE = new BigDecimal("1000000");

    private final FremdleistungRepository fremdleistungen;
    private final LieferantenRepository lieferanten;

    @Transactional(readOnly = true)
    public List<FremdleistungDto> alle(Long lieferantId) {
        List<Fremdleistung> liste = lieferantId == null ? fremdleistungen.findAllByOrderByBezeichnungAscIdAsc() : fremdleistungen.findByLieferantIdOrderByBezeichnungAscIdAsc(lieferantId);
        return liste.stream().map(this::dto).toList();
    }

    @Transactional
    public FremdleistungDto anlegen(FremdleistungEingabeDto e) {
        Fremdleistung f = new Fremdleistung();
        uebernehmen(f, e);
        return dto(fremdleistungen.save(f));
    }

    @Transactional
    public FremdleistungDto aendern(Long id, FremdleistungEingabeDto e) {
        Fremdleistung f = finde(id);
        uebernehmen(f, e);
        return dto(fremdleistungen.save(f));
    }

    /** Löscht eine Fremdleistung, die in keiner Kalkulation vorkommt; sonst bitte ausschalten (die Kalkulationen rechnen weiter mit ihr). */
    @Transactional
    public void loeschen(Long id) {
        Fremdleistung f = finde(id);
        long n = fremdleistungen.zaehleVerwendung(id);
        if (n > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "„" + f.getBezeichnung() + "“ kommt in " + n + (n == 1 ? " Kalkulationszeile" : " Kalkulationszeilen")
                    + " vor und lässt sich nicht löschen. Bitte stattdessen ausschalten.");
        }
        fremdleistungen.delete(f);
    }

    /**
     * Vergleicht alle eingeschalteten Fremdleistungen mit dieser Bezeichnung für die Menge (in der jeweiligen Abrechnungseinheit; haben die Veredler
     * verschiedene Einheiten, gilt die Menge für jede). Der günstigste ist markiert.
     */
    @Transactional(readOnly = true)
    public List<FremdleistungVergleichDto> vergleich(String bezeichnung, BigDecimal menge, BigDecimal gewichtKg) {
        if (bezeichnung == null || bezeichnung.isBlank()) {
            throw fehler("Bitte angeben, was verglichen werden soll.");
        }
        if (menge == null || menge.signum() <= 0 || menge.compareTo(MAX_MENGE) > 0) {
            throw fehler("Die Menge muss größer als 0 sein.");
        }
        List<FremdleistungVergleichDto> zeilen = fremdleistungen.findeAktiveMitBezeichnung(bezeichnung.strip()).stream().map(f -> {
            FremdleistungsRechner.Ergebnis r = FremdleistungsRechner.rechne(f, menge, gewichtKg);
            return new FremdleistungVergleichDto(f.getId(), f.getLieferant().getId(), f.getLieferant().getLieferantenname(), f.getBezeichnung(), f.getEinheit().name(), r.summe(),
                    r.summe().divide(menge, 4, RoundingMode.HALF_UP), r.gewichtFehlt(), false,
                    r.kostenarten().stream().map(k -> new FremdleistungVergleichDto.Kostenart(k.art(), k.text(), k.betrag())).toList());
        }).sorted(Comparator.comparing(FremdleistungVergleichDto::gewichtFehlt).thenComparing(FremdleistungVergleichDto::summe)).toList();
        if (zeilen.isEmpty()) {
            return zeilen;
        }
        // Der günstigste unter denen mit vollständigen Zahlen (ohne bekanntes Gewicht wäre die Summe zu niedrig)
        FremdleistungVergleichDto erster = zeilen.get(0);
        if (erster.gewichtFehlt()) {
            return zeilen;
        }
        return java.util.stream.IntStream.range(0, zeilen.size()).mapToObj(i -> {
            FremdleistungVergleichDto z = zeilen.get(i);
            return i == 0 ? new FremdleistungVergleichDto(z.fremdleistungId(), z.lieferantId(), z.lieferantName(), z.bezeichnung(), z.einheit(), z.summe(), z.summeJeEinheit(),
                    z.gewichtFehlt(), true, z.kostenarten()) : z;
        }).toList();
    }

    private void uebernehmen(Fremdleistung f, FremdleistungEingabeDto e) {
        f.setLieferant(lieferanten.findById(e.lieferantId()).orElseThrow(() -> fehler("Der Veredler (Lieferant) wurde nicht gefunden.")));
        f.setBezeichnung(e.bezeichnung().strip());
        try {
            f.setEinheit(Einheit.valueOf(e.einheit().strip().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            throw fehler("Die Abrechnungseinheit muss kg, m², m oder Stück sein.");
        }
        f.setPreisJeEinheit(geld(e.preisJeEinheit(), "Der Preis je Einheit", 4));
        f.setMindestpreis(geld(e.mindestpreis(), "Der Mindestpreis", 2));
        f.setEinmalBetrag(geld(e.einmalBetrag(), "Der einmalige Betrag", 2));
        f.setAnteilBetrag(geld(e.anteilBetrag(), "Der anteilige Betrag", 2));
        f.setAnteilChargenmenge(e.anteilChargenmenge() == null ? null : menge(e.anteilChargenmenge(), "Die Chargenmenge"));
        if (f.getAnteilBetrag() != null && f.getAnteilChargenmenge() == null) {
            throw fehler("Für anteilige Kosten bitte angeben, wie viele " + f.getEinheit().kurz() + " eine Charge fasst.");
        }
        BigDecimal prozent = e.prozentZuschlag();
        if (prozent != null && (prozent.signum() < 0 || prozent.compareTo(new BigDecimal("1000")) > 0)) {
            throw fehler("Der Prozentzuschlag muss zwischen 0 und 1000 liegen.");
        }
        f.setProzentZuschlag(prozent == null || prozent.signum() == 0 ? null : prozent.setScale(2, RoundingMode.HALF_UP));
        TransportArt art;
        try {
            art = e.transportArt() == null || e.transportArt().isBlank() ? TransportArt.KEINE : TransportArt.valueOf(e.transportArt().strip().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw fehler("Die Transportart ist unbekannt.");
        }
        f.setTransportArt(art);
        f.setTransportBetrag(art == TransportArt.KEINE ? null : geld(e.transportBetrag(), "Der Transportbetrag", 4));
        if (art != TransportArt.KEINE && f.getTransportBetrag() == null) {
            throw fehler("Bitte den Transportbetrag angeben.");
        }
        f.setTransportKm(art == TransportArt.JE_KM ? e.transportKm() == null ? null : menge(e.transportKm(), "Die Strecke") : null);
        if (art == TransportArt.JE_KM && f.getTransportKm() == null) {
            throw fehler("Bitte die Strecke in km angeben.");
        }
        int fahrten = e.transportFahrten() == null ? 1 : e.transportFahrten();
        if (fahrten < 1 || fahrten > 100) {
            throw fehler("Die Zahl der Fahrten liegt zwischen 1 und 100.");
        }
        f.setTransportFahrten(fahrten);
        f.setSonstigeBetrag(geld(e.sonstigeBetrag(), "Der Betrag der sonstigen Kosten", 2));
        f.setSonstigeBezeichnung(f.getSonstigeBetrag() == null || e.sonstigeBezeichnung() == null || e.sonstigeBezeichnung().isBlank() ? null : e.sonstigeBezeichnung().strip());
        f.setGueltigAb(e.gueltigAb() == null ? LocalDate.now() : e.gueltigAb());
        f.setAktiv(e.aktiv() == null || e.aktiv());
        boolean irgendeineKostenart = f.getPreisJeEinheit() != null || f.getMindestpreis() != null || f.getEinmalBetrag() != null || f.getAnteilBetrag() != null
                || (art != TransportArt.KEINE) || f.getSonstigeBetrag() != null;
        if (!irgendeineKostenart) {
            throw fehler("Bitte mindestens eine Kostenart angeben (zum Beispiel den Preis je Einheit).");
        }
    }

    private static BigDecimal geld(BigDecimal wert, String name, int stellen) {
        if (wert == null) {
            return null;
        }
        if (wert.signum() < 0 || wert.compareTo(MAX_GELD) > 0) {
            throw fehler(name + " muss zwischen 0 und 1.000.000 liegen.");
        }
        return wert.setScale(stellen, RoundingMode.HALF_UP);
    }

    private static BigDecimal menge(BigDecimal wert, String name) {
        if (wert.signum() <= 0 || wert.compareTo(MAX_MENGE) > 0) {
            throw fehler(name + " muss größer als 0 sein.");
        }
        return wert.setScale(3, RoundingMode.HALF_UP);
    }

    private Fremdleistung finde(Long id) {
        return fremdleistungen.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Die Fremdleistung wurde nicht gefunden."));
    }

    private FremdleistungDto dto(Fremdleistung f) {
        return new FremdleistungDto(f.getId(), f.getLieferant().getId(), f.getLieferant().getLieferantenname(), f.getBezeichnung(), f.getEinheit().name(), f.getPreisJeEinheit(),
                f.getMindestpreis(), f.getEinmalBetrag(), f.getAnteilBetrag(), f.getAnteilChargenmenge(), f.getProzentZuschlag(), f.getTransportArt().name(), f.getTransportBetrag(),
                f.getTransportKm(), f.getTransportFahrten(), f.getSonstigeBezeichnung(), f.getSonstigeBetrag(), f.getGueltigAb(), f.isAktiv(),
                f.getId() == null ? 0 : fremdleistungen.zaehleVerwendung(f.getId()));
    }

    private static ResponseStatusException fehler(String text) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, text);
    }
}
