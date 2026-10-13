package org.example.kalkulationsprogramm.service;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.repository.WerkstoffRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/** Vorschau für den Profilrechner: rechnet mit den Maßen aus der Eingabemaske, ohne etwas zu speichern. Die Dichte kommt vom gewählten Werkstoff. */
@Service
@RequiredArgsConstructor
public class ProfilBerechnungService {

    private final ProfilRechner rechner;
    private final WerkstoffRepository werkstoffe;

    @Transactional(readOnly = true)
    public ProfilRechner.Ergebnis vorschau(Profilform form, ProfilRechner.Masse masse, Long werkstoffId) {
        BigDecimal dichte = null;
        if (werkstoffId != null) {
            dichte = werkstoffe.findById(werkstoffId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Den gewählten Werkstoff gibt es nicht."))
                    .getDichte();
        }
        return rechner.berechne(form, masse, dichte);
    }
}
