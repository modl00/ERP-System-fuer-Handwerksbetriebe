package org.example.kalkulationsprogramm.service.wartung;

import static org.assertj.core.api.Assertions.assertThat;

import org.example.kalkulationsprogramm.domain.SystemSetting;
import org.example.kalkulationsprogramm.repository.SystemSettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

/** Der Schalter in der echten Einstellungstabelle: Standard ein, aus- und einschalten, nichts anderes wird berührt. */
@DataJpaTest
@Import(WartungModulSchalterService.class)
class WartungModulSchalterServiceTest {

    @Autowired private WartungModulSchalterService service;
    @Autowired private SystemSettingRepository repository;

    @Test
    @DisplayName("Ohne Eintrag ist das Modul eingeschaltet, damit sich nach dem Update nichts ändert")
    void standardIstEin() {
        assertThat(service.istAktiv()).isTrue();
    }

    @Test
    @DisplayName("Aus- und wieder Einschalten ändert nur den eigenen Eintrag und lässt andere Einstellungen unberührt")
    void ausUndEinschalten() {
        SystemSetting andere = new SystemSetting();
        andere.setKey("smtp.host");
        andere.setValue("mail.example.test");
        repository.saveAndFlush(andere);

        service.setzeAktiv(false);
        assertThat(service.istAktiv()).isFalse();
        assertThat(repository.findById("wartung.modul.aktiv")).get().extracting(SystemSetting::getValue).isEqualTo("false");

        service.setzeAktiv(true);
        assertThat(service.istAktiv()).isTrue();
        assertThat(repository.findAll()).hasSize(2);
        assertThat(repository.findById("smtp.host")).get().extracting(SystemSetting::getValue).isEqualTo("mail.example.test");
    }

    @Test
    @DisplayName("Ein unleserlicher Wert schaltet das Modul nicht versehentlich aus")
    void unleserlicherWertGiltAlsEin() {
        SystemSetting kaputt = new SystemSetting();
        kaputt.setKey("wartung.modul.aktiv");
        kaputt.setValue("vielleicht");
        repository.saveAndFlush(kaputt);

        assertThat(service.istAktiv()).isTrue();
    }
}
