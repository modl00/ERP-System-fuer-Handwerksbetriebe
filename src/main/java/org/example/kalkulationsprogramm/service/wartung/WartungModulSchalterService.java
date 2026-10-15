package org.example.kalkulationsprogramm.service.wartung;

import lombok.AllArgsConstructor;
import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Schalter "Wartungsmodul ein/aus" in den Einstellungen. Ausgeschaltet bedeutet: Menüpunkte, Seiten und
 * {@code /api/wartung/**} sind nicht erreichbar. Gelöscht wird dabei nichts, beim Einschalten ist alles wieder da.
 * Ohne Eintrag gilt "ein", damit sich nach dem Update nichts ändert.
 */
@Service
@AllArgsConstructor
public class WartungModulSchalterService {

    public static final String SCHLUESSEL = "wartung.modul.aktiv";

    private final SystemSettingRepository repository;

    @Transactional(readOnly = true)
    public boolean istAktiv() {
        return repository.findById(SCHLUESSEL).map(s -> !"false".equalsIgnoreCase(s.getValue())).orElse(true);
    }

    @Transactional
    public boolean setzeAktiv(boolean aktiv) {
        SystemSetting einstellung = repository.findById(SCHLUESSEL).orElseGet(SystemSetting::new);
        einstellung.setKey(SCHLUESSEL);
        einstellung.setValue(String.valueOf(aktiv));
        repository.save(einstellung);
        return aktiv;
    }
}
