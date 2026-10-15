package org.example.kalkulationsprogramm.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class AktivitaetObjektAdviceTest {

    public record Neu(Long id, String name) { }

    public static class Alt {
        public Long getId() { return 42L; }
    }

    private final AktivitaetObjektAdvice advice = new AktivitaetObjektAdvice();

    private MockHttpServletRequest anfrage(String methode, String muster) {
        MockHttpServletRequest r = new MockHttpServletRequest(methode, "/x");
        r.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, muster);
        return r;
    }

    private Object schreibe(MockHttpServletRequest r, Object body) {
        return advice.beforeBodyWrite(body, null, null, null, new ServletServerHttpRequest(r), null);
    }

    @Test
    @DisplayName("Beim Anlegen aus dem Katalog wird die Nummer aus der Antwort (Record, Getter oder Map) gemerkt; die Antwort bleibt unverändert")
    void merktDieNummer() {
        MockHttpServletRequest r = anfrage("POST", "/api/projekte");
        Neu body = new Neu(7L, "x");

        assertThat(schreibe(r, body)).isSameAs(body);
        assertThat(r.getAttribute(AktivitaetObjektAdvice.ATTRIBUT)).isEqualTo(7L);

        MockHttpServletRequest r2 = anfrage("POST", "/api/kunden");
        schreibe(r2, new Alt());
        assertThat(r2.getAttribute(AktivitaetObjektAdvice.ATTRIBUT)).isEqualTo(42L);

        MockHttpServletRequest r3 = anfrage("POST", "/api/artikel");
        schreibe(r3, Map.of("id", 9));
        assertThat(r3.getAttribute(AktivitaetObjektAdvice.ATTRIBUT)).isEqualTo(9L);
    }

    @Test
    @DisplayName("Nichts wird gemerkt bei Lesen, Adressen außerhalb des Katalogs, Aktionen ohne Objektart, fehlender Nummer oder leerer Antwort")
    void merktNichts() {
        MockHttpServletRequest lesen = anfrage("GET", "/api/projekte");
        schreibe(lesen, new Neu(7L, "x"));
        MockHttpServletRequest fremd = anfrage("POST", "/api/auth/login");
        schreibe(fremd, new Neu(7L, "x"));
        MockHttpServletRequest ohneObjekt = anfrage("POST", "/api/emails/send");
        schreibe(ohneObjekt, new Neu(7L, "x"));
        MockHttpServletRequest ohneNummer = anfrage("POST", "/api/projekte");
        schreibe(ohneNummer, "nur Text");
        MockHttpServletRequest leer = anfrage("POST", "/api/projekte");
        assertThat(schreibe(leer, null)).isNull();

        for (MockHttpServletRequest r : new MockHttpServletRequest[] { lesen, fremd, ohneObjekt, ohneNummer, leer }) {
            assertThat(r.getAttribute(AktivitaetObjektAdvice.ATTRIBUT)).isNull();
        }
    }
}
