package org.example.kalkulationsprogramm.controller;

import java.math.BigDecimal;

import org.example.kalkulationsprogramm.domain.Profilform;
import org.example.kalkulationsprogramm.service.ProfilBerechnungService;
import org.example.kalkulationsprogramm.service.ProfilRechner;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Gewicht, Querschnitt und Oberfläche eines Profils aus seinen Maßen (nur Vorschau, nichts wird gespeichert). */
@RestController
@RequestMapping("/api/artikel/profilrechner")
@RequiredArgsConstructor
public class ProfilRechnerController {

    private final ProfilBerechnungService berechnung;

    public record Anfrage(Profilform profilform, BigDecimal durchmesser, BigDecimal hoehe, BigDecimal breite, BigDecimal wandstaerke, Long werkstoffId) { }

    public record Antwort(BigDecimal querschnittCm2, BigDecimal kgProMeter, BigDecimal kgProQm, BigDecimal mantelflaecheQm, String herkunft, String hinweis) { }

    @PostMapping
    public Antwort berechne(@RequestBody Anfrage a) {
        ProfilRechner.Ergebnis e = berechnung.vorschau(a.profilform(), new ProfilRechner.Masse(a.durchmesser(), a.hoehe(), a.breite(), a.wandstaerke()), a.werkstoffId());
        return new Antwort(e.querschnittCm2(), e.kgProMeter(), e.kgProQm(), e.mantelflaecheQm(), e.herkunft().name(), e.hinweis());
    }
}
