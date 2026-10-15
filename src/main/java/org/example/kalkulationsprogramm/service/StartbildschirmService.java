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
 * Der Startbildschirm (#290): welche Bausteine (Widgets) in welcher Reihenfolge erscheinen. Zwei Ebenen wie beim Menü: die <b>Vorgabe</b> der Firma (vom Admin) und die
 * <b>eigene Auswahl</b> je Benutzer, die Vorrang hat. Gespeichert wird in {@code system_setting} als Kennungen wie {@code aufgaben}; welche es gibt, weiß die Oberfläche
 * (Unbekanntes ignoriert sie). Die Bausteine zeigen nur, was der Benutzer ohnehin sehen darf.
 */
@Service
@AllArgsConstructor
public class StartbildschirmService {

    public static final String SCHLUESSEL_VORGABE = "start.vorgabe";
    private static final String SCHLUESSEL_BENUTZER = "start.benutzer.";

    /** Mehr passen nicht sinnvoll in eine Leiste. */
    static final int MAX_EINTRAEGE = 20;

    private static final Pattern KENNUNG = Pattern.compile("[a-z0-9-]{1,40}");

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
        return repository.findById(schluessel).map(SystemSetting::getValue).map(StartbildschirmService::zerlege);
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
            throw new IllegalArgumentException("Bitte die Bausteine angeben.");
        }
        List<String> sauber = eintraege.stream().peek(k -> {
            if (k == null || !KENNUNG.matcher(k).matches()) {
                throw new IllegalArgumentException("Ungültiger Baustein.");
            }
        }).distinct().toList();
        if (sauber.size() > MAX_EINTRAEGE) {
            throw new IllegalArgumentException("Es sind höchstens " + MAX_EINTRAEGE + " Bausteine möglich.");
        }
        return sauber;
    }

    private static List<String> zerlege(String wert) {
        if (wert == null || wert.isBlank()) {
            return List.of();
        }
        return Arrays.stream(wert.split(",")).filter(a -> KENNUNG.matcher(a).matches()).distinct().limit(MAX_EINTRAEGE).toList();
    }
}
