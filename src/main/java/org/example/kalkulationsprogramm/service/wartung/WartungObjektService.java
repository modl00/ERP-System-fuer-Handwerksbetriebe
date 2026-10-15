package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.wartung.WartungModulAuswahl;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.wartung.WartungObjektDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungObjektSpeichernDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungTuerDto;
import org.example.kalkulationsprogramm.dto.wartung.WartungTuerSpeichernDto;
import org.example.kalkulationsprogramm.mapper.wartung.WartungMapper;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungObjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungProtokollRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungTuerRepository;
import org.example.kalkulationsprogramm.service.wartung.WartungModulAuswahlPruefer.GewaehltesModul;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Verwaltet Objekte (Gebäude/Standorte eines Kunden) und ihre Türen/Tore. Löschen ist immer weich:
 * Ein deaktiviertes Objekt oder eine deaktivierte Tür verschwindet aus den Auswahllisten, ihre
 * Wartungshistorie bleibt aber erhalten.
 */
@Service
@AllArgsConstructor
public class WartungObjektService {

    static final String KOPIE_ZUSATZ = " (Kopie)";
    private static final int MAX_BEZEICHNUNG = 200;

    private final WartungObjektRepository objektRepository;
    private final WartungTuerRepository tuerRepository;
    private final WartungProtokollRepository protokollRepository;
    private final KundeRepository kundeRepository;
    private final WartungModulAuswahlPruefer auswahlPruefer;
    private final WartungMapper mapper;

    // ── Objekte ─────────────────────────────────────────────────────────────

    /** Objekte eines Kunden mit ihren Türen. Ohne {@code mitInaktiven} nur aktive. */
    @Transactional(readOnly = true)
    public List<WartungObjektDto> fuerKunde(Long kundeId, boolean mitInaktiven) {
        if (!kundeRepository.existsById(kundeId)) {
            throw nichtGefunden("Der Kunde wurde nicht gefunden.");
        }
        List<WartungObjekt> objekte = objektRepository.findByKundeIdOrderByBezeichnungAscIdAsc(kundeId).stream()
                .filter(o -> mitInaktiven || o.isAktiv())
                .toList();
        if (objekte.isEmpty()) {
            return List.of();
        }
        Map<Long, List<WartungTuerDto>> tueren = tuerRepository
                .findByObjektIds(objekte.stream().map(WartungObjekt::getId).toList()).stream()
                .filter(t -> mitInaktiven || t.isAktiv())
                .collect(Collectors.groupingBy(t -> t.getObjekt().getId(),
                        Collectors.mapping(mapper::toDto, Collectors.toList())));
        return objekte.stream().map(o -> mapper.toDto(o, tueren.getOrDefault(o.getId(), List.of()))).toList();
    }

    @Transactional
    public WartungObjektDto objektAnlegen(Long kundeId, WartungObjektSpeichernDto dto) {
        Kunde kunde = kundeRepository.findById(kundeId).orElseThrow(() -> nichtGefunden("Der Kunde wurde nicht gefunden."));
        WartungObjekt objekt = new WartungObjekt();
        objekt.setKunde(kunde);
        uebernehmen(objekt, dto);
        return mapper.toDto(objektRepository.save(objekt), List.of());
    }

    @Transactional
    public WartungObjektDto objektAendern(Long objektId, WartungObjektSpeichernDto dto) {
        WartungObjekt objekt = ladeObjekt(objektId);
        uebernehmen(objekt, dto);
        objektRepository.save(objekt);
        return objektMitTueren(objekt);
    }

    @Transactional
    public WartungObjektDto objektAktivSetzen(Long objektId, boolean aktiv) {
        WartungObjekt objekt = ladeObjekt(objektId);
        objekt.setAktiv(aktiv);
        objektRepository.save(objekt);
        return objektMitTueren(objekt);
    }

    // ── Türen ───────────────────────────────────────────────────────────────

    @Transactional
    public WartungTuerDto tuerAnlegen(Long objektId, WartungTuerSpeichernDto dto) {
        WartungObjekt objekt = ladeObjekt(objektId);
        if (!objekt.isAktiv()) {
            throw falsch("Dieses Objekt ist nicht mehr aktiv. Bitte zuerst wieder aktivieren.");
        }
        WartungTuer tuer = new WartungTuer();
        tuer.setObjekt(objekt);
        uebernehmen(tuer, dto);
        return mapper.toDto(tuerRepository.save(tuer));
    }

    @Transactional
    public WartungTuerDto tuerAendern(Long tuerId, WartungTuerSpeichernDto dto) {
        WartungTuer tuer = ladeTuer(tuerId);
        uebernehmen(tuer, dto);
        return mapper.toDto(tuerRepository.save(tuer));
    }

    @Transactional
    public WartungTuerDto tuerAktivSetzen(Long tuerId, boolean aktiv) {
        WartungTuer tuer = ladeTuer(tuerId);
        tuer.setAktiv(aktiv);
        return mapper.toDto(tuerRepository.save(tuer));
    }

    /**
     * Hängt die Tür an ein anderes Objekt desselben Kunden. Ihre Protokolle wandern mit, denn das
     * Objekt steht auch direkt im Protokoll. Zwischen verschiedenen Kunden verschiebt man nicht.
     */
    @Transactional
    public WartungTuerDto tuerVerschieben(Long tuerId, Long zielObjektId) {
        WartungTuer tuer = ladeTuer(tuerId);
        WartungObjekt ziel = ladeObjekt(zielObjektId);
        if (!ziel.getKunde().getId().equals(tuer.getObjekt().getKunde().getId())) {
            throw falsch("Eine Tür lässt sich nur zu einem Objekt desselben Kunden verschieben.");
        }
        if (!ziel.isAktiv()) {
            throw falsch("Das Zielobjekt ist nicht mehr aktiv.");
        }
        tuer.setObjekt(ziel);
        tuerRepository.saveAndFlush(tuer);
        protokollRepository.setzeObjektFuerTuer(tuer.getId(), ziel);
        return mapper.toDto(ladeTuer(tuerId));
    }

    /** Legt eine Kopie der Tür im selben Objekt an, mit denselben Modulen, aber ohne Verlauf. */
    @Transactional
    public WartungTuerDto tuerKopieren(Long tuerId) {
        WartungTuer original = ladeTuer(tuerId);
        WartungTuer kopie = new WartungTuer();
        kopie.setObjekt(original.getObjekt());
        kopie.setBezeichnung(kopiename(original.getBezeichnung()));
        kopie.setNotizen(original.getNotizen());
        kopie.setIntervallMonate(original.getIntervallMonate());
        original.getModule().forEach(m -> kopie.getModule().add(new WartungModulAuswahl(m.getModulId(), m.getUntertypSchluessel())));
        return mapper.toDto(tuerRepository.save(kopie));
    }

    // ── Hilfen ──────────────────────────────────────────────────────────────

    private void uebernehmen(WartungObjekt objekt, WartungObjektSpeichernDto dto) {
        objekt.setBezeichnung(dto.bezeichnung().trim());
        objekt.setStrasse(leerZuNull(dto.strasse()));
        objekt.setPlz(leerZuNull(dto.plz()));
        objekt.setOrt(leerZuNull(dto.ort()));
        objekt.setAnsprechpartnerName(leerZuNull(dto.ansprechpartnerName()));
        objekt.setAnsprechpartnerTelefon(leerZuNull(dto.ansprechpartnerTelefon()));
        objekt.setNotizen(leerZuNull(dto.notizen()));
    }

    private void uebernehmen(WartungTuer tuer, WartungTuerSpeichernDto dto) {
        // Erst prüfen, dann schreiben: Eine ungültige Modulauswahl darf die Tür nicht halb ändern.
        List<GewaehltesModul> module = auswahlPruefer.pruefe(dto.module(), false);
        tuer.setBezeichnung(dto.bezeichnung().trim());
        tuer.setNotizen(leerZuNull(dto.notizen()));
        tuer.setIntervallMonate(dto.intervallMonate());
        Map<Long, String> neu = new HashMap<>();
        module.forEach(g -> neu.put(g.modul().getId(), g.untertyp() == null ? null : g.untertyp().getSchluessel()));
        tuer.getModule().clear();
        neu.forEach((modulId, bauart) -> tuer.getModule().add(new WartungModulAuswahl(modulId, bauart)));
    }

    private WartungObjektDto objektMitTueren(WartungObjekt objekt) {
        List<WartungTuerDto> tueren = tuerRepository.findByObjektIdOrderByBezeichnungAscIdAsc(objekt.getId()).stream()
                .map(mapper::toDto).toList();
        return mapper.toDto(objekt, tueren);
    }

    private static String kopiename(String bezeichnung) {
        String kopie = bezeichnung + KOPIE_ZUSATZ;
        if (kopie.length() <= MAX_BEZEICHNUNG) {
            return kopie;
        }
        return bezeichnung.substring(0, MAX_BEZEICHNUNG - KOPIE_ZUSATZ.length()) + KOPIE_ZUSATZ;
    }

    private WartungObjekt ladeObjekt(Long id) {
        return objektRepository.findById(id).orElseThrow(() -> nichtGefunden("Das Objekt wurde nicht gefunden."));
    }

    private WartungTuer ladeTuer(Long id) {
        return tuerRepository.findById(id).orElseThrow(() -> nichtGefunden("Die Tür wurde nicht gefunden."));
    }

    private static String leerZuNull(String text) {
        if (text == null) {
            return null;
        }
        String getrimmt = text.trim();
        return getrimmt.isEmpty() ? null : getrimmt;
    }

    private static ResponseStatusException falsch(String meldung) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, meldung);
    }

    private static ResponseStatusException nichtGefunden(String meldung) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, meldung);
    }
}
