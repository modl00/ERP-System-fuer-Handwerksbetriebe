package org.example.kalkulationsprogramm.service.planung;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.example.kalkulationsprogramm.service.planung.PlanungsRechner.Kante;
import org.example.kalkulationsprogramm.service.planung.PlanungsStunden.Eingang;
import org.example.kalkulationsprogramm.service.planung.PlanungsStunden.Ergebnis;
import org.example.kalkulationsprogramm.service.planung.PlanungsStunden.Kapazitaet;
import org.example.kalkulationsprogramm.service.planung.PlanungsStunden.Plan;
import org.junit.jupiter.api.Test;

/** Die Stundenrechnung der Plantafel mit den Beispielen aus dem Entwurf (Issue #331, Abschnitt 4). Nur Dummy-Daten. */
class PlanungsStundenTest {

    // Montag, 12. Oktober 2026
    private static final LocalDate MO = LocalDate.of(2026, 10, 12);
    private static final long MAX = 1;
    private static final long ERIKA = 2;

    /** Acht Stunden Montag bis Freitag; einzelne Tage lassen sich sperren. */
    private static class TestKapazitaet implements Kapazitaet {
        final Map<Long, Map<LocalDate, String>> gesperrt = new HashMap<>();
        final Set<LocalDate> frei = new HashSet<>();
        final Map<Long, Set<DayOfWeek>> buero = new HashMap<>();

        void sperre(long person, LocalDate tag, String grund) {
            gesperrt.computeIfAbsent(person, x -> new HashMap<>()).put(tag, grund);
        }

        @Override
        public boolean arbeitstag(LocalDate tag) {
            return tag.getDayOfWeek().getValue() <= 5 && !frei.contains(tag);
        }

        @Override
        public BigDecimal stunden(Long person, LocalDate tag, boolean bueroArbeit) {
            if (!arbeitstag(tag) || grund(person, tag, bueroArbeit) != null) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal("8");
        }

        @Override
        public String grund(Long person, LocalDate tag, boolean bueroArbeit) {
            if (!arbeitstag(tag)) {
                return null;
            }
            String g = gesperrt.getOrDefault(person, Map.of()).get(tag);
            if (g != null) {
                return g;
            }
            return !bueroArbeit && buero.getOrDefault(person, Set.of()).contains(tag.getDayOfWeek()) ? "Bürotag" : null;
        }

        @Override
        public BigDecimal standard(LocalDate tag) {
            return arbeitstag(tag) ? new BigDecimal("8") : BigDecimal.ZERO;
        }

        @Override
        public String name(Long person) {
            return person == MAX ? "Max" : person == ERIKA ? "Erika" : "Person " + person;
        }
    }

    private static Eingang vorgang(long id, long projekt, String prio, double stunden, Set<Long> personen, Kante... vorgaenger) {
        return new Eingang(id, projekt, "Vorgang " + id, "Auftrag " + (char) ('A' + projekt - 1), prio, null, null, false, false, BigDecimal.valueOf(stunden), 0, null, null, null,
                personen, List.of(vorgaenger), null, null, (int) id);
    }

    private static Eingang warte(long id, long projekt, int tage, Kante... vorgaenger) {
        return new Eingang(id, projekt, "Warten " + id, "Auftrag " + (char) ('A' + projekt - 1), "NORMAL", null, null, true, false, null, tage, null, null, null,
                Set.of(), List.of(vorgaenger), null, null, (int) id);
    }

    private static Plan rechne(TestKapazitaet kap, Eingang... e) {
        return PlanungsStunden.berechne(List.of(e), MO, kap);
    }

    @Test
    void beispiel1ZweiMitarbeiterSchaffenDoppeltSoViel() {
        Plan p = rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 48, Set.of(MAX, ERIKA)), vorgang(2, 1, "NORMAL", 8, Set.of(MAX), new Kante(1L, 0)));

        assertThat(p.vorgaenge().get(1L)).extracting(Ergebnis::start, Ergebnis::ende).containsExactly(MO, MO.plusDays(2)); // Mo bis Mi
        assertThat(p.vorgaenge().get(2L).start()).as("Montage beginnt am nächsten Arbeitstag").isEqualTo(MO.plusDays(3));
    }

    @Test
    void beispiel2NurEinerBrauchtSechsTage() {
        Plan p = rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 48, Set.of(MAX)), vorgang(2, 1, "NORMAL", 8, Set.of(MAX), new Kante(1L, 0)));

        assertThat(p.vorgaenge().get(1L).ende()).isEqualTo(MO.plusDays(7)); // Mo 19.10.
        assertThat(p.vorgaenge().get(2L).start()).isEqualTo(MO.plusDays(8)); // Di 20.10.
    }

    @Test
    void beispiel3DringenderAuftragNimmtErikaHeraus() {
        // B (dringend) braucht Erika am Dienstag ganztägig, A (normal) ist 48 Stunden mit Max und Erika
        Plan p = rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 48, Set.of(MAX, ERIKA)), vorgang(2, 2, "DRINGEND", 8, Set.of(ERIKA)));
        // Ohne Verschiebung läge B am Montag; wir legen B über einen frühesten Beginn auf den Dienstag
        Eingang b = new Eingang(2L, 2L, "Dringend", "Auftrag B", "DRINGEND", null, null, false, false, BigDecimal.valueOf(8), 0, null, MO.plusDays(1), null, Set.of(ERIKA), List.of(), null, null, 2);
        Plan mitB = rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 48, Set.of(MAX, ERIKA)), b);

        assertThat(mitB.vorgaenge().get(2L)).extracting(Ergebnis::start, Ergebnis::ende).containsExactly(MO.plusDays(1), MO.plusDays(1));
        assertThat(mitB.vorgaenge().get(1L).ende()).as("Mo 16, Di 8, Mi 16, Do 8").isEqualTo(MO.plusDays(3));
        assertThat(mitB.vorgaenge().get(1L).hinweise()).anyMatch(h -> h.startsWith("Erika: Auftrag B (dringend) am 13.10."));
        assertThat(p.vorgaenge().get(2L).start()).as("ohne frühesten Beginn plant B am Montag").isEqualTo(MO);
    }

    @Test
    void beispiel4UrlaubUndBuerotagNehmenKapazitaetWeg() {
        TestKapazitaet urlaub = new TestKapazitaet();
        urlaub.sperre(ERIKA, MO.plusDays(1), "Urlaub");
        Plan p = rechne(urlaub, vorgang(1, 1, "NORMAL", 48, Set.of(MAX, ERIKA)));
        assertThat(p.vorgaenge().get(1L).ende()).isEqualTo(MO.plusDays(3));
        assertThat(p.vorgaenge().get(1L).hinweise()).containsExactly("Erika: Urlaub am 13.10.");

        TestKapazitaet buero = new TestKapazitaet();
        buero.buero.put(ERIKA, Set.of(DayOfWeek.MONDAY));
        Plan q = rechne(buero, vorgang(1, 1, "NORMAL", 48, Set.of(MAX, ERIKA)));
        assertThat(q.vorgaenge().get(1L).hinweise()).containsExactly("Erika: Bürotag am 12.10.");
    }

    @Test
    void beispiel5EinMitarbeiterZweiAuftraegeMitTeiltagen() {
        // C (12 h) zuerst nach Wahl des Planers, dann A (12 h): Max arbeitet nie an beiden zugleich, der Tag wird geteilt
        Eingang c = new Eingang(1L, 3L, "C", "Auftrag C", "NORMAL", 1, null, false, false, BigDecimal.valueOf(12), 0, null, null, null, Set.of(MAX), List.of(), null, null, 1);
        Eingang a = new Eingang(2L, 1L, "A", "Auftrag A", "NORMAL", 2, null, false, false, BigDecimal.valueOf(12), 0, null, null, null, Set.of(MAX), List.of(), null, null, 2);
        Plan p = rechne(new TestKapazitaet(), a, c);

        assertThat(p.vorgaenge().get(1L)).extracting(Ergebnis::start, Ergebnis::ende).containsExactly(MO, MO.plusDays(1));
        assertThat(p.vorgaenge().get(2L)).extracting(Ergebnis::start, Ergebnis::ende).containsExactly(MO.plusDays(1), MO.plusDays(2));
        BigDecimal dienstag = p.zuteilungen().stream().filter(z -> z.tag().equals(MO.plusDays(1))).map(PlanungsStunden.Zuteilung::stunden).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(dienstag).as("nie mehr als ein Arbeitstag").isEqualByComparingTo("8");
    }

    @Test
    void ohneZuweisungRechnetMitDemStandardwertUndKennzeichnetGeschaetzt() {
        Plan p = rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 16, Set.of()));
        assertThat(p.vorgaenge().get(1L)).extracting(Ergebnis::ende, Ergebnis::geschaetzt).containsExactly(MO.plusDays(1), true);
    }

    @Test
    void zweiAuftraegeOhneZuweisungBehindernSichNicht() {
        Plan p = rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 8, Set.of()), vorgang(2, 2, "NORMAL", 8, Set.of()));
        assertThat(p.vorgaenge().get(1L).ende()).isEqualTo(MO);
        assertThat(p.vorgaenge().get(2L).ende()).isEqualTo(MO);
    }

    @Test
    void dringendVorNormalVorNichtDringend() {
        Plan p = rechne(new TestKapazitaet(), vorgang(1, 1, "NICHT_DRINGEND", 8, Set.of(MAX)), vorgang(2, 2, "NORMAL", 8, Set.of(MAX)), vorgang(3, 3, "DRINGEND", 8, Set.of(MAX)));
        assertThat(p.vorgaenge().get(3L).start()).isEqualTo(MO);
        assertThat(p.vorgaenge().get(2L).start()).isEqualTo(MO.plusDays(1));
        assertThat(p.vorgaenge().get(1L).start()).isEqualTo(MO.plusDays(2));
    }

    @Test
    void dasMaterialVorEinemDringendenVorgangIstAuchDringend() {
        // Material (normal) vor der Fertigung (dringend) darf nicht hinter einem anderen normalen Vorgang warten
        Eingang material = new Eingang(1L, 1L, "Material", "Auftrag A", "NORMAL", null, null, false, false, BigDecimal.valueOf(8), 0, null, null, null, Set.of(MAX), List.of(), null, null, 1);
        Eingang fertigung = new Eingang(2L, 1L, "Fertigung", "Auftrag A", "DRINGEND", null, null, false, false, BigDecimal.valueOf(8), 0, null, null, null, Set.of(MAX), List.of(new Kante(1L, 0)), null, null, 2);
        Plan p = rechne(new TestKapazitaet(), vorgang(3, 2, "NORMAL", 8, Set.of(MAX)), material, fertigung);
        assertThat(p.vorgaenge().get(1L).start()).isEqualTo(MO);
        assertThat(p.vorgaenge().get(3L).start()).isEqualTo(MO.plusDays(2));
    }

    @Test
    void einWartevorgangBrauchtKeinePersonUndZaehltArbeitstage() {
        Plan p = rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 8, Set.of(MAX)), warte(2, 1, 5, new Kante(1L, 0)), vorgang(3, 1, "NORMAL", 8, Set.of(MAX), new Kante(2L, 0)));
        assertThat(p.vorgaenge().get(2L)).extracting(Ergebnis::start, Ergebnis::ende).containsExactly(MO.plusDays(1), MO.plusDays(7)); // Di bis Mo
        assertThat(p.vorgaenge().get(3L).start()).isEqualTo(MO.plusDays(8));
    }

    @Test
    void einFesterTerminBleibtUndNimmtAnderenDieZeit() {
        Eingang fest = new Eingang(1L, 2L, "Fix", "Auftrag B", "NICHT_DRINGEND", null, null, false, false, BigDecimal.valueOf(8), 0, MO.plusDays(1), null, null, Set.of(MAX), List.of(), null, null, 1);
        Plan p = rechne(new TestKapazitaet(), vorgang(2, 1, "DRINGEND", 16, Set.of(MAX)), fest);
        assertThat(p.vorgaenge().get(1L)).extracting(Ergebnis::start, Ergebnis::ende, Ergebnis::fixiert).containsExactly(MO.plusDays(1), MO.plusDays(1), true);
        assertThat(p.vorgaenge().get(2L).ende()).as("der dringende Vorgang weicht dem Fixtermin aus: Mo 8, Mi 8").isEqualTo(MO.plusDays(2));
    }

    @Test
    void einFesterStartVorDemEndeDerVorgaengerIstEinKonfliktUndWirdNichtVerschoben() {
        Eingang fest = new Eingang(2L, 1L, "Montage", "Auftrag A", "NORMAL", null, null, false, false, BigDecimal.valueOf(8), 0, MO.plusDays(1), null, null, Set.of(ERIKA), List.of(new Kante(1L, 0)), null, null, 2);
        Plan p = rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 24, Set.of(MAX)), fest);
        assertThat(p.vorgaenge().get(2L)).extracting(Ergebnis::start, Ergebnis::konflikt).containsExactly(MO.plusDays(1), true);
    }

    @Test
    void frueheestensAbVerschiebtNurNachHinten() {
        Eingang e = new Eingang(1L, 1L, "A", "Auftrag A", "NORMAL", null, null, false, false, BigDecimal.valueOf(8), 0, null, MO.plusDays(3), null, Set.of(MAX), List.of(), null, null, 1);
        assertThat(rechne(new TestKapazitaet(), e).vorgaenge().get(1L).start()).isEqualTo(MO.plusDays(3));
    }

    @Test
    void erledigteVorgaengeBehaltenIhreTermineUndBelegenNichts() {
        Eingang fertig = new Eingang(1L, 1L, "Fertig", "Auftrag A", "NORMAL", null, null, false, false, BigDecimal.valueOf(40), 0, null, null, null, Set.of(MAX), List.of(), MO.minusDays(7), MO.minusDays(3), 1);
        Plan p = rechne(new TestKapazitaet(), fertig, vorgang(2, 1, "NORMAL", 8, Set.of(MAX), new Kante(1L, 0)));
        assertThat(p.vorgaenge().get(1L)).extracting(Ergebnis::start, Ergebnis::ende).containsExactly(MO.minusDays(7), MO.minusDays(3));
        assertThat(p.vorgaenge().get(2L).start()).isEqualTo(MO);
        assertThat(p.zuteilungen()).noneMatch(z -> z.vorgangId() == 1L);
    }

    @Test
    void ohneJedeKapazitaetIstDerVorgangNichtPlanbar() {
        TestKapazitaet nie = new TestKapazitaet() {
            @Override
            public BigDecimal stunden(Long person, LocalDate tag, boolean b) {
                return BigDecimal.ZERO;
            }
        };
        Plan p = rechne(nie, vorgang(1, 1, "NORMAL", 8, Set.of(MAX)));
        assertThat(p.vorgaenge().get(1L).planbar()).isFalse();
        assertThat(p.vorgaenge().get(1L).hinweise()).anyMatch(h -> h.startsWith("Keine freie Zeit"));
    }

    @Test
    void einKreisWirdMitKlartextAbgelehnt() {
        assertThatThrownBy(() -> rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 8, Set.of(), new Kante(2L, 0)), vorgang(2, 1, "NORMAL", 8, Set.of(), new Kante(1L, 0))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("im Kreis");
        assertThatThrownBy(() -> rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 8, Set.of(), new Kante(9L, 0)))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Vorgänger");
    }

    @Test
    void meilensteinHatKeineDauer() {
        Plan p = rechne(new TestKapazitaet(), vorgang(1, 1, "NORMAL", 0, Set.of(MAX)));
        assertThat(p.vorgaenge().get(1L)).extracting(Ergebnis::start, Ergebnis::ende).containsExactly(MO, MO);
        assertThat(new ArrayList<>(p.zuteilungen())).isEmpty();
    }

    private static Eingang lieferung(long id, int tage, LocalDate bestellt, LocalDate fix, Kante... vorgaenger) {
        return new Eingang(id, 1L, "Material " + id, "Auftrag A", "NORMAL", null, null, true, false, null, tage, null, null, null, Set.of(), List.of(vorgaenger), null, null, (int) id, bestellt, fix);
    }

    @Test
    void dieLieferzeitZaehltAbDerBestellungAuchWennDieSchonVorHeuteWar() {
        // bestellt am Mittwoch vor dem Start (7.10.), fünf Arbeitstage: Mi, Do, Fr, Mo, Di = Ende Di 13.10.
        Plan p = rechne(new TestKapazitaet(), lieferung(1, 5, MO.minusDays(5), null), vorgang(2, 1, "NORMAL", 8, Set.of(MAX), new Kante(1L, 0)));

        assertThat(p.vorgaenge().get(1L)).extracting(Ergebnis::start, Ergebnis::ende).containsExactly(MO.minusDays(5), MO.plusDays(1));
        assertThat(p.vorgaenge().get(2L).start()).as("die Folge beginnt am nächsten Arbeitstag nach der Lieferung").isEqualTo(MO.plusDays(2));
    }

    @Test
    void derBestaetigteLiefertermindesLieferantenIstDasEndeNichtDieDauer() {
        // Dauer 5 Tage würde Fr 16.10. ergeben, der Lieferant bestätigt Mi 14.10.
        Plan frueher = rechne(new TestKapazitaet(), lieferung(1, 5, null, MO.plusDays(2)));
        assertThat(frueher.vorgaenge().get(1L)).extracting(Ergebnis::start, Ergebnis::ende).containsExactly(MO, MO.plusDays(2));

        // bestätigt Mo 19.10., obwohl nur 2 Tage veranschlagt waren: Das Ende ist der bestätigte Tag
        Plan spaeter = rechne(new TestKapazitaet(), lieferung(1, 2, null, MO.plusDays(7)), vorgang(2, 1, "NORMAL", 8, Set.of(MAX), new Kante(1L, 0)));
        assertThat(spaeter.vorgaenge().get(1L).ende()).isEqualTo(MO.plusDays(7));
        assertThat(spaeter.vorgaenge().get(2L).start()).isEqualTo(MO.plusDays(8));
    }

    @Test
    void einLiefertermindenAmWochenendeRutschtAufDenMontagUndLiegtNieVorDemBeginn() {
        Plan p = rechne(new TestKapazitaet(), lieferung(1, 3, null, MO.plusDays(5)));
        assertThat(p.vorgaenge().get(1L).ende()).as("Samstag wird Montag").isEqualTo(MO.plusDays(7));

        Plan zuFrueh = rechne(new TestKapazitaet(), lieferung(1, 3, null, MO.minusDays(3)));
        assertThat(zuFrueh.vorgaenge().get(1L).ende()).isEqualTo(MO);
    }
}
