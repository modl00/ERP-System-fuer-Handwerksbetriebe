package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.kalkulationsprogramm.domain.Email;
import org.example.kalkulationsprogramm.repository.EmailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

/** Änderungen im Postfach nachführen: Schalter, Voraussetzungen, Namen. Die IMAP-Zugriffe selbst laufen nur gegen ein echtes Postfach. */
class PostfachSpiegelServiceTest {

    private final SystemSettingsService einstellungen = mock(SystemSettingsService.class);
    private final PostfachSpiegelService service = new PostfachSpiegelService(einstellungen, mock(EmailRepository.class), mock(PlatformTransactionManager.class));

    private static Email mail(Long id, String ordner, Long uid) {
        Email e = new Email();
        e.setId(id);
        e.setImapFolder(ordner);
        e.setImapUid(uid);
        return e;
    }

    @Test
    void standardmaessigAusUndNurDerWertTrueSchaltetEin() {
        when(einstellungen.get(PostfachSpiegelService.AN, "false")).thenReturn("false");
        assertThat(service.istAktiv()).isFalse();
        when(einstellungen.get(PostfachSpiegelService.AN, "false")).thenReturn("TRUE");
        assertThat(service.istAktiv()).isTrue();
        when(einstellungen.get(PostfachSpiegelService.AN, "false")).thenReturn("ja");
        assertThat(service.istAktiv()).isFalse();
    }

    @Test
    void ausgeschaltetOderOhneServerNummerWirdNichtsAnDenProviderGeschickt() {
        when(einstellungen.get(PostfachSpiegelService.AN, "false")).thenReturn("false");
        service.loeschenVormerken(mail(1L, "INBOX", 5L));
        service.markierenVormerken(mail(1L, "INBOX", 5L), "2026-0412");

        when(einstellungen.get(PostfachSpiegelService.AN, "false")).thenReturn("true");
        service.loeschenVormerken(mail(2L, "INBOX", null));
        service.loeschenVormerken(mail(3L, null, 5L));
        service.loeschenVormerken(null);
        service.markierenVormerken(mail(4L, "INBOX", 5L), " ");

        verify(einstellungen, never()).getStandardImapZugang();
        verify(einstellungen, never()).save(anyString(), anyString(), anyString());
    }

    @Test
    void derSchalterWirdGespeichert() {
        assertThat(service.setzeAktiv(true)).isTrue();
        verify(einstellungen).save(org.mockito.ArgumentMatchers.eq(PostfachSpiegelService.AN), org.mockito.ArgumentMatchers.eq("true"), anyString());
    }

    @Test
    void schlagwoerterEnthaltenNurErlaubteZeichenUndSindBegrenzt() {
        assertThat(PostfachSpiegelService.schlagwort("2026-0412")).isEqualTo("ERP_2026-0412");
        assertThat(PostfachSpiegelService.schlagwort("Müller & Söhne (Halle)")).isEqualTo("ERP_M_ller___S_hne__Halle_");
        assertThat(PostfachSpiegelService.schlagwort("x".repeat(100))).hasSize(44);
    }

    @Test
    void papierkorbNamenWerdenErkanntAuchMitPraefix() {
        for (String n : new String[] { "Trash", "INBOX.Trash", "INBOX/Papierkorb", "Gelöschte Elemente", "Deleted Items", "[Gmail]/Bin" }) {
            assertThat(PostfachSpiegelService.istPapierkorbName(n)).as(n).isTrue();
        }
        for (String n : new String[] { "INBOX", "Sent", "Archiv", "Trashy", "", null }) {
            assertThat(PostfachSpiegelService.istPapierkorbName(n)).as(String.valueOf(n)).isFalse();
        }
    }
}
