package org.example.kalkulationsprogramm.service.ki;

import java.util.Optional;

/** Die KI-Anbieter, zwischen denen je Funktion gewählt werden kann. {@code lokal}: Die Daten verlassen das eigene Netz nicht. */
public enum AnbieterTyp {

    GEMINI("Gemini (Google)", false),
    LOKAL("Lokale KI (Ollama u. a.)", true),
    CLAUDE("Claude (Anthropic)", false),
    MISTRAL("Mistral", false);

    private final String anzeige;
    private final boolean lokal;

    AnbieterTyp(String anzeige, boolean lokal) {
        this.anzeige = anzeige;
        this.lokal = lokal;
    }

    public String anzeige() {
        return anzeige;
    }

    public boolean lokal() {
        return lokal;
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
