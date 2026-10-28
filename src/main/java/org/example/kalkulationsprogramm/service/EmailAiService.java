package org.example.kalkulationsprogramm.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.kalkulationsprogramm.service.ki.KiFunktion;
import org.example.kalkulationsprogramm.service.ki.KiTextWeiche;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;


@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAiService {

    private static final String BASE_SYSTEM_PROMPT =
            "Du bist ein Assistent, der E-Mails verbessert. Deine Aufgabe ist es, Rechtschreibung und Grammatik zu korrigieren.\\n" +
            "WICHTIG: Behalte den ursprünglichen Schreibstil und Tonfall des Nutzers UNBEDINGT bei!\\n" +
            "- Wenn der Nutzer 'Du' schreibt, bleibe beim 'Du'.\\n" +
            "- Wenn der Nutzer formell 'Sie' schreibt, bleibe formell.\\n" +
            "- Wenn der Text salopp/kurz ist (z.B. 'Passt morgen?'), verbessere nur die Fehler, aber mache daraus keinen Roman.\\n" +
            "- Der Text darf NICHT nach einer künstlichen Intelligenz klingen.\\n" +
            "- Strukturiere den Text mit HTML-Absätzen (<p>), um die Lesbarkeit zu verbessern.\\n" +
            "Entferne Signaturen, Grußformeln und den Namen des Absenders am Ende vollständig (z.B. 'LG Max', 'Max Mustermann', 'Mit freundlichen Grüßen'), da diese vom Programm automatisch angefügt werden. Der Text soll ohne Schlussformel enden.\\n" +
            "Antworte AUSSCHLIESSLICH mit einem JSON-Objekt im Format { \"email\": \"...\" }, wobei der Wert der verbesserte E-Mail-Text als HTML (ohne <html>/<body> Tags, nur <p>...) ist.";

    private final ObjectMapper objectMapper;
    private final KiTextWeiche weiche;

    @Value("${ai.email.temperature:0.2}")
    private double temperature;

    @Value("${ai.email.enabled:true}")
    private boolean enabled;

    public String beautify(String originalText) throws IOException, InterruptedException {
        return beautify(originalText, null);
    }

    public String beautify(String originalText, String replyContext) throws IOException, InterruptedException {
        if (!enabled || originalText == null || originalText.isBlank()) {
            return originalText;
        }

        String trimmed = normalizeLineEndings(originalText).trim();
        if (trimmed.isEmpty()) return "";

        String normalizedContext = replyContext == null ? "" : normalizeLineEndings(replyContext).trim();

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Original-Text:\n").append(trimmed).append("\n");
        if (!normalizedContext.isEmpty()) {
            userPrompt.append("\nKontext (Antwort auf vorherige E-Mail):\n").append(normalizedContext).append("\n");
            userPrompt.append("\nHinweis: Beziehe dich auf den Kontext nur, um den Sinn zu verstehen. Verändere nicht den Stil des Original-Textes deswegen.\n");
        }
        userPrompt.append("\nOptimiere diesen Entwurf (Grammatik/Rechtschreibung), aber behalte den Stil bei.");

        String jsonResponse = weiche.frage(KiFunktion.EMAIL_TEXT, BASE_SYSTEM_PROMPT, userPrompt.toString(), temperature, 2000, true);
        if (jsonResponse == null || jsonResponse.isBlank()) {
            throw new IOException("Keine Antwort von KI erhalten");
        }

        try {
            JsonNode contentNode = objectMapper.readTree(jsonResponse);
            if (contentNode.has("email")) {
                return cleanResult(contentNode.get("email").asText());
            } else if (contentNode.has("text")) {
                return cleanResult(contentNode.get("text").asText());
            } else {
                return cleanResult(jsonResponse);
            }
        } catch (Exception e) {
            log.warn("Konnte KI-Antwort nicht als JSON parsen, verwende Roh-Antwort: {}", e.getClass().getSimpleName());
            return cleanResult(jsonResponse);
        }
    }

    private String cleanResult(String text) {
        if (text == null) return "";
        String plain = org.example.kalkulationsprogramm.util.EmailHtmlSanitizer.htmlToPlainText(text);
        return org.example.kalkulationsprogramm.util.EmailAiPostProcessor.sanitizePlainText(plain).trim();
    }

    private String normalizeLineEndings(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace("\r", "\n");
    }
}
