package org.example.kalkulationsprogramm.service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Die Schnellzugriffsleiste unter dem Hauptmenü (#290): frei gewählte Funktionen, in der gewählten Reihenfolge. Wie beim Menü gibt es zwei Ebenen: die <b>Vorgabe</b> der
 * Firma (vom Admin) und die <b>eigene Auswahl</b> je Benutzer, die Vorrang hat. Gespeichert wird in {@code system_setting} als Adressen wie {@code /werkstoffe}; die
 * besondere Adresse {@value #ALLE_FUNKTIONEN} steht für den Knopf „Alle Funktionen“. Rechte und Module regeln weiterhin, was jemand sehen darf: Was er nicht darf, zeigt die
 * Oberfläche nicht an.
 */
@Service
@AllArgsConstructor
public class SchnellzugriffService {

    public static final String SCHLUESSEL_VORGABE = "schnellzugriff.vorgabe";
    private static final String SCHLUESSEL_BENUTZER = "schnellzugriff.benutzer.";
    public static final String ALLE_FUNKTIONEN = "/alle-funktionen";

    /** Mehr passen nicht sinnvoll in eine Leiste. */
    static final int MAX_EINTRAEGE = 30;

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
    public List<String> setzeVorgabe(List<String> eintraege) {
        return schreibe(SCHLUESSEL_VORGABE, eintraege);
    }

    @Transactional
    public List<String> setzeEigene(long benutzerId, List<String> eintraege) {
        return schreibe(SCHLUESSEL_BENUTZER + benutzerId, eintraege);
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
        return repository.findById(schluessel).map(SystemSetting::getValue).map(SchnellzugriffService::zerlege);
    }

    private List<String> schreibe(String schluessel, List<String> eintraege) {
        List<String> sauber = pruefe(eintraege);
        SystemSetting einstellung = repository.findById(schluessel).orElseGet(SystemSetting::new);
        einstellung.setKey(schluessel);
        einstellung.setValue(String.join(",", sauber));
        repository.save(einstellung);
        return sauber;
    }

    /** Prüft die Liste und entfernt Doppelte; die Reihenfolge bleibt (sie ist die Reihenfolge der Leiste). */
    static List<String> pruefe(List<String> eintraege) {
        if (eintraege == null) {
            throw new IllegalArgumentException("Bitte die Funktionen für die Leiste angeben.");
        }
        List<String> sauber = MenueService.pruefe(eintraege);
        if (sauber.size() > MAX_EINTRAEGE) {
            throw new IllegalArgumentException("Auf die Leiste passen höchstens " + MAX_EINTRAEGE + " Funktionen.");
        }
        return sauber;
    }

    private static List<String> zerlege(String wert) {
        if (wert == null || wert.isBlank()) {
            return List.of();
        }
        return Arrays.stream(wert.split(",")).filter(a -> ADRESSE.matcher(a).matches()).distinct().limit(MAX_EINTRAEGE).toList();
    }
}
