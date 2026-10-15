package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Erkennung des Ordners mit gesendeten Mails (nur Namen, keine Verbindung). */
class EmailImportOrdnerTest {

    @Test
    void ueblicheNamenWerdenErkanntAuchMitPraefix() {
        for (String n : new String[] { "Sent", "INBOX.Sent", "INBOX/Sent Items", "Gesendet", "INBOX.Gesendete Elemente", "Gesendete Objekte", "INBOX.Sent Messages", "sent" }) {
            assertThat(EmailImportService.istGesendetName(n)).as(n).isTrue();
        }
    }

    @Test
    void andereOrdnerSindNichtGesendet() {
        for (String n : new String[] { "INBOX", "Drafts", "Entwürfe", "INBOX.Archives (2).Eingangsrechnungen", "Sentimental", "Papierkorb", "", null }) {
            assertThat(EmailImportService.istGesendetName(n)).as(String.valueOf(n)).isFalse();
        }
    }
}
