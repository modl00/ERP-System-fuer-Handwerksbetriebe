package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verschlüsselung der Postfach-Passwörter. Nur Dummy-Werte. */
class PasswortSchutzTest {

    private static byte[] schluessel(int start) {
        byte[] k = new byte[32];
        for (int i = 0; i < k.length; i++) {
            k[i] = (byte) (start + i);
        }
        return k;
    }

    @Test
    void verschluesselnUndEntschluesselnErgibtDasOriginalAberNieKlartext() {
        PasswortSchutz s = new PasswortSchutz(schluessel(1));
        String text = s.verschluessele("Geheim-äöü-123");
        assertThat(text).startsWith("v1:").doesNotContain("Geheim");
        assertThat(s.entschluessele(text)).isEqualTo("Geheim-äöü-123");
    }

    @Test
    void jedesVerschluesselnGibtAnderenText() {
        PasswortSchutz s = new PasswortSchutz(schluessel(1));
        assertThat(s.verschluessele("gleich")).isNotEqualTo(s.verschluessele("gleich"));
    }

    @Test
    void verfaelschterTextOderFremderSchluesselSindNichtLesbar() {
        PasswortSchutz a = new PasswortSchutz(schluessel(1));
        PasswortSchutz b = new PasswortSchutz(schluessel(50));
        String text = a.verschluessele("pw");
        assertThatThrownBy(() -> b.entschluessele(text)).isInstanceOf(IllegalStateException.class).hasMessageContaining("neu eingeben");
        assertThatThrownBy(() -> a.entschluessele(text.substring(0, text.length() - 4) + "AAAA")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> a.entschluessele("klartext")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> a.entschluessele(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void derSchluesselWirdEinmalInDerDateiAngelegtUndWiederverwendet(@TempDir Path ordner) throws Exception {
        PasswortSchutz erst = new PasswortSchutz(ordner.toString());
        String text = erst.verschluessele("pw");
        assertThat(Files.exists(ordner.resolve(".postfach-schluessel"))).isTrue();

        PasswortSchutz spaeter = new PasswortSchutz(ordner.toString());
        assertThat(spaeter.entschluessele(text)).isEqualTo("pw");
    }

    @Test
    void eineBeschaedigteSchluesseldateiWirdNichtStillErsetzt(@TempDir Path ordner) throws Exception {
        Files.writeString(ordner.resolve(".postfach-schluessel"), "zu-kurz");
        PasswortSchutz s = new PasswortSchutz(ordner.toString());
        assertThatThrownBy(() -> s.verschluessele("pw")).isInstanceOf(RuntimeException.class);
    }
}
