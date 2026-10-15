package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailFehlermeldungTest {

    @Test
    @DisplayName("Die häufigen englischen Fehlertexte kommen verständlich auf Deutsch, mit dem, was zu prüfen ist")
    void uebersetzt() {
        assertThat(SystemSettingsService.verstaendlicheMailFehlermeldung("STARTTLS is required but host does not support STARTTLS"))
                .contains("STARTTLS").contains("SSL/TLS").contains("465");
        assertThat(SystemSettingsService.verstaendlicheMailFehlermeldung("Unsupported or unrecognized SSL message")).contains("keine SSL/TLS-Verbindung").contains("587");
        assertThat(SystemSettingsService.verstaendlicheMailFehlermeldung("Exception reading response")).contains("keine SSL/TLS-Verbindung");
        assertThat(SystemSettingsService.verstaendlicheMailFehlermeldung("Couldn't connect to host, port: mail.example.org, 465; timeout 10000"))
                .contains("nicht erreichbar").contains("mail.example.org");
        assertThat(SystemSettingsService.verstaendlicheMailFehlermeldung("PKIX path building failed: unable to find valid certification path")).contains("Zertifikat");
    }

    @Test
    @DisplayName("Unbekannte Meldungen bleiben unverändert, leere werden zu einem Satz")
    void unveraendert() {
        assertThat(SystemSettingsService.verstaendlicheMailFehlermeldung("550 Mailbox unavailable")).isEqualTo("550 Mailbox unavailable");
        assertThat(SystemSettingsService.verstaendlicheMailFehlermeldung(null)).isEqualTo("Der Server hat nicht geantwortet.");
        assertThat(SystemSettingsService.verstaendlicheMailFehlermeldung("  ")).isEqualTo("Der Server hat nicht geantwortet.");
    }
}
