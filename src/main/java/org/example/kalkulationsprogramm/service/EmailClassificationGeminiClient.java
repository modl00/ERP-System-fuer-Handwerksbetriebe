package org.example.kalkulationsprogramm.service;

import java.io.IOException;
import java.util.List;

import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Nutzer;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Client für die E-Mail-Klassifizierung über den konfigurierten KI-Anbieter (EMAIL_EINORDNEN).
 * Welcher Anbieter (Gemini, Claude, Mistral, lokal) zum Einsatz kommt, bestimmt die Nutzer-Einstellung.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailClassificationGeminiClient {

    private final KiAnbieterService kiAnbieterService;

    public boolean isEnabled() {
        return kiAnbieterService.irgendeinAnbieterEingerichtet();
    }

    /**
     * Sendet eine Klassifizierungs-Anfrage an den konfigurierten KI-Anbieter.
     *
     * @param systemPrompt System-Anweisungen (Rollenbeschreibung, Output-Format)
     * @param userMessage  Benutzer-Inhalt (eingehende E-Mail + Kandidaten)
     * @return Roher Antwort-Text (typischerweise JSON)
     */
    public String chat(String systemPrompt, String userMessage) throws IOException, InterruptedException {
        try {
            KiAnfrage anfrage = new KiAnfrage(systemPrompt, List.of(new Nutzer(userMessage)), List.of(), 0.1, 500);
            return kiAnbieterService.waehle(KiFunktion.EMAIL_EINORDNEN).chat(anfrage).text();
        } catch (KiAnbieterFehler e) {
            throw new IOException("KI nicht eingerichtet: " + e.getMessage());
        }
    }
}
