package org.example.kalkulationsprogramm.controller;

import java.util.Map;

import org.example.kalkulationsprogramm.service.PostfachSpiegelService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** Schalter „Änderungen im Postfach nachführen“. Unter {@code /api/settings/**}: nur Administratoren. */
@RestController
@RequestMapping("/api/settings/email/postfach-spiegeln")
@RequiredArgsConstructor
public class PostfachSpiegelController {

    private final PostfachSpiegelService spiegel;

    public record Schalter(boolean aktiv) { }

    @GetMapping
    public Map<String, Boolean> status() {
        return Map.of("aktiv", spiegel.istAktiv());
    }

    @PutMapping
    public Map<String, Boolean> schalten(@RequestBody Schalter s) {
        return Map.of("aktiv", spiegel.setzeAktiv(s != null && s.aktiv()));
    }
}
