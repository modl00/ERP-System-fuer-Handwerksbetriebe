package org.example.kalkulationsprogramm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;

import org.example.kalkulationsprogramm.domain.Arbeitsgang;
import org.example.kalkulationsprogramm.domain.Leistung;
import org.example.kalkulationsprogramm.domain.LeistungPosition;
import org.example.kalkulationsprogramm.repository.ArbeitsgangRepository;
import org.example.kalkulationsprogramm.repository.ArtikelRepository;
import org.example.kalkulationsprogramm.repository.FremdleistungRepository;
import org.example.kalkulationsprogramm.repository.LeistungRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.example.kalkulationsprogramm.repository.MaschineRepository;
import org.example.kalkulationsprogramm.repository.ProduktkategorieRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** Die mitgelieferten Leistungen für Metallbau: Der Katalog ist in sich stimmig, das Übernehmen legt alles Nötige an und ein zweiter Lauf nichts doppelt. Nur Dummy-Daten. */
@DataJpaTest
@Import({ MetallbauLeistungenService.class, MetallbauVorlagenService.class, ProfilTabelle.class, ArtikelService.class, LeistungKalkulationService.class, MaschineService.class,
        MetallbauLeistungenServiceTest.Uhr.class })
class MetallbauLeistungenServiceTest {

    @TestConfiguration
    static class Uhr {
        @Bean
        Clock uhr() {
            return Clock.systemDefaultZone();
        }

        @Bean
        ArtikelPositionsPreisService artikelPositionsPreisService() {
            return new ArtikelPositionsPreisService();
        }
    }

    @Autowired
    private MetallbauLeistungenService service;
    @Autowired
    private LeistungRepository leistungen;
    @Autowired
    private ArtikelRepository artikel;
    @Autowired
    private ArbeitsgangRepository arbeitsgaenge;
    @Autowired
    private MaschineRepository maschinen;
    @Autowired
    private ProduktkategorieRepository kategorien;
    @Autowired
    private FremdleistungRepository fremdleistungen;
    @Autowired
    private LieferantenRepository lieferanten;
    @Autowired
    private LeistungKalkulationService kalkulation;

    @Test
    void derKatalogIstInSichStimmigUndEnthaeltEchteVerschachtelung() {
        var k = service.katalog();

        assertThat(k.leistungen()).hasSizeGreaterThanOrEqualTo(15);
        assertThat(k.leistungen()).filteredOn(MetallbauLeistungenService.LeistungVorlage::baugruppe).hasSizeGreaterThanOrEqualTo(6);
        assertThat(k.leistungen()).extracting(MetallbauLeistungenService.LeistungVorlage::name).doesNotHaveDuplicates();
        assertThat(k.leistungen().stream().flatMap(l -> l.positionen().stream()).map(MetallbauLeistungenService.PositionVorlage::typ).distinct()).containsExactlyInAnyOrder("ARTIKEL", "ARBEIT", "BAUGRUPPE", "FREMDLEISTUNG");
        assertThat(k.fremdleistungen()).extracting(MetallbauLeistungenService.FremdVorlage::bezeichnung).contains("Feuerverzinken", "Pulverbeschichten, einschichtig");
        assertThat(k.maschinen()).hasSizeGreaterThanOrEqualTo(6);
    }

    @Test
    void dieVorschauZeigtLeistungenBaugruppenUndDieErklaerungDerFremdleistungen() {
        var v = service.vorschau();

        assertThat(v.leistungen()).extracting(MetallbauLeistungenService.Zeile::name).contains("Geländer, Stahl, feuerverzinkt", "Stahltreppe, gerade, 12 Stufen, feuerverzinkt");
        var gelaender = v.leistungen().stream().filter(z -> z.name().equals("Geländer, Stahl, gestrichen")).findFirst().orElseThrow();
        assertThat(gelaender.enthaelt()).hasSize(3);
        assertThat(gelaender.vorhanden()).isFalse();
        assertThat(v.fremdleistungen()).anySatisfy(f -> assertThat(f.erklaerung()).contains("Ladung").contains("Chargen"));
    }

    @Test
    @Transactional
    void uebernehmenLegtAllesNoetigeAnUndRechnetDieKalkulationDurch() {
        var e = service.uebernehmen(List.of("Geländer, Stahl, feuerverzinkt"));

        assertThat(e.leistungen()).as("das Geländer und seine drei Baugruppen").isEqualTo(4);
        assertThat(e.uebersprungen()).isZero();
        assertThat(e.arbeitsgaenge()).isPositive();
        assertThat(e.artikel()).isGreaterThanOrEqualTo(4);
        assertThat(e.maschinen()).isPositive();
        assertThat(e.lieferanten()).isEqualTo(1);
        assertThat(e.fremdleistungen()).isEqualTo(1);
        assertThat(e.kategorien()).isPositive();

        Leistung gelaender = leistungen.findAll().stream().filter(l -> l.getBezeichnung().equals("Geländer, Stahl, feuerverzinkt")).findFirst().orElseThrow();
        assertThat(gelaender.isTemporaer()).isFalse();
        assertThat(gelaender.isBaugruppe()).isFalse();
        assertThat(gelaender.getKategorie()).isNotNull();
        assertThat(gelaender.getKategorie().getBezeichnung()).isEqualTo("Geländer");
        assertThat(gelaender.getKategorie().getUebergeordneteKategorie().getBezeichnung()).isEqualTo("Geländer und Handläufe");
        assertThat(gelaender.getPositionen()).extracting(LeistungPosition::getTyp)
                .contains(LeistungPosition.Typ.BAUGRUPPE, LeistungPosition.Typ.ARBEIT, LeistungPosition.Typ.FREMDLEISTUNG);
        assertThat(gelaender.getPositionen().stream().filter(p -> p.getTyp() == LeistungPosition.Typ.BAUGRUPPE)).allSatisfy(p -> assertThat(p.getBaugruppe().isBaugruppe()).isTrue());
        assertThat(gelaender.getPositionen().stream().filter(p -> p.getTyp() == LeistungPosition.Typ.FREMDLEISTUNG).findFirst().orElseThrow().getMengeAus()).isEqualTo("GEWICHT");
        assertThat(gelaender.getZuschlagProzent()).isEqualByComparingTo("15");
        assertThat(gelaender.getLosgroesse()).isEqualByComparingTo("10");

        var pfosten = leistungen.findAll().stream().filter(l -> l.getBezeichnung().startsWith("Geländerpfosten")).findFirst().orElseThrow();
        assertThat(pfosten.isBaugruppe()).isTrue();
        assertThat(pfosten.getPositionen().stream().filter(p -> p.getTyp() == LeistungPosition.Typ.ARTIKEL)).extracting(p -> p.getArtikel().getArtikelnummer()).contains("VL-QR-40x3", "VL-BL-8");
        // Die Kalkulation rechnet durch (ohne Preise, die Hinweise sagen, was fehlt)
        var stand = kalkulation.fuerAngebotsmenge(gelaender.getId(), new BigDecimal("12"));
        assertThat(stand.positionen()).isNotEmpty();
        assertThat(stand.positionen().stream().filter(p -> "BAUGRUPPE".equals(p.typ()))).hasSize(3);
        // Maschinen sind mit ihren Arbeitsgängen verbunden
        Arbeitsgang schweissen = arbeitsgaenge.findByBeschreibung("Schweißen (MAG)").orElseThrow();
        assertThat(schweissen.getMaschine()).isNotNull();
        assertThat(schweissen.getMaschine().getName()).isEqualTo("MAG-Schweißanlage (Beispiel)");
        assertThat(schweissen.getMaschine().getSatzVonHand()).isEqualByComparingTo("15.00");
    }

    @Test
    @Transactional
    void einZweiterLaufLegtNichtsDoppeltAn() {
        service.uebernehmen(List.of("Geländer, Stahl, pulverbeschichtet"));
        long leistungenVorher = leistungen.count();
        long artikelVorher = artikel.count();
        long gaengeVorher = arbeitsgaenge.count();
        long maschinenVorher = maschinen.count();
        long kategorienVorher = kategorien.count();
        long fremdVorher = fremdleistungen.count();
        long lieferantenVorher = lieferanten.count();

        var zweiter = service.uebernehmen(List.of("Geländer, Stahl, pulverbeschichtet"));

        assertThat(zweiter.leistungen()).isZero();
        assertThat(zweiter.uebersprungen()).isEqualTo(4);
        assertThat(zweiter.artikel()).isZero();
        assertThat(zweiter.arbeitsgaenge()).isZero();
        assertThat(zweiter.maschinen()).isZero();
        assertThat(leistungen.count()).isEqualTo(leistungenVorher);
        assertThat(artikel.count()).isEqualTo(artikelVorher);
        assertThat(arbeitsgaenge.count()).isEqualTo(gaengeVorher);
        assertThat(maschinen.count()).isEqualTo(maschinenVorher);
        assertThat(kategorien.count()).isEqualTo(kategorienVorher);
        assertThat(fremdleistungen.count()).isEqualTo(fremdVorher);
        assertThat(lieferanten.count()).isEqualTo(lieferantenVorher);
    }

    @Test
    @Transactional
    void gemeinsameBaugruppenUndArtikelWerdenNurEinmalAngelegt() {
        var e = service.uebernehmen(List.of("Geländer, Stahl, gestrichen", "Geländer, Stahl, feuerverzinkt", "Handlauf an der Wand, Rundrohr, gebogen"));

        assertThat(e.leistungen()).as("drei Leistungen plus Pfosten, Handlauf, Füllstäbe, Wandkonsole").isEqualTo(7);
        assertThat(leistungen.findAll().stream().filter(l -> l.getBezeichnung().startsWith("Handlauf, Rundrohr")).count()).isEqualTo(1);
        assertThat(e.lieferanten()).isEqualTo(1);
    }

    @Test
    @Transactional
    void jedeLeistungDesKatalogsLaesstSichUebernehmenUndKalkulieren() {
        var namen = service.katalog().leistungen().stream().filter(l -> !l.baugruppe()).map(MetallbauLeistungenService.LeistungVorlage::name).toList();

        var e = service.uebernehmen(namen);

        assertThat(e.leistungen()).isEqualTo(service.katalog().leistungen().size());
        for (Leistung l : leistungen.findAll()) {
            var stand = kalkulation.fuerAngebotsmenge(l.getId(), BigDecimal.ONE);
            assertThat(stand.positionen()).as(l.getBezeichnung()).hasSameSizeAs(l.getPositionen());
        }
    }

    @Test
    void eingabenWerdenGeprueft() {
        assertThatThrownBy(() -> service.uebernehmen(null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mindestens eine Leistung");
        assertThatThrownBy(() -> service.uebernehmen(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.uebernehmen(List.of("Gibt es nicht"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("gibt es im Katalog nicht");
    }
}
