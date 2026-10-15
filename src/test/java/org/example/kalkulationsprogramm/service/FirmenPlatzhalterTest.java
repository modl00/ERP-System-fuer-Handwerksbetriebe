package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.junit.jupiter.api.Test;

class FirmenPlatzhalterTest {

    private static Firmeninformation musterfirma() {
        Firmeninformation f = new Firmeninformation();
        f.setFirmenname("Muster Metallbau GmbH");
        f.setStrasse("Werkstraße 5");
        f.setPlz("97070");
        f.setOrt("Würzburg");
        f.setTelefon("0931 123456");
        f.setEmail("info@muster-metallbau.example");
        f.setBankName("Mustersparkasse");
        f.setIban("DE00 0000 0000 0000 0000 00");
        f.setBic("MUSTDE00XXX");
        f.setSteuernummer("123/456/78901");
        f.setHandelsregister("Amtsgericht Würzburg");
        f.setHandelsregisterNummer("HRB 12345");
        f.setGeschaeftsfuehrer("Max Mustermann");
        return f;
    }

    @Test
    void setztAngabenZuMehrzeiligenBloeckenZusammen() {
        Map<String, String> m = FirmenPlatzhalter.werte(musterfirma());

        assertEquals("Muster Metallbau GmbH", m.get("FIRMENNAME"));
        assertEquals("Werkstraße 5\n97070 Würzburg", m.get("FIRMENADRESSE"));
        assertEquals("Muster Metallbau GmbH | Werkstraße 5 | 97070 Würzburg", m.get("ABSENDERZEILE"));
        assertEquals("Tel.: 0931 123456\ninfo@muster-metallbau.example", m.get("FIRMENKONTAKT"));
        assertEquals("Mustersparkasse\nIBAN: DE00 0000 0000 0000 0000 00\nBIC: MUSTDE00XXX", m.get("FIRMENBANK"));
        assertEquals("Steuernr.: 123/456/78901", m.get("FIRMENSTEUER"));
        assertEquals("Amtsgericht Würzburg HRB 12345\nGeschäftsführer: Max Mustermann", m.get("FIRMENREGISTER"));
    }

    @Test
    void leereAngabenHinterlassenKeineLeerenBeschriftungen() {
        Firmeninformation f = new Firmeninformation();
        f.setFirmenname("  Nur Name  ");

        Map<String, String> m = FirmenPlatzhalter.werte(f);

        assertEquals("Nur Name", m.get("FIRMENNAME"));
        assertEquals("", m.get("FIRMENKONTAKT"), "ohne Telefon/E-Mail keine Zeile 'Tel.: '");
        assertEquals("", m.get("FIRMENBANK"));
        assertEquals("", m.get("FIRMENSTEUER"));
        assertEquals("Nur Name", m.get("ABSENDERZEILE"));
    }

    @Test
    void ohneFirmendatenSindAlleWerteLeerAberVorhanden() {
        Map<String, String> m = FirmenPlatzhalter.werte(null);

        assertTrue(m.containsKey("FIRMENNAME"));
        assertTrue(m.values().stream().allMatch(String::isEmpty));
        assertEquals(FirmenPlatzhalter.namen().size(), m.size());
    }

    @Test
    void erkenntBlockeDieNurAusFirmenangabenBestehen() {
        assertTrue(FirmenPlatzhalter.istNurFirmenangaben("{{FIRMENNAME}}"));
        assertTrue(FirmenPlatzhalter.istNurFirmenangaben("{{FIRMENBANK}}"));
        assertTrue(FirmenPlatzhalter.istNurFirmenangaben("{{FIRMENADRESSE}} · {{FIRMENKONTAKT}}"));
        assertTrue(FirmenPlatzhalter.istNurFirmenangaben("{{ABSENDERZEILE}}"));
        assertTrue(FirmenPlatzhalter.istNurFirmenangaben("{{FUSSZEILE}}"));
        org.junit.jupiter.api.Assertions.assertFalse(FirmenPlatzhalter.istNurFirmenangaben("{{FIRMENNAME}}  ·  {{DOKUMENTTYP}} {{DOKUMENTNUMMER}}"), "gemischt: bleibt");
        org.junit.jupiter.api.Assertions.assertFalse(FirmenPlatzhalter.istNurFirmenangaben("{{KUNDENADRESSE}}"));
        org.junit.jupiter.api.Assertions.assertFalse(FirmenPlatzhalter.istNurFirmenangaben("Tel. {{FIRMENTELEFON}}"), "fester Text dabei: bleibt");
        org.junit.jupiter.api.Assertions.assertFalse(FirmenPlatzhalter.istNurFirmenangaben(""));
        org.junit.jupiter.api.Assertions.assertFalse(FirmenPlatzhalter.istNurFirmenangaben(null));
        org.junit.jupiter.api.Assertions.assertFalse(FirmenPlatzhalter.istNurFirmenangaben("{{DATUM}}"));
    }
}
