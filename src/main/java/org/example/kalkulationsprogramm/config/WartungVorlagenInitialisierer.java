package org.example.kalkulationsprogramm.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Spielt die Startvorlagen der Wartung ({@code V901__wartung_vorlagen.sql}) ein, wenn Flyway ausgeschaltet ist.
 * <p>
 * Das H2-Profil (Windows-Installer, Einzel-Container) legt die Tabellen mit Hibernate an und startet ohne Flyway.
 * Dort käme der Modulkatalog sonst nie an, und eine neue Wartung ließe sich nicht anlegen. Die Vorlagen werden nur
 * eingespielt, solange der Katalog leer ist: Wer Module später ändert oder löscht, bekommt sie nicht zurück.
 * Das Einspielen ist eine einzige Transaktion, ein Abbruch hinterlässt also keinen halben Katalog.
 * Mit Flyway (MySQL) macht die Migration diese Arbeit, dieser Starter ist dann nicht aktiv.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.flyway.enabled", havingValue = "false")
public class WartungVorlagenInitialisierer implements ApplicationRunner {

    /** Reihenfolge wie bei Flyway: erst die Startvorlagen, dann der einheitliche Katalog (Auswahllisten statt Freitext). */
    private static final String[] SKRIPTE = { "db/migration/V901__wartung_vorlagen.sql", "db/migration/V921__wartung_katalog_einheitlich.sql" };

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;

    public WartungVorlagenInitialisierer(DataSource dataSource, JdbcTemplate jdbc) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer vorhanden = jdbc.queryForObject("select count(*) from wartung_modul", Integer.class);
            if (vorhanden != null && vorhanden > 0) {
                return;
            }
            spieleEin();
            Integer danach = jdbc.queryForObject("select count(*) from wartung_modul", Integer.class);
            log.info("Wartung: Startvorlagen eingespielt ({} Module).", danach);
        } catch (Exception e) {
            // Der Start der Anwendung darf daran nicht scheitern. Ohne Vorlagen bleibt nur die Wartung leer.
            Throwable ursache = e;
            while (ursache.getCause() != null && ursache.getCause() != ursache) {
                ursache = ursache.getCause();
            }
            log.warn("Wartung: Startvorlagen konnten nicht eingespielt werden: {}", ursache.getMessage());
        }
    }

    private void spieleEin() throws Exception {
        try (Connection verbindung = dataSource.getConnection()) {
            boolean vorherAutoCommit = verbindung.getAutoCommit();
            verbindung.setAutoCommit(false);
            try {
                for (String skript : SKRIPTE) {
                    new ResourceDatabasePopulator(new ClassPathResource(skript)).populate(verbindung);
                }
                verbindung.commit();
            } catch (Exception e) {
                verbindung.rollback();
                throw e;
            } finally {
                verbindung.setAutoCommit(vorherAutoCommit);
            }
        }
    }
}
