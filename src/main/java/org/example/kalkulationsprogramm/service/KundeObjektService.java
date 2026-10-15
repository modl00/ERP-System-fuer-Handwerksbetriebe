package org.example.kalkulationsprogramm.service;

import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Kunde;
import org.example.kalkulationsprogramm.domain.wartung.WartungObjekt;
import org.example.kalkulationsprogramm.domain.wartung.WartungTuer;
import org.example.kalkulationsprogramm.dto.Kunde.KundeObjektDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeObjektSpeichernDto;
import org.example.kalkulationsprogramm.repository.KundeRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungObjektRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungTuerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Objekte (Liegenschaften) im Kundenstamm. Es sind dieselben Datensätze wie in der Wartung ({@code wartung_objekt}),
 * damit ein Objekt nur einmal angelegt wird. Dieser Weg läuft bewusst nicht unter {@code /api/wartung}: Der Kundenstamm
 * funktioniert auch, wenn das Wartungsmodul ausgeschaltet ist. Löschen ist weich ({@code aktiv = false}), weil an einem
 * Objekt Wartungen mit Historie hängen können.
 */
@Service
@RequiredArgsConstructor
public class KundeObjektService {

    private final WartungObjektRepository objektRepository;
    private final WartungTuerRepository tuerRepository;
    private final KundeRepository kundeRepository;

    @Transactional(readOnly = true)
    public List<KundeObjektDto> liste(Long kundeId, boolean mitInaktiven) {
        pruefeKunde(kundeId);
        List<WartungObjekt> objekte = objektRepository.findByKundeIdOrderByBezeichnungAscIdAsc(kundeId).stream()
                .filter(o -> mitInaktiven || o.isAktiv())
                .toList();
        if (objekte.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> wartungen = tuerRepository.findByObjektIds(objekte.stream().map(WartungObjekt::getId).toList()).stream()
                .filter(WartungTuer::isAktiv)
                .collect(Collectors.groupingBy(t -> t.getObjekt().getId(), Collectors.counting()));
        return objekte.stream().map(o -> alsDto(o, wartungen.getOrDefault(o.getId(), 0L).intValue())).toList();
    }

    @Transactional(readOnly = true)
    public long anzahl(Long kundeId) {
        return objektRepository.findByKundeIdOrderByBezeichnungAscIdAsc(kundeId).stream().filter(WartungObjekt::isAktiv).count();
    }

    @Transactional
    public KundeObjektDto anlegen(Long kundeId, KundeObjektSpeichernDto dto) {
        Kunde kunde = kundeRepository.findById(kundeId).orElseThrow(KundeObjektService::kundeFehlt);
        WartungObjekt objekt = new WartungObjekt();
        objekt.setKunde(kunde);
        uebernehmen(objekt, dto);
        return alsDto(objektRepository.save(objekt), 0);
    }

    @Transactional
    public KundeObjektDto aendern(Long kundeId, Long objektId, KundeObjektSpeichernDto dto) {
        WartungObjekt objekt = lade(kundeId, objektId);
        uebernehmen(objekt, dto);
        objektRepository.save(objekt);
        return mitAnzahl(objekt);
    }

    @Transactional
    public KundeObjektDto aktivSetzen(Long kundeId, Long objektId, boolean aktiv) {
        WartungObjekt objekt = lade(kundeId, objektId);
        objekt.setAktiv(aktiv);
        objektRepository.save(objekt);
        return mitAnzahl(objekt);
    }

    // ── intern ──────────────────────────────────────────────────────────────

    private KundeObjektDto mitAnzahl(WartungObjekt objekt) {
        int n = (int) tuerRepository.findByObjektIds(List.of(objekt.getId())).stream().filter(WartungTuer::isAktiv).count();
        return alsDto(objekt, n);
    }

    private void pruefeKunde(Long kundeId) {
        if (!kundeRepository.existsById(kundeId)) {
            throw kundeFehlt();
        }
    }

    /** Das Objekt muss zum Kunden aus der Adresse gehören: Ein fremdes Objekt ist hier "nicht gefunden". */
    private WartungObjekt lade(Long kundeId, Long objektId) {
        return objektRepository.findById(objektId)
                .filter(o -> o.getKunde().getId().equals(kundeId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Das Objekt wurde nicht gefunden."));
    }

    private static ResponseStatusException kundeFehlt() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Kunde wurde nicht gefunden.");
    }

    private static void uebernehmen(WartungObjekt o, KundeObjektSpeichernDto dto) {
        o.setBezeichnung(dto.bezeichnung().trim());
        o.setStrasse(leerZuNull(dto.strasse()));
        o.setPlz(leerZuNull(dto.plz()));
        o.setOrt(leerZuNull(dto.ort()));
        o.setAnsprechpartnerName(leerZuNull(dto.ansprechpartnerName()));
        o.setAnsprechpartnerTelefon(leerZuNull(dto.ansprechpartnerTelefon()));
        o.setNotizen(leerZuNull(dto.notizen()));
    }

    private static KundeObjektDto alsDto(WartungObjekt o, int anzahlWartungen) {
        return new KundeObjektDto(o.getId(), o.getBezeichnung(), o.getStrasse(), o.getPlz(), o.getOrt(),
                o.getAnsprechpartnerName(), o.getAnsprechpartnerTelefon(), o.getNotizen(), o.isAktiv(), anzahlWartungen);
    }

    private static String leerZuNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
