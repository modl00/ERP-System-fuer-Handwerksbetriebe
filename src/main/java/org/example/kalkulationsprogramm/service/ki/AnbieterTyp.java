package org.example.kalkulationsprogramm.service.ki;

import java.util.Optional;

/**
 * Die KI-Anbieter, zwischen denen je Funktion gewählt werden kann.
 * {@code lokal}: Die Daten verlassen das eigene Netz nicht.
 * {@code kannBild}: Der Anbieter kann Bilder (JPEG, PNG, GIF, WebP) verarbeiten.
 * {@code kannPdf}: Der Anbieter kann PDF-Dateien nativ als Dokument lesen (Gemini, Claude). Ohne PDF-Support bekommt der Anbieter die Seiten als Bilder –
 * Mistral liest PDFs über die separate OCR-API ({@code /v1/ocr}, {@link MistralOcrService}) und fällt sonst auf Pixtral-Bildanalyse zurück.
 */
public enum AnbieterTyp {

    GEMINI("Gemini (Google)", false, true, true),
    LOKAL("Lokale KI (Ollama u. a.)", true, true, false),
    CLAUDE("Claude (Anthropic)", false, true, true),
    MISTRAL("Mistral", false, true, false);

    private final String anzeige;
    private final boolean lokal;
    private final boolean kannBild;
    private final boolean kannPdf;

    AnbieterTyp(String anzeige, boolean lokal, boolean kannBild, boolean kannPdf) {
        this.anzeige = anzeige;
        this.lokal = lokal;
        this.kannBild = kannBild;
        this.kannPdf = kannPdf;
    }

    public String anzeige() {
        return anzeige;
    }

    public boolean lokal() {
        return lokal;
    }

    public boolean kannBild() {
        return kannBild;
    }

    public boolean kannPdf() {
        return kannPdf;
    }

    public static Optional<AnbieterTyp> ausName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        for (AnbieterTyp t : values()) {
            if (t.name().equalsIgnoreCase(name.strip())) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
