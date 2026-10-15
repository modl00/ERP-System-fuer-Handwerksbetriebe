package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import(LagerModulSchalterService.class)
class LagerModulSchalterServiceTest {

    @Autowired private LagerModulSchalterService service;
    @Autowired private SystemSettingRepository repository;

    @Test
    @DisplayName("Ohne Eintrag ist das Lager eingeschaltet")
    void standardIstEin() {
        assertThat(service.istAktiv()).isTrue();
    }

    @Test
    @DisplayName("Aus- und Einschalten ändert nur den eigenen Eintrag")
    void ausUndEinschalten() {
        SystemSetting andere = new SystemSetting();
        andere.setKey("smtp.host");
        andere.setValue("mail.example.test");
        repository.saveAndFlush(andere);

        service.setzeAktiv(false);
        assertThat(service.istAktiv()).isFalse();
        assertThat(repository.findById("lager.modul.aktiv")).get().extracting(SystemSetting::getValue).isEqualTo("false");

        service.setzeAktiv(true);
        assertThat(service.istAktiv()).isTrue();
        assertThat(repository.findById("smtp.host")).get().extracting(SystemSetting::getValue).isEqualTo("mail.example.test");
    }
}
