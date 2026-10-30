package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(ModulSchalterService.class)
class ModulSchalterServiceTest {

    @Autowired private ModulSchalterService service;
    @Autowired private SystemSettingRepository repository;

    @Test
    @DisplayName("Ohne Eintrag sind alle Module eingeschaltet")
    void standardIstEin() {
        for (Modul m : Modul.values()) {
            assertThat(service.istAktiv(m)).as(m.getName()).isTrue();
        }
    }

    @Test
    @DisplayName("Jedes Modul schaltet nur seinen eigenen Eintrag")
    void jedesModulFuerSich() {
        SystemSetting andere = new SystemSetting();
        andere.setKey("smtp.host");
        andere.setValue("mail.example.test");
        repository.saveAndFlush(andere);

        service.setzeAktiv(Modul.MIETE, false);

        assertThat(service.istAktiv(Modul.MIETE)).isFalse();
        assertThat(service.istAktiv(Modul.WEBSITE)).isTrue();
        assertThat(repository.findById("miete.modul.aktiv")).get().extracting(SystemSetting::getValue).isEqualTo("false");
        assertThat(repository.findById("smtp.host")).get().extracting(SystemSetting::getValue).isEqualTo("mail.example.test");

        service.setzeAktiv(Modul.MIETE, true);
        assertThat(service.istAktiv(Modul.MIETE)).isTrue();
    }

    @Test
    @DisplayName("Namen und Schlüssel der Module sind eindeutig und auffindbar")
    void namenEindeutig() {
        assertThat(java.util.Arrays.stream(Modul.values()).map(Modul::getName).distinct().count()).isEqualTo(Modul.values().length);
        assertThat(java.util.Arrays.stream(Modul.values()).map(Modul::getSchluessel).distinct().count()).isEqualTo(Modul.values().length);
        assertThat(Modul.ausName("miete")).contains(Modul.MIETE);
        assertThat(Modul.ausName("lager")).isEmpty();
        assertThat(Modul.ausName("../etc")).isEmpty();
    }
}
