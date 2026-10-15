package org.example.kalkulationsprogramm.service.lieferschein;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.lieferschein.Nummernreservierung;
import org.example.kalkulationsprogramm.domain.lieferschein.Nummernreservierung.Status;
import org.example.kalkulationsprogramm.dto.lieferschein.NummernbereichDto;
import org.example.kalkulationsprogramm.repository.lieferschein.NummernreservierungRepository;
import org.example.kalkulationsprogramm.service.NummernkreisService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Nummernbereiche je Mitarbeiter für das Arbeiten ohne Netz (Issue #128): Die App holt sich bei Verbindung eine Reihe Nummern, die der Server aus dem
 * Nummernkreis reserviert (nie parallel gezählt). Offline erstellte Lieferscheine bekommen daraus sofort ihre endgültige Nummer. Der Server schreibt jede
 * Reservierung mit Mitarbeiter und Zeit auf; was nicht verwendet wird, bleibt als „nicht verwendet“ erklärbar.
 */
@Service
@RequiredArgsConstructor
public class NummernbereichService {

    static final int MAX_BEREICH = 50;

    private final NummernreservierungRepository reservierungen;
    private final NummernkreisService nummernkreise;

    /** Füllt den Bereich des Mitarbeiters auf {@code soll} (höchstens {@value #MAX_BEREICH}) nicht verwendete Nummern auf und gibt alle zurück. */
    @Transactional
    public NummernbereichDto auffuellen(Mitarbeiter mitarbeiter, String art, int soll, Predicate<String> bereitsVergeben) {
        if (soll < 1 || soll > MAX_BEREICH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der Nummernbereich umfasst 1 bis " + MAX_BEREICH + " Nummern.");
        }
        List<Nummernreservierung> offen = new ArrayList<>(reservierungen.findByArtAndMitarbeiterIdAndStatusOrderByIdAsc(art, mitarbeiter.getId(), Status.RESERVIERT));
        for (int i = offen.size(); i < soll; i++) {
            String nummer = nummernkreise.naechsteNummer(art, n -> reservierungen.existsByArtAndNummer(art, n) || (bereitsVergeben != null && bereitsVergeben.test(n)))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Für diese Dokumentart gibt es keinen Nummernkreis."));
            Nummernreservierung r = new Nummernreservierung();
            r.setArt(art);
            r.setNummer(nummer);
            r.setMitarbeiter(mitarbeiter);
            r.setReserviertAm(LocalDateTime.now());
            offen.add(reservierungen.save(r));
        }
        return new NummernbereichDto(art, offen.stream().map(Nummernreservierung::getNummer).toList());
    }

    /** Ordnet eine reservierte Nummer einem Dokument zu. Sie muss dem Mitarbeiter gehören und noch frei sein. */
    @Transactional
    public void verwenden(Mitarbeiter mitarbeiter, String art, String nummer) {
        Nummernreservierung r = reservierungen.findeMitSperre(art, nummer)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Die Nummer " + nummer + " ist nicht für dieses Gerät reserviert. Bitte mit Verbindung neu laden."));
        if (!r.getMitarbeiter().getId().equals(mitarbeiter.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Nummer " + nummer + " gehört zu einem anderen Gerät.");
        }
        if (r.getStatus() == Status.VERWENDET) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die Nummer " + nummer + " ist schon verwendet.");
        }
        r.setStatus(Status.VERWENDET);
        r.setVerwendetAm(LocalDateTime.now());
        reservierungen.save(r);
    }

    /** Das Protokoll aller Reservierungen einer Art, neueste zuerst (für die Verwaltung: Lücken müssen erklärbar sein). */
    @Transactional(readOnly = true)
    public List<Eintrag> protokoll(String art) {
        return reservierungen.findByArtOrderByIdDesc(art).stream()
                .map(r -> new Eintrag(r.getNummer(), r.getMitarbeiter().getVorname() + " " + r.getMitarbeiter().getNachname(), r.getReserviertAm(), r.getStatus() == Status.VERWENDET ? "verwendet" : "nicht verwendet", r.getVerwendetAm()))
                .toList();
    }

    public record Eintrag(String nummer, String mitarbeiter, LocalDateTime reserviertAm, String status, LocalDateTime verwendetAm) { }
}
