package org.example.kalkulationsprogramm.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Welche Menüpunkte im Hauptmenü ausgeblendet sind. Ausgeblendete Punkte bleiben über „Alle Funktionen“ erreichbar,
 * es wird nichts gesperrt und nichts gelöscht; Rechte und Module regeln weiterhin, was jemand überhaupt sehen darf.
 *
 * <p>Zwei Ebenen: die <b>Vorgabe</b> der Firma (vom Admin) und die <b>eigene Auswahl</b> je Benutzer. Die eigene Auswahl
 * hat Vorrang. Ohne gespeicherte Vorgabe gilt der Vorschlag, den die Oberfläche mitbringt. Gespeichert wird in
 * {@code system_setting} (so läuft es auch ohne Flyway, etwa unter H2), die Punkte als Adressen wie {@code /werkstoffe}.</p>
 */
@Service
@AllArgsConstructor
public class MenueService {

    public static final String SCHLUESSEL_VORGABE = "menue.vorgabe";
    private static final String SCHLUESSEL_BENUTZER = "menue.benutzer.";

    /** Höchstzahl der Einträge; das Menü hat rund 50 Punkte. */
    static final int MAX_EINTRAEGE = 200;

    /** Eine Adresse wie {@code /bestellungen/liste}: klein, Ziffern, Schrägstrich, Bindestrich; ohne Wiederholungsgruppen. */
    private static final Pattern ADRESSE = Pattern.compile("/[a-z0-9/_-]{1,80}");

    private final SystemSettingRepository repository;

    @Transactional(readOnly = true)
    public Optional<List<String>> vorgabe() {
        return lese(SCHLUESSEL_VORGABE);
    }

    @Transactional(readOnly = true)
    public Optional<List<String>> eigene(long benutzerId) {
        return lese(SCHLUESSEL_BENUTZER + benutzerId);
    }

    @Transactional
    public List<String> setzeVorgabe(List<String> ausgeblendet) {
        return schreibe(SCHLUESSEL_VORGABE, ausgeblendet);
    }

    @Transactional
    public List<String> setzeEigene(long benutzerId, List<String> ausgeblendet) {
        return schreibe(SCHLUESSEL_BENUTZER + benutzerId, ausgeblendet);
    }

    @Transactional
    public void loescheVorgabe() {
        repository.findById(SCHLUESSEL_VORGABE).ifPresent(repository::delete);
    }

    @Transactional
    public void loescheEigene(long benutzerId) {
        repository.findById(SCHLUESSEL_BENUTZER + benutzerId).ifPresent(repository::delete);
    }

    private Optional<List<String>> lese(String schluessel) {
        return repository.findById(schluessel).map(SystemSetting::getValue).map(MenueService::zerlege);
    }

    private List<String> schreibe(String schluessel, List<String> ausgeblendet) {
        List<String> sauber = pruefe(ausgeblendet);
        SystemSetting einstellung = repository.findById(schluessel).orElseGet(SystemSetting::new);
        einstellung.setKey(schluessel);
        einstellung.setValue(String.join(",", sauber));
        repository.save(einstellung);
        return sauber;
    }

    /**
     * Prüft die Liste und entfernt Doppelte; die Reihenfolge bleibt.
     *
     * @throws IllegalArgumentException bei fehlender Liste, zu vielen Einträgen oder einer ungültigen Adresse
     */
    static List<String> pruefe(List<String> ausgeblendet) {
        if (ausgeblendet == null) {
            throw new IllegalArgumentException("Bitte die ausgeblendeten Menüpunkte angeben.");
        }
        if (ausgeblendet.size() > MAX_EINTRAEGE) {
            throw new IllegalArgumentException("Es sind zu viele Menüpunkte ausgewählt.");
        }
        Set<String> ergebnis = new LinkedHashSet<>();
        for (String adresse : ausgeblendet) {
            if (adresse == null || !ADRESSE.matcher(adresse).matches()) {
                throw new IllegalArgumentException("Ungültiger Menüpunkt.");
            }
            ergebnis.add(adresse);
        }
        return List.copyOf(ergebnis);
    }

    private static List<String> zerlege(String wert) {
        if (wert == null || wert.isBlank()) {
            return List.of();
        }
        return Arrays.stream(wert.split(",")).filter(a -> ADRESSE.matcher(a).matches()).distinct().toList();
    }
}
