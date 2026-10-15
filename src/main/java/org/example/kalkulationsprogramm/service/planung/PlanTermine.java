package org.example.kalkulationsprogramm.service.planung;

import java.time.LocalDate;
import java.time.LocalTime;

import org.example.kalkulationsprogramm.domain.KalenderEintrag;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.KalenderEintragRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Plantafel (#323): Ein Kalendertermin wird in der Ansicht mit der Maus verschoben. Geändert werden nur Tag, Uhrzeit und (bei Bedarf) der Mitarbeiter, bei dem er steht;
 * Titel, Beschreibung, Projekt und Kunde bleiben unberührt.
 */
@Service
@RequiredArgsConstructor
public class PlanTermine {

    private final KalenderEintragRepository kalender;
    private final MitarbeiterRepository mitarbeiter;

    /**
     * @param von Zeit-Beginn; bei ganztägigen Terminen ohne Wirkung
     * @param vonMitarbeiterId wenn gesetzt, ersetzt {@code nachMitarbeiterId} diesen Teilnehmer (der Termin wandert in die Zeile eines anderen Mitarbeiters)
     */
    @Transactional
    public void verschiebe(Long id, LocalDate datum, LocalTime von, LocalTime bis, Long vonMitarbeiterId, Long nachMitarbeiterId) {
        if (datum == null) {
            throw new IllegalArgumentException("Bitte einen Tag angeben.");
        }
        KalenderEintrag k = kalender.findById(id).orElseThrow(() -> new IllegalArgumentException("Der Termin wurde nicht gefunden."));
        if (!k.isGanztaegig() && von != null) {
            if (bis != null && !bis.isAfter(von)) {
                throw new IllegalArgumentException("Das Ende muss nach dem Beginn liegen.");
            }
            k.setStartZeit(von);
            k.setEndeZeit(bis);
        }
        if (k.getDatumBis() != null && k.getDatum() != null) {
            // ein mehrtägiger Termin behält seine Länge
            k.setDatumBis(k.getDatumBis().plusDays(java.time.temporal.ChronoUnit.DAYS.between(k.getDatum(), datum)));
        }
        k.setDatum(datum);
        if (nachMitarbeiterId != null && !nachMitarbeiterId.equals(vonMitarbeiterId)) {
            Mitarbeiter neu = mitarbeiter.findById(nachMitarbeiterId).filter(m -> !Boolean.FALSE.equals(m.getAktiv()))
                    .orElseThrow(() -> new IllegalArgumentException("Der Mitarbeiter wurde nicht gefunden."));
            if (vonMitarbeiterId != null) {
                k.getTeilnehmer().removeIf(m -> vonMitarbeiterId.equals(m.getId()));
            }
            k.getTeilnehmer().add(neu);
        }
        kalender.save(k);
    }
}
