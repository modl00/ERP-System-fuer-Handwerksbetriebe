package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.example.kalkulationsprogramm.service.importe.HeroVerbindungService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Beim Start muss Spring die Klassen mit mehreren Konstruktoren anlegen können (ein Konstruktor ist nur für Tests). Ohne {@code @Autowired} am richtigen Konstruktor scheitert
 * der ganze Start mit „No default constructor found“.
 */
class KonstruktorStartTest {

    @Test
    void passwortSchutzUndHeroVerbindungLassenSichVonSpringAnlegen() {
        try (var ctx = new AnnotationConfigApplicationContext()) {
            SystemSettingsService einstellungen = mock(SystemSettingsService.class);
            org.mockito.Mockito.when(einstellungen.get(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString())).thenAnswer(i -> i.getArgument(1));
            ctx.registerBean(SystemSettingsService.class, () -> einstellungen);
            ctx.registerBean(org.springframework.context.support.PropertySourcesPlaceholderConfigurer.class);
            ctx.register(PasswortSchutz.class, HeroVerbindungService.class);
            ctx.refresh();
            assertThat(ctx.getBean(PasswortSchutz.class)).isNotNull();
            assertThat(ctx.getBean(HeroVerbindungService.class).status().adresse()).contains("hero-software.de");
        }
    }
}
