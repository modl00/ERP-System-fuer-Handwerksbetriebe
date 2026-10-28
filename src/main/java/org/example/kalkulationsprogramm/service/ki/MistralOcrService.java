package org.example.kalkulationsprogramm.service.ki;

import java.time.Duration;
import java.util.Base64;
import java.util.Map;

import org.example.kalkulationsprogramm.service.SystemSettingsService;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OCR über die dedizierte Mistral-OCR-API ({@code POST /v1/ocr}, Modell {@code mistral-ocr-latest}).
 * Unterstützt PDFs und Bilder; gibt den extrahierten Text als Markdown zurück. Gibt {@code null} zurück
 * wenn Mistral nicht konfiguriert ist oder der Aufruf fehlschlägt – der Aufrufer kann dann auf den
 * bildbasierten Pfad (Pixtral) zurückfallen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MistralOcrService {

    static final String OCR_MODELL = "mistral-ocr-latest";
    private static final String ENDPUNKT = "https://api.mistral.ai/v1/ocr";
    private static final Duration FRIST = Duration.ofSeconds(90);

    private final SystemSettingsService einstellungen;

    /**
     * Extrahiert den Textinhalt eines Dokuments über die Mistral OCR API.
     * Unterstützt {@code application/pdf}, {@code image/jpeg}, {@code image/png}, {@code image/webp}.
     *
     * @return Markdown-Text aller Seiten, oder {@code null} bei nicht konfiguriertem Schlüssel / Fehler
     */
    @Nullable
    public String extrahiereText(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        String key = strip(einstellungen.get(KiAnbieterService.MISTRAL_SCHLUESSEL, ""));
        if (key.isEmpty()) {
            return null;
        }
        try {
            ObjectNode body = HttpHilfe.JSON.createObjectNode();
            body.put("model", OCR_MODELL);
            ObjectNode doc = body.putObject("document");
            String datenUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes);
            if ("application/pdf".equals(mimeType)) {
                doc.put("type", "document_url");
                doc.put("document_url", datenUri);
            } else {
                doc.put("type", "image_url");
                doc.put("image_url", datenUri);
            }
            JsonNode root = HttpHilfe.sende(HttpHilfe.STANDARD, ENDPUNKT,
                    Map.of("Authorization", "Bearer " + key), body, FRIST, "Mistral OCR", "");
            return seitenText(root);
        } catch (KiAnbieterFehler e) {
            log.debug("[MistralOCR] API-Fehler: {}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.debug("[MistralOCR] Fehler: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static String seitenText(JsonNode root) {
        var sb = new StringBuilder();
        for (JsonNode seite : root.path("pages")) {
            String text = seite.path("markdown").asText("").strip();
            if (!text.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n---\n\n");
                }
                sb.append(text);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String strip(String s) {
        return s == null ? "" : s.strip();
    }
}
