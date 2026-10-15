package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.AbteilungFunktionsrecht;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FunktionsAktion;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.repository.AbteilungFunktionsrechtRepository;
import org.example.kalkulationsprogramm.repository.AbteilungRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.repository.MitarbeiterRepository;
import org.example.kalkulationsprogramm.service.FunktionsRechteService.Rechte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class FunktionsRechteServiceTest {

    private final AbteilungRepository abteilungen = mock(AbteilungRepository.class);
    private final AbteilungFunktionsrechtRepository rechteRepo = mock(AbteilungFunktionsrechtRepository.class);
    private final FrontendUserProfileRepository profile = mock(FrontendUserProfileRepository.class);
    private final MitarbeiterRepository mitarbeiterRepo = mock(MitarbeiterRepository.class);
    private FunktionsRechteService service;
    private Abteilung montage;
    private Abteilung buero;
    private final java.util.List<AbteilungFunktionsrecht> gespeichert = new java.util.ArrayList<>();

    private static Abteilung abteilung(long id, String name) {
        Abteilung a = new Abteilung();
        ReflectionTestUtils.setField(a, "id", id);
        a.setName(name);
        return a;
    }

    private AbteilungFunktionsrecht zeile(Abteilung a, Funktion f, boolean sehen, boolean anlegen, boolean aendern, boolean loeschen) {
        AbteilungFunktionsrecht z = new AbteilungFunktionsrecht();
        z.setAbteilung(a);
        z.setFunktion(f.name());
        z.setAnsehen(sehen);
        z.setAnlegen(anlegen);
        z.setAendern(aendern);
        z.setLoeschen(loeschen);
        return z;
    }

    private Mitarbeiter mitarbeiter(Abteilung... in) {
        Mitarbeiter m = new Mitarbeiter();
        m.setAbteilungen(new HashSet<>(Set.of(in)));
        return m;
    }

    @BeforeEach
    void setUp() {
        service = new FunktionsRechteService(abteilungen, rechteRepo, profile, mitarbeiterRepo);
        montage = abteilung(1, "Montage");
        buero = abteilung(2, "Büro");
        when(abteilungen.findById(1L)).thenReturn(Optional.of(montage));
        when(abteilungen.findAll()).thenReturn(List.of(montage, buero));
        when(rechteRepo.findByAbteilungId(any())).thenAnswer(a -> gespeichert.stream().filter(z -> z.getAbteilung().getId().equals(a.getArgument(0))).toList());
        when(rechteRepo.findByAbteilungIdIn(any())).thenAnswer(a -> {
            java.util.Collection<Long> ids = a.getArgument(0);
            return gespeichert.stream().filter(z -> ids.contains(z.getAbteilung().getId())).toList();
        });
        when(rechteRepo.save(any())).thenAnswer(a -> {
            AbteilungFunktionsrecht z = a.getArgument(0);
            if (!gespeichert.contains(z)) {
                gespeichert.add(z);
            }
            return z;
        });
    }

    @Test
    void ohneGespeicherteZeileIstAllesErlaubt() {
        Mitarbeiter m = mitarbeiter(montage);

        for (Funktion f : Funktion.values()) {
            for (FunktionsAktion a : FunktionsAktion.values()) {
                assertThat(service.darf(m, f, a)).as(f + " " + a).isTrue();
            }
        }
    }

    @Test
    void eineEingeschraenkteAbteilungDarfNurWasEingestelltIst() {
        gespeichert.add(zeile(montage, Funktion.LAGER, true, true, false, false));
        Mitarbeiter m = mitarbeiter(montage);

        assertThat(service.darf(m, Funktion.LAGER, FunktionsAktion.ANSEHEN)).isTrue();
        assertThat(service.darf(m, Funktion.LAGER, FunktionsAktion.ANLEGEN)).isTrue();
        assertThat(service.darf(m, Funktion.LAGER, FunktionsAktion.AENDERN)).isFalse();
        assertThat(service.darf(m, Funktion.LAGER, FunktionsAktion.LOESCHEN)).isFalse();
        assertThat(service.darf(m, Funktion.WARTUNG, FunktionsAktion.LOESCHEN)).as("andere Funktion bleibt offen").isTrue();
    }

    @Test
    void ohneAnsehenGiltAuchAnlegenAendernLoeschenNicht() {
        gespeichert.add(zeile(montage, Funktion.LAGER, false, true, true, true));

        Map<Funktion, Rechte> r = service.wirksam(mitarbeiter(montage));

        assertThat(r.get(Funktion.LAGER)).isEqualTo(new Rechte(false, false, false, false));
    }

    @Test
    void mehrereAbteilungenGebenDieSummeDerRechte() {
        gespeichert.add(zeile(montage, Funktion.LAGER, true, false, false, false));
        gespeichert.add(zeile(buero, Funktion.LAGER, true, true, false, false));

        Rechte r = service.wirksam(mitarbeiter(montage, buero)).get(Funktion.LAGER);

        assertThat(r).isEqualTo(new Rechte(true, true, false, false));
    }

    @Test
    void eineAbteilungOhneZeileMachtDieSummeOffen() {
        gespeichert.add(zeile(montage, Funktion.LAGER, false, false, false, false));

        Rechte r = service.wirksam(mitarbeiter(montage, buero)).get(Funktion.LAGER);

        assertThat(r).as("Büro hat keine Einschränkung, also gilt dessen Standard").isEqualTo(Rechte.ALLES);
    }

    @Test
    void ohneAbteilungOderMitarbeiterIstAllesErlaubt() {
        assertThat(service.wirksam(mitarbeiter()).get(Funktion.LAGER)).isEqualTo(Rechte.ALLES);
        assertThat(service.wirksam(null).get(Funktion.WARTUNG)).isEqualTo(Rechte.ALLES);
        assertThat(service.wirksamFuerProfil(null).get(Funktion.LAGER)).isEqualTo(Rechte.ALLES);
    }

    @Test
    void benutzerAmPcBekommtDieRechteSeinesMitarbeiters() {
        gespeichert.add(zeile(montage, Funktion.WARTUNG, true, false, false, false));
        FrontendUserProfile p = new FrontendUserProfile();
        p.setMitarbeiter(mitarbeiter(montage));
        when(profile.findById(7L)).thenReturn(Optional.of(p));

        assertThat(service.darfProfil(7L, Funktion.WARTUNG, FunktionsAktion.ANSEHEN)).isTrue();
        assertThat(service.darfProfil(7L, Funktion.WARTUNG, FunktionsAktion.ANLEGEN)).isFalse();
        assertThat(service.darfProfil(99L, Funktion.WARTUNG, FunktionsAktion.ANLEGEN)).as("unbekanntes Profil: kein Mitarbeiter, kein Zwang").isTrue();
    }

    @Test
    void speichernLegtZeilenAnAendertSieUndLaesstNichtGenannteUnveraendert() {
        service.speichern(1L, Map.of("LAGER", new Rechte(true, true, false, false)));
        assertThat(gespeichert).hasSize(1);

        var ergebnis = service.speichern(1L, Map.of("WARTUNG", new Rechte(false, true, true, true), "LAGER", new Rechte(true, false, false, false)));

        assertThat(gespeichert).hasSize(2);
        assertThat(ergebnis.rechte().get(Funktion.LAGER)).isEqualTo(new Rechte(true, false, false, false));
        assertThat(ergebnis.rechte().get(Funktion.WARTUNG)).as("ohne Ansehen auch nichts anderes").isEqualTo(new Rechte(false, false, false, false));
    }

    @Test
    void speichernPrueftAbteilungFunktionUndRechte() {
        assertThatThrownBy(() -> service.speichern(999L, Map.of())).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.speichern(null, Map.of())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.speichern(1L, null)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.speichern(1L, Map.of("GIBTES_NICHT", Rechte.ALLES))).hasMessageContaining("Funktion");
        assertThatThrownBy(() -> service.speichern(1L, Map.of("'; DROP TABLE x; --", Rechte.ALLES))).isInstanceOf(ResponseStatusException.class);
        var map = new java.util.HashMap<String, Rechte>();
        map.put("LAGER", null);
        assertThatThrownBy(() -> service.speichern(1L, map)).hasMessageContaining("Lager");
        assertThat(gespeichert).isEmpty();
    }

    @Test
    void alleListetJedeAbteilungMitAllenFunktionen() {
        gespeichert.add(zeile(montage, Funktion.LAGER, true, false, false, false));

        var liste = service.alle();

        assertThat(liste).extracting(FunktionsRechteService.AbteilungRechte::abteilungName).containsExactly("Montage", "Büro");
        assertThat(liste.get(0).rechte()).containsKeys(Funktion.values());
        assertThat(liste.get(0).rechte().get(Funktion.LAGER).anlegen()).isFalse();
        assertThat(liste.get(1).rechte().get(Funktion.LAGER)).isEqualTo(Rechte.ALLES);
    }

    @Test
    void zugangslinkDerAppGibtDieRechteOderWirdAbgelehnt() {
        gespeichert.add(zeile(montage, Funktion.LAGER, true, true, false, false));
        when(mitarbeiterRepo.findByLoginTokenAndAktivTrue("gueltig")).thenReturn(Optional.of(mitarbeiter(montage)));

        assertThat(service.wirksamFuerToken("gueltig")).get().satisfies(r -> assertThat(r.get(Funktion.LAGER).aendern()).isFalse());
        assertThat(service.wirksamFuerToken("falsch")).isEmpty();
        assertThat(service.wirksamFuerToken(" ")).isEmpty();
        assertThat(service.wirksamFuerToken(null)).isEmpty();

        assertThat(service.verlangeFuerToken("gueltig", Funktion.LAGER, FunktionsAktion.ANLEGEN)).isNotNull();
        assertThatThrownBy(() -> service.verlangeFuerToken("gueltig", Funktion.LAGER, FunktionsAktion.LOESCHEN))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
                .hasMessageContaining("Lager, Löschen");
        assertThatThrownBy(() -> service.verlangeFuerToken("falsch", Funktion.LAGER, FunktionsAktion.ANSEHEN))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void aktionAusHttpMethode() {
        assertThat(FunktionsAktion.zuHttpMethode("GET")).isEqualTo(FunktionsAktion.ANSEHEN);
        assertThat(FunktionsAktion.zuHttpMethode("POST")).isEqualTo(FunktionsAktion.ANLEGEN);
        assertThat(FunktionsAktion.zuHttpMethode("put")).isEqualTo(FunktionsAktion.AENDERN);
        assertThat(FunktionsAktion.zuHttpMethode("PATCH")).isEqualTo(FunktionsAktion.AENDERN);
        assertThat(FunktionsAktion.zuHttpMethode("DELETE")).isEqualTo(FunktionsAktion.LOESCHEN);
        assertThat(FunktionsAktion.zuHttpMethode(null)).isEqualTo(FunktionsAktion.ANSEHEN);
    }
}
