package org.example.kalkulationsprogramm.service;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Programmfarbe der Firma. Gespeichert wird nur die Hauptfarbe (Stufe 600) als {@code #rrggbb}; die übrigen Stufen
 * (hell bis dunkel) berechnet die Oberfläche daraus. Ohne Eintrag bleibt das mitgelieferte Rosé, damit sich nach dem
 * Update nichts ändert.
 */
@Service
@AllArgsConstructor
public class DarstellungService {

    public static final String SCHLUESSEL_FARBE = "darstellung.farbe";

    /** Genau sechs Hexziffern mit Raute, keine Wiederholungen, damit kein Backtracking möglich ist. */
    private static final Pattern FARBE = Pattern.compile("#[0-9a-fA-F]{6}");

    private final SystemSettingRepository repository;

    /** Die gewählte Farbe in Kleinbuchstaben oder leer, wenn das Standard-Rosé gilt. */
    @Transactional(readOnly = true)
    public Optional<String> farbe() {
        return repository.findById(SCHLUESSEL_FARBE)
                .map(SystemSetting::getValue)
                .filter(wert -> wert != null && FARBE.matcher(wert).matches());
    }

    /**
     * Setzt die Programmfarbe. {@code null} oder leer stellt das Standard-Rosé wieder her.
     *
     * @throws IllegalArgumentException wenn der Wert keine Farbe wie {@code #e11d48} ist
     */
    @Transactional
    public Optional<String> setzeFarbe(String farbe) {
        if (farbe == null || farbe.isBlank()) {
            repository.findById(SCHLUESSEL_FARBE).ifPresent(repository::delete);
            return Optional.empty();
        }
        String bereinigt = farbe.trim();
        if (bereinigt.length() != 7 || !FARBE.matcher(bereinigt).matches()) {
            throw new IllegalArgumentException("Bitte eine Farbe wie #e11d48 angeben (Raute und sechs Zeichen 0-9, a-f).");
        }
        SystemSetting einstellung = repository.findById(SCHLUESSEL_FARBE).orElseGet(SystemSetting::new);
        einstellung.setKey(SCHLUESSEL_FARBE);
        einstellung.setValue(bereinigt.toLowerCase(Locale.ROOT));
        repository.save(einstellung);
        return Optional.of(einstellung.getValue());
    }
}
