package org.example.kalkulationsprogramm.service;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

/**
 * Schalter "Lagermodul ein/aus" in den Einstellungen. Ausgeschaltet bedeutet: Menüpunkt, Seite und {@code /api/lager/**} sind nicht erreichbar,
 * und Material "aus Lager" im Projekt wird nicht mehr im Lager abgebucht. Gelöscht wird nichts, beim Einschalten ist alles wieder da.
 * Ohne Eintrag gilt "ein", damit sich nach dem Update nichts ändert.
 */
@Service
@AllArgsConstructor
public class LagerModulSchalterService {

    public static final String SCHLUESSEL = "lager.modul.aktiv";

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
