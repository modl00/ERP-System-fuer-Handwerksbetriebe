package org.example.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MailSicherheitTest {

    @Test
    @DisplayName("Versand: Port 465 = SSL/TLS von Anfang an wie bisher, 587, 25 und 2525 = STARTTLS und zwar verlangt, jeder andere Port = SSL/TLS")
    void versand() {
        Properties ssl = new Properties();
        MailSicherheit.smtp(ssl, 465);
        assertThat(ssl.getProperty("mail.smtp.ssl.enable")).isEqualTo("true");
        assertThat(ssl.getProperty("mail.smtp.socketFactory.port")).isEqualTo("465");
        assertThat(ssl.getProperty("mail.smtp.socketFactory.class")).isEqualTo("javax.net.ssl.SSLSocketFactory");
        assertThat(ssl.getProperty("mail.smtp.starttls.enable")).isNull();

        Properties anderer = new Properties();
        MailSicherheit.smtp(anderer, 3465);
        assertThat(anderer.getProperty("mail.smtp.ssl.enable")).as("jeder andere Port: SSL von Anfang an").isEqualTo("true");

        for (int port : new int[] { 587, 25, 2525 }) {
            Properties starttls = new Properties();
            MailSicherheit.smtp(starttls, port);
            assertThat(starttls.getProperty("mail.smtp.starttls.enable")).as("Port " + port).isEqualTo("true");
            assertThat(starttls.getProperty("mail.smtp.starttls.required")).as("Port " + port).isEqualTo("true");
            assertThat(starttls.getProperty("mail.smtp.ssl.enable")).as("Port " + port).isNull();
            assertThat(starttls.getProperty("mail.smtp.socketFactory.class")).as("Port " + port).isNull();
        }
    }

    @Test
    @DisplayName("Empfang: Port 993 und jeder andere = SSL/TLS (imaps) wie bisher, Port 143 = STARTTLS (imap, verlangt)")
    void empfang() {
        Properties ssl = MailSicherheit.imap(993, 15000, 30000);
        assertThat(MailSicherheit.imapProtokoll(993)).isEqualTo("imaps");
        assertThat(ssl.getProperty("mail.store.protocol")).isEqualTo("imaps");
        assertThat(ssl.getProperty("mail.imaps.ssl.enable")).isEqualTo("true");
        assertThat(ssl.getProperty("mail.imaps.connectiontimeout")).isEqualTo("15000");
        assertThat(ssl.getProperty("mail.imaps.timeout")).isEqualTo("30000");
        assertThat(ssl.getProperty("mail.mime.address.strict")).isEqualTo("false");
        assertThat(MailSicherheit.imapProtokoll(1993)).as("ein ungewöhnlicher Port bleibt SSL/TLS wie bisher").isEqualTo("imaps");

        Properties starttls = MailSicherheit.imap(143, 10000, 10000);
        assertThat(MailSicherheit.imapProtokoll(143)).isEqualTo("imap");
        assertThat(starttls.getProperty("mail.store.protocol")).isEqualTo("imap");
        assertThat(starttls.getProperty("mail.imap.starttls.enable")).isEqualTo("true");
        assertThat(starttls.getProperty("mail.imap.starttls.required")).isEqualTo("true");
        assertThat(starttls.getProperty("mail.imap.connectiontimeout")).isEqualTo("10000");
        assertThat(starttls.getProperty("mail.imaps.ssl.enable")).isNull();
    }

    @Test
    @DisplayName("Ohne eigene Zeitgrenzen bleiben die Standardwerte der Bibliothek")
    void ohneZeitgrenzen() {
        Properties props = MailSicherheit.imap(993);

        assertThat(props.getProperty("mail.imaps.connectiontimeout")).isNull();
        assertThat(props.getProperty("mail.imaps.timeout")).isNull();
        assertThat(props.getProperty("mail.imaps.ssl.enable")).isEqualTo("true");
    }
}
