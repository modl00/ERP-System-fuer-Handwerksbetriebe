package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.example.kalkulationsprogramm.domain.ArtikelInProjekt;
import org.example.kalkulationsprogramm.dto.Artikel.ArtikelInProjektResponseDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBestandDto;
import org.example.kalkulationsprogramm.dto.Lager.LagerBuchungEingabe;
import org.example.kalkulationsprogramm.mapper.ProjektMapper;
import org.example.kalkulationsprogramm.repository.ArtikelInProjektRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Bedarfsliste im Projekt: Aus-Lager-Buchung, Reservierung und Lagerbestand-Abfrage
 * für einzelne ArtikelInProjekt-Positionen.
 */
@Service
@RequiredArgsConstructor
public class ProjektMaterialService {

    private final ArtikelInProjektRepository repo;
    private final LagerService lagerService;
    private final ProjektMapper mapper;

    // ── Aus-Lager-Buchung ────────────────────────────────────────────────────

    /**
     * Bucht den Artikel einer Position direkt aus dem Lager und markiert sie als
     * erledigt (ausLager = true, bestellt = true).
     *
     * @param positionId ArtikelInProjekt-ID
     * @param lagerortId Lagerort, von dem entnommen wird
     * @param menge      entnommene Menge (muss > 0 sein)
     */
    @Transactional
    public ArtikelInProjektResponseDto ausLagerBuchen(Long positionId, Long lagerortId, BigDecimal menge) {
        ArtikelInProjekt pos = holePosition(positionId);
        if (pos.isAusLager()) {
            throw fehler("Diese Position wurde bereits aus dem Lager entnommen.");
        }
        Long artikelId = pos.getArtikel().getId();
        Long projektId = pos.getProjekt().getId();

        lagerService.buche(new LagerBuchungEingabe("ABGANG", artikelId, lagerortId, menge, projektId, null));

        pos.setAusLager(true);
        pos.setBestellt(true);
        pos.setReserviert(false);
        pos.setReserviertAm(null);
        pos.setReserviertMenge(null);
        repo.save(pos);
        return mapper.toArtikelInProjektDto(pos);
    }

    // ── Reservierung ─────────────────────────────────────────────────────────

    /**
     * Reserviert eine Menge im Lager für diese Position (logische Markierung,
     * keine physische Buchung).
     */
    @Transactional
    public ArtikelInProjektResponseDto reservieren(Long positionId, BigDecimal menge) {
        ArtikelInProjekt pos = holePosition(positionId);
        if (pos.isAusLager()) {
            throw fehler("Diese Position wurde bereits aus dem Lager entnommen – Reservierung nicht mehr nötig.");
        }
        pos.setReserviert(true);
        pos.setReserviertAm(LocalDateTime.now());
        pos.setReserviertMenge(menge);
        repo.save(pos);
        return mapper.toArtikelInProjektDto(pos);
    }

    /** Hebt eine bestehende Reservierung auf. */
    @Transactional
    public ArtikelInProjektResponseDto reservierungAufheben(Long positionId) {
        ArtikelInProjekt pos = holePosition(positionId);
        pos.setReserviert(false);
        pos.setReserviertAm(null);
        pos.setReserviertMenge(null);
        repo.save(pos);
        return mapper.toArtikelInProjektDto(pos);
    }

    // ── Lagerbestand für alle Artikel des Projekts ───────────────────────────

    /**
     * Liefert den aktuellen Lagerbestand für alle Artikel, die im Projekt
     * als ArtikelInProjekt geführt werden.
     */
    @Transactional(readOnly = true)
    public List<LagerBestandDto> lagerbestandFuerProjekt(Long projektId) {
        List<ArtikelInProjekt> positionen = repo.findByProjekt_Id(projektId);
        List<Long> artikelIds = positionen.stream()
                .filter(p -> p.getArtikel() != null)
                .map(p -> p.getArtikel().getId())
                .distinct()
                .toList();
        return lagerService.bestandFuer(artikelIds);
    }

    // ── private ──────────────────────────────────────────────────────────────

    private ArtikelInProjekt holePosition(Long id) {
        return repo.findById(id)
                .orElseThrow(() -> fehler("Die Materialposition existiert nicht."));
    }

    private static ResponseStatusException fehler(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
