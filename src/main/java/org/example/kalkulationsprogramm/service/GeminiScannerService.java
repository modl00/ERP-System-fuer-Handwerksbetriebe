package org.example.kalkulationsprogramm.service;

import java.util.List;

import org.example.kalkulationsprogramm.service.ki.KiAnbieterFehler;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Anhang;
import org.example.kalkulationsprogramm.service.ki.KiTypen.KiAnfrage;
import org.example.kalkulationsprogramm.service.ki.KiTypen.Nutzer;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiScannerService {

    private final KiAnbieterService kiAnbieterService;

    private static final String SYSTEM_PROMPT_FILENAME = """
            You are a smart file naming assistant.
            Analyze the document image and generate a concise, professional filename.

            Format: "{Type}_{Sender/Context}_{Date}"
            - Type: Rechnung, Vertrag, Brief, Notiz, etc. (German)
            - Sender: Company name or person if visible.
            - Date: YYYY-MM-DD (from the document) or TODAY if not found.

            Examples:
            - Rechnung_Amazon_2023-12-24
            - Brief_Finanzamt_2024-01-05
            - Notiz_Meeting_2024-03-10

            Rules:
            1. Return ONLY the filename string.
            2. No file extension.
            3. No spaces (use underscores).
            4. If text is illegible, return "Scan_{Timestamp}".
            """;

    public String generateFilename(byte[] imageBytes) {
        try {
            KiAnfrage anfrage = new KiAnfrage(
                    SYSTEM_PROMPT_FILENAME,
                    List.of(new Nutzer("Generate a filename.", List.of(new Anhang("image/jpeg", imageBytes)))),
                    List.of(), 0.0, 200);
            String text = kiAnbieterService.waehle(KiFunktion.SCANNER).chat(anfrage).text().trim();

            text = text.replace(" ", "_").replaceAll("[^a-zA-Z0-9_\\-]", "");
            return text.isEmpty() ? "Scan_" + System.currentTimeMillis() : text;
        } catch (KiAnbieterFehler e) {
            log.warn("[Scanner] KI nicht eingerichtet: {}", e.getMessage());
            return "Scan_" + System.currentTimeMillis();
        } catch (Exception e) {
            log.error("[Scanner] Fehler beim Dateinamen: {}", e.getClass().getSimpleName());
            return "Scan_" + System.currentTimeMillis();
        }
    }
}
