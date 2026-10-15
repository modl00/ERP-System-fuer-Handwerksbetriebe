package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Schalter für die Module aus {@link Modul}. Ausgeschaltet bedeutet: Menüpunkt, Seite und die Pfade des Moduls sind nicht erreichbar.
 * Gelöscht wird nichts, beim Einschalten ist alles wieder da. Ohne Eintrag gilt "ein", damit sich nach dem Update nichts ändert.
 */
@Service
@AllArgsConstructor
public class ModulSchalterService {

    private final SystemSettingRepository repository;

    @Transactional(readOnly = true)
    public boolean istAktiv(Modul modul) {
        return repository.findById(modul.getSchluessel()).map(s -> !"false".equalsIgnoreCase(s.getValue())).orElse(true);
    }

    @Transactional
    public boolean setzeAktiv(Modul modul, boolean aktiv) {
        SystemSetting einstellung = repository.findById(modul.getSchluessel()).orElseGet(SystemSetting::new);
        einstellung.setKey(modul.getSchluessel());
        einstellung.setValue(String.valueOf(aktiv));
        repository.save(einstellung);
        return aktiv;
    }
}
