package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.domain.Abteilung;
import org.example.kalkulationsprogramm.domain.Funktion;
import org.example.kalkulationsprogramm.domain.FrontendUserProfile;
import org.example.kalkulationsprogramm.domain.FrontendUserRole;
import org.example.kalkulationsprogramm.domain.Mitarbeiter;
import org.example.kalkulationsprogramm.domain.MitarbeiterArt;
import org.example.kalkulationsprogramm.dto.BelegDto;
import org.example.kalkulationsprogramm.dto.Rechte.RechteUebersichtDto;
import org.example.kalkulationsprogramm.dto.Rechte.RechteUebersichtDto.Benutzer;
import org.example.kalkulationsprogramm.repository.AbteilungDokumentBerechtigungRepository;
import org.example.kalkulationsprogramm.repository.FrontendUserProfileRepository;
import org.example.kalkulationsprogramm.service.FunktionsRechteService.Rechte;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Nur Dummy-Daten. Die Übersicht entscheidet nichts selbst; geprüft wird, dass sie die Antworten der echten Regeln richtig zeigt und erklärt. */
@ExtendWith(MockitoExtension.class)
class RechteUebersichtServiceTest {

    @Mock FrontendUserProfileRepository profile;
    @Mock FunktionsRechteService funktionsRechte;
    @Mock BelegService belegService;
    @Mock AbteilungDokumentBerechtigungRepository dokumentRechte;

    RechteUebersichtService service;
    Abteilung montage;
    Abteilung buero;

    @BeforeEach
    void setUp() {
        service = new RechteUebersichtService(profile, funktionsRechte, belegService, dokumentRechte);
        montage = abteilung(1L, "Montage");
        buero = abteilung(2L, "Büro");
        lenient().when(funktionsRechte.wirksam(any())).thenReturn(alle(Rechte.ALLES));
        lenient().when(belegService.getPermissions(any())).thenReturn(BelegDto.PermissionResponse.builder().darfSehen(false).darfScannen(false)
                .grund(BelegDto.PermissionResponse.KEIN_RECHT).build());
        lenient().when(dokumentRechte.findSichtbareTypenByAbteilungIds(any())).thenReturn(List.of());
    }

    private static Abteilung abteilung(Long id, String name) {
        Abteilung a = new Abteilung();
        a.setId(id);
        a.setName(name);
        return a;
    }

    private static Map<Funktion, Rechte> alle(Rechte r) {
        Map<Funktion, Rechte> m = new EnumMap<>(Funktion.class);
        for (Funktion f : Funktion.values()) {
            m.put(f, r);
        }
        return m;
    }

    private static Mitarbeiter mitarbeiter(Long id, String vorname, String nachname, Abteilung... abteilungen) {
        Mitarbeiter m = new Mitarbeiter();
        m.setId(id);
        m.setVorname(vorname);
        m.setNachname(nachname);
        m.setArt(MitarbeiterArt.MENSCH);
        m.setAktiv(true);
        m.setAbteilungen(new LinkedHashSet<>(List.of(abteilungen)));
        return m;
    }

    private static FrontendUserProfile benutzer(Long id, String name, boolean admin, Mitarbeiter m) {
        FrontendUserProfile p = new FrontendUserProfile();
        p.setId(id);
        p.setDisplayName(name);
        p.setUsername(name.toLowerCase().replace(' ', '.'));
        p.setActive(true);
        p.setRoleSet(new LinkedHashSet<>(admin ? Set.of(FrontendUserRole.ADMIN) : Set.of(FrontendUserRole.USER)));
        p.setMitarbeiter(m);
        return p;
    }

    private Benutzer finde(RechteUebersichtDto d, String name) {
        return d.benutzer().stream().filter(b -> b.anzeigename().equals(name)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Administrator: Einstellungen und Funktionen alles; Monatsabschluss und Telefon nur mit Abteilungsrecht, mit Hinweis")
    void administrator() {
        Mitarbeiter m = mitarbeiter(10L, "Max", "Mustermann", buero);
        when(profile.findAll()).thenReturn(List.of(benutzer(1L, "Admin Chef", true, m)));

        Benutzer b = finde(service.uebersicht(), "Admin Chef");

        assertThat(b.administrator()).isTrue();
        assertThat(b.felder().get("EINSTELLUNGEN").stufe()).isEqualTo("ALLES");
        assertThat(b.felder().get("FUNKTION_LAGER").grund()).contains("Administrator");
        assertThat(b.felder().get("BELEGE").stufe()).isEqualTo("ALLES");
        assertThat(b.felder().get("MONATSABSCHLUSS").stufe()).isEqualTo("NICHTS");
        assertThat(b.felder().get("MONATSABSCHLUSS").grund()).contains("Keine seiner Abteilungen").contains("nicht automatisch");
    }

    @Test
    @DisplayName("Normaler Benutzer: Funktionsrechte aus den Abteilungen mit Text und Begründung; Nur-Ansehen ist Teilweise")
    void funktionsrechte() {
        Mitarbeiter m = mitarbeiter(11L, "Erika", "Musterfrau", montage, buero);
        Map<Funktion, Rechte> rechte = alle(Rechte.ALLES);
        rechte.put(Funktion.LAGER, new Rechte(true, false, false, false));
        rechte.put(Funktion.WARTUNG, new Rechte(false, false, false, false));
        when(funktionsRechte.wirksam(m)).thenReturn(rechte);
        when(profile.findAll()).thenReturn(List.of(benutzer(2L, "Erika Musterfrau", false, m)));

        Benutzer b = finde(service.uebersicht(), "Erika Musterfrau");

        assertThat(b.felder().get("FUNKTION_LAGER").stufe()).isEqualTo("TEILWEISE");
        assertThat(b.felder().get("FUNKTION_LAGER").text()).isEqualTo("Sehen");
        assertThat(b.felder().get("FUNKTION_LAGER").grund()).isEqualTo("Über die Abteilungen Büro, Montage (zählt zusammen, was eine der Abteilungen erlaubt).");
        assertThat(b.felder().get("FUNKTION_WARTUNG").stufe()).isEqualTo("NICHTS");
        assertThat(b.felder().get("FUNKTION_WARTUNG").text()).isEqualTo("Nein");
        for (Funktion f : Funktion.values()) {
            if (f != Funktion.LAGER && f != Funktion.WARTUNG) {
                assertThat(b.felder().get("FUNKTION_" + f.name()).stufe()).isEqualTo("ALLES");
            }
        }
        assertThat(b.abteilungen()).containsExactly("Büro", "Montage");
        assertThat(b.mitarbeiterName()).isEqualTo("Erika Musterfrau");
    }

    @Test
    @DisplayName("Ohne Mitarbeiter: Funktionen uneingeschränkt (das ist die Regel), Belege/Telefon/Monat gesperrt, Warnung mit Klartext und Link")
    void ohneMitarbeiter() {
        when(profile.findAll()).thenReturn(List.of(benutzer(3L, "Hans Beispiel", false, null), benutzer(1L, "Admin", true, null)));
        when(belegService.getPermissions(null)).thenReturn(BelegDto.PermissionResponse.builder().grund(BelegDto.PermissionResponse.KEIN_MITARBEITER).build());

        RechteUebersichtDto d = service.uebersicht();
        Benutzer b = finde(d, "Hans Beispiel");

        assertThat(b.felder().get("FUNKTION_LAGER").stufe()).isEqualTo("ALLES");
        assertThat(b.felder().get("FUNKTION_LAGER").grund()).contains("Kein Mitarbeiter").contains("alles erlaubt");
        assertThat(b.felder().get("BELEGE").grund()).isEqualTo("Kein Mitarbeiter mit diesem Benutzer verknüpft.");
        assertThat(b.felder().get("TELEFON").stufe()).isEqualTo("NICHTS");
        assertThat(d.warnungen()).anySatisfy(w -> {
            assertThat(w.art()).isEqualTo("OHNE_MITARBEITER");
            assertThat(w.text()).contains("Hans Beispiel").contains("keinem Mitarbeiter");
            assertThat(w.ziel()).isEqualTo("/benutzer");
            assertThat(w.profilId()).isEqualTo(3L);
        });
        assertThat(d.warnungen()).noneMatch(w -> "Admin".equals(w.text().split(" ")[0]));
    }

    @Test
    @DisplayName("Mitarbeiter ohne Abteilung: Warnung, Funktionen alles erlaubt, Schalter gesperrt")
    void ohneAbteilung() {
        Mitarbeiter m = mitarbeiter(12L, "Paul", "Probe");
        when(profile.findAll()).thenReturn(List.of(benutzer(4L, "Paul Probe", false, m)));
        when(belegService.getPermissions(m)).thenReturn(BelegDto.PermissionResponse.builder().grund(BelegDto.PermissionResponse.KEINE_ABTEILUNG).build());

        RechteUebersichtDto d = service.uebersicht();
        Benutzer b = finde(d, "Paul Probe");

        assertThat(b.felder().get("FUNKTION_LAGER").grund()).contains("keine Abteilung");
        assertThat(b.felder().get("BELEGE").grund()).isEqualTo("Der Mitarbeiter hat keine Abteilung.");
        assertThat(b.felder().get("EINGANGSRECHNUNGEN_SEHEN").grund()).isEqualTo("Der Mitarbeiter hat keine Abteilung.");
        assertThat(d.warnungen()).anySatisfy(w -> {
            assertThat(w.art()).isEqualTo("OHNE_ABTEILUNG");
            assertThat(w.text()).contains("Paul Probe").contains("Paul Probe").contains("keine Abteilung");
        });
    }

    @Test
    @DisplayName("Schalter an der Abteilung: Ja mit den Abteilungen, die es erlauben; Nein mit Grund; inaktiver Mitarbeiter hat Telefon und Monatsabschluss nicht")
    void schalter() {
        montage.setDarfRechnungenSehen(true);
        buero.setDarfRechnungenSehen(true);
        buero.setDarfRechnungenGenehmigen(true);
        buero.setDarfMonatAbschliessen(true);
        buero.setDarfTelefonSehen(true);
        Mitarbeiter aktiv = mitarbeiter(13L, "Anna", "Aktiv", montage, buero);
        Mitarbeiter inaktiv = mitarbeiter(14L, "Ida", "Inaktiv", buero);
        inaktiv.setAktiv(false);
        when(profile.findAll()).thenReturn(List.of(benutzer(5L, "Anna Aktiv", false, aktiv), benutzer(6L, "Ida Inaktiv", false, inaktiv)));

        RechteUebersichtDto d = service.uebersicht();
        Benutzer a = finde(d, "Anna Aktiv");
        Benutzer i = finde(d, "Ida Inaktiv");

        assertThat(a.felder().get("EINGANGSRECHNUNGEN_SEHEN").grund()).isEqualTo("Über die Abteilungen Büro, Montage.");
        assertThat(a.felder().get("EINGANGSRECHNUNGEN_GENEHMIGEN").grund()).isEqualTo("Über die Abteilung Büro.");
        assertThat(a.felder().get("MONATSABSCHLUSS").stufe()).isEqualTo("ALLES");
        assertThat(a.felder().get("TELEFON").text()).isEqualTo("Ja");
        assertThat(i.felder().get("TELEFON").stufe()).isEqualTo("NICHTS");
        assertThat(i.felder().get("TELEFON").grund()).contains("nicht aktiv");
        assertThat(i.felder().get("EINGANGSRECHNUNGEN_GENEHMIGEN").stufe()).isEqualTo("ALLES");
    }

    @Test
    @DisplayName("Belege: nur Sehen und nur Scannen werden unterschieden")
    void belege() {
        Mitarbeiter sehen = mitarbeiter(15L, "Sven", "Sehen", buero);
        Mitarbeiter scannen = mitarbeiter(16L, "Sara", "Scannen", buero);
        when(belegService.getPermissions(sehen)).thenReturn(BelegDto.PermissionResponse.builder().darfSehen(true).build());
        when(belegService.getPermissions(scannen)).thenReturn(BelegDto.PermissionResponse.builder().darfScannen(true).build());
        when(profile.findAll()).thenReturn(List.of(benutzer(7L, "Sven Sehen", false, sehen), benutzer(8L, "Sara Scannen", false, scannen)));

        RechteUebersichtDto d = service.uebersicht();

        assertThat(finde(d, "Sven Sehen").felder().get("BELEGE").text()).isEqualTo("Nur sehen");
        assertThat(finde(d, "Sven Sehen").felder().get("BELEGE").stufe()).isEqualTo("TEILWEISE");
        assertThat(finde(d, "Sara Scannen").felder().get("BELEGE").text()).isEqualTo("Nur scannen");
    }

    @Test
    @DisplayName("Lieferantendokumente: Arten aus den Abteilungsrechten; alle Arten ist Alles, einige sind Teilweise, keine ist Nein")
    void dokumente() {
        Mitarbeiter teil = mitarbeiter(17L, "Tom", "Teil", buero);
        Mitarbeiter keine = mitarbeiter(18L, "Karl", "Keine", montage);
        when(dokumentRechte.findSichtbareTypenByAbteilungIds(List.of(2L))).thenReturn(List.of("RECHNUNG", "LIEFERSCHEIN", "BELEG"));
        when(dokumentRechte.findSichtbareTypenByAbteilungIds(List.of(1L))).thenReturn(List.of("BELEG"));
        when(profile.findAll()).thenReturn(List.of(benutzer(9L, "Tom Teil", false, teil), benutzer(10L, "Karl Keine", false, keine)));

        RechteUebersichtDto d = service.uebersicht();

        assertThat(finde(d, "Tom Teil").felder().get("DOKUMENTE").stufe()).isEqualTo("TEILWEISE");
        assertThat(finde(d, "Tom Teil").felder().get("DOKUMENTE").text()).isEqualTo("Lieferscheine, Rechnungen");
        assertThat(finde(d, "Karl Keine").felder().get("DOKUMENTE").stufe()).isEqualTo("NICHTS");
    }

    @Test
    @DisplayName("Warnung, wenn niemand ein Recht hat (Monate abschließen) oder kein Administrator aktiv ist; inaktive Benutzer fehlen in der Übersicht")
    void niemandUndKeinAdmin() {
        Mitarbeiter m = mitarbeiter(19L, "Uwe", "Normal", montage);
        FrontendUserProfile inaktiv = benutzer(12L, "Gesperrt Gabi", false, m);
        inaktiv.setActive(false);
        when(profile.findAll()).thenReturn(List.of(benutzer(11L, "Uwe Normal", false, m), inaktiv));

        RechteUebersichtDto d = service.uebersicht();

        assertThat(d.benutzer()).extracting(Benutzer::anzeigename).containsExactly("Uwe Normal");
        assertThat(d.warnungen()).extracting(w -> w.art()).contains("NIEMAND", "KEIN_ADMIN");
        assertThat(d.warnungen()).anySatisfy(w -> assertThat(w.text()).contains("Niemand darf „Monate abschließen“").contains("Monat der Zeiterfassung"));
        assertThat(d.warnungen()).anySatisfy(w -> assertThat(w.text()).isEqualTo("Es gibt keinen aktiven Administrator. Niemand kann dann Einstellungen, Benutzer und Rechte ändern."));
        assertThat(d.warnungen()).noneMatch(w -> w.text().contains("Gesperrt Gabi"));
    }

    @Test
    @DisplayName("Die Spalten enthalten alle Funktionen der Rechteverwaltung mit lesbaren Namen, sortiert nach Gruppe; Benutzer alphabetisch")
    void bereicheUndSortierung() {
        when(profile.findAll()).thenReturn(List.of(benutzer(1L, "Zora Z", true, null), benutzer(2L, "adam a", true, null)));

        RechteUebersichtDto d = service.uebersicht();

        assertThat(d.bereiche()).extracting(b -> b.schluessel()).contains("EINSTELLUNGEN", "BELEGE", "MONATSABSCHLUSS", "TELEFON", "DOKUMENTE")
                .containsAll(java.util.Arrays.stream(Funktion.values()).map(f -> "FUNKTION_" + f.name()).toList());
        assertThat(d.bereiche()).allSatisfy(b -> assertThat(b.name()).doesNotContain("BELEG").doesNotContain("_"));
        assertThat(d.benutzer()).extracting(Benutzer::anzeigename).containsExactly("adam a", "Zora Z");
        assertThat(d.benutzer().get(0).felder().keySet()).containsAll(d.bereiche().stream().map(b -> b.schluessel()).toList());
    }
}
