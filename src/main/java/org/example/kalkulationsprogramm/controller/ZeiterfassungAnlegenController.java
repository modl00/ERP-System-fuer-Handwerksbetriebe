package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.example.kalkulationsprogramm.dto.Anfrage.AnfrageResponseDto;
import org.example.kalkulationsprogramm.dto.Kunde.KundeCreateRequestDto;
import org.example.kalkulationsprogramm.service.AppAnlegenService;
import org.example.kalkulationsprogramm.service.AppAnlegenService.AnfrageEingabe;
import org.example.kalkulationsprogramm.service.AppAnlegenService.KundeKurz;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Kunden und Anfragen in der Handy-App anlegen (Issue #117). Der Zugangslink steht im Text der Anfrage, nicht in der Adresse. Das Recht prüft der
 * {@link AppAnlegenService} bei jedem Aufruf. Bei möglichen Duplikaten antwortet der Server mit 409 und {@code code: DUPLIKAT}; die App zeigt die Treffer
 * und sendet bei „Trotzdem anlegen“ erneut mit {@code bestaetigt: true}.
 */
@RestController
@RequestMapping("/api/zeiterfassung/anlegen")
@RequiredArgsConstructor
public class ZeiterfassungAnlegenController {

    private final AppAnlegenService service;

    public record KundeAnfrage(String token, @Valid KundeCreateRequestDto kunde, Boolean bestaetigt) { }

    public record AnfrageAnfrage(String token, String bauvorhaben, Long kundenId, String projektStrasse, String projektPlz, String projektOrt, String kurzbeschreibung,
            BigDecimal betrag) { }

    @PostMapping("/kunden")
    public ResponseEntity<?> kunde(@Valid @RequestBody KundeAnfrage a) {
        try {
            KundeKurz k = service.legeKundeAn(a.token(), a.kunde(), Boolean.TRUE.equals(a.bestaetigt()));
            return ResponseEntity.status(HttpStatus.CREATED).body(k);
        } catch (KundeDuplikatException d) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("code", "DUPLIKAT");
            r.put("message", "Es gibt schon einen ähnlichen Kunden.");
            r.put("duplikate", d.getAntwort().getDuplikate() == null ? List.of() : d.getAntwort().getDuplikate());
            r.put("harterTreffer", d.getAntwort().isHarterTreffer());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(r);
        }
    }

    @PostMapping("/anfragen")
    public ResponseEntity<AnfrageResponseDto> anfrage(@RequestBody AnfrageAnfrage a) {
        AnfrageResponseDto neu = service.legeAnfrageAn(a.token(), new AnfrageEingabe(a.bauvorhaben(), a.kundenId(), a.projektStrasse(), a.projektPlz(), a.projektOrt(),
                a.kurzbeschreibung(), a.betrag()));
        return ResponseEntity.status(HttpStatus.CREATED).body(neu);
    }
}
