package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.wartung.WartungMitarbeiterUnterschrift;
import org.example.kalkulationsprogramm.dto.wartung.WartungGespeicherteUnterschriftDto;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.repository.wartung.WartungMitarbeiterUnterschriftRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Die einmal erfasste Unterschrift eines Mitarbeiters. Sie wird bei einem neuen Feldeinsatz ins Protokoll kopiert
 * (siehe {@link WartungProtokollService}); hier ändern wirkt deshalb nur auf <b>künftige</b> Protokolle.
 */
@Service
@AllArgsConstructor
public class WartungMitarbeiterUnterschriftService {

    private final WartungMitarbeiterUnterschriftRepository repository;
    private final MitarbeiterRepository mitarbeiterRepository;

    @Transactional(readOnly = true)
    public Optional<WartungGespeicherteUnterschriftDto> finde(Long mitarbeiterId) {
        if (mitarbeiterId == null) {
            return Optional.empty();
        }
        return repository.findById(mitarbeiterId).map(u -> new WartungGespeicherteUnterschriftDto(u.getBild(), u.getName(), u.getGeaendertAm()));
    }

    @Transactional
    public WartungGespeicherteUnterschriftDto speichern(Long mitarbeiterId, String bild, String name) {
        if (mitarbeiterId == null || !mitarbeiterRepository.existsById(mitarbeiterId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ihr Benutzer ist keinem Mitarbeiter zugeordnet.");
        }
        WartungUnterschriftPruefer.pruefe(bild);
        WartungMitarbeiterUnterschrift unterschrift = repository.findById(mitarbeiterId).orElseGet(WartungMitarbeiterUnterschrift::new);
        unterschrift.setMitarbeiterId(mitarbeiterId);
        unterschrift.setBild(bild);
        unterschrift.setName(name == null || name.isBlank() ? null : name.trim());
        unterschrift.setGeaendertAm(LocalDateTime.now());
        repository.save(unterschrift);
        return new WartungGespeicherteUnterschriftDto(unterschrift.getBild(), unterschrift.getName(), unterschrift.getGeaendertAm());
    }

    @Transactional
    public void loeschen(Long mitarbeiterId) {
        if (mitarbeiterId != null && repository.existsById(mitarbeiterId)) {
            repository.deleteById(mitarbeiterId);
        }
    }
}
