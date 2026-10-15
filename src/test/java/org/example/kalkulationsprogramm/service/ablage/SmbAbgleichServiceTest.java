package org.example.kalkulationsprogramm.service.ablage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.ablage.LesbareAblageService.Eintrag;
import org.example.kalkulationsprogramm.service.ablage.SmbAbgleichService.Ergebnis;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/** Der Abgleich in beide Richtungen, gegen einen Ordner als Freigabe. Nur Dummy-Daten (DSGVO). */
class SmbAbgleichServiceTest {

    static final String PROJEKT = "Projekte/2026/Max Mustermann GmbH/2026-10-00014 - Anbau Halle";
    static final String PROJEKT_ALT = "Projekte/2026/2026-10-00014 - Anbau Halle";

    @TempDir Path wurzel;
    Path app;
    Path freigabe;
    LesbareAblageService ablage = mock(LesbareAblageService.class);
    ProjektRepository projekte = mock(ProjektRepository.class);
    DateiSpeicherService dateien = mock(DateiSpeicherService.class);
    SmbAbgleichService service;
    SmbAbgleichEinstellungenService einstellungen = mock(SmbAbgleichEinstellungenService.class);
    OrdnerAbgleichSpeicher speicher;
    List<Eintrag> eintraege = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        app = Files.createDirectories(wurzel.resolve("app"));
        freigabe = Files.createDirectories(wurzel.resolve("freigabe"));
        Projekt p = new Projekt();
        p.setId(7L);
        p.setAuftragsnummer("2026/10/00014");
        p.setBauvorhaben("Anbau Halle");
        p.setAnlegedatum(LocalDate.of(2026, 10, 1));
        org.example.kalkulationsprogramm.domain.Kunde kunde = new org.example.kalkulationsprogramm.domain.Kunde();
        kunde.setName("Max Mustermann GmbH");
        p.setKundenId(kunde);
        when(projekte.findAll()).thenReturn(List.of(p));
        when(ablage.eintraege()).thenAnswer(a -> eintraege);
        service = new SmbAbgleichService(ablage, projekte, dateien, einstellungen);
        speicher = new OrdnerAbgleichSpeicher(freigabe);
    }

    /** Ein Dokument in der App, das unter dem lesbaren Pfad erscheinen soll. */
    private Path appDatei(String relativ, String inhalt) throws IOException {
        Path q = app.resolve(java.util.UUID.randomUUID() + ".dat");
        Files.writeString(q, inhalt);
        eintraege.add(new Eintrag(q, relativ));
        return q;
    }

    private Path geraetDatei(String relativ, String inhalt, long minutenAlt) throws IOException {
        Path p = freigabe.resolve(relativ);
        Files.createDirectories(p.getParent());
        Files.writeString(p, inhalt);
        Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis() - minutenAlt * 60_000));
        return p;
    }

    private static void spaeter(Path p, long sekunden) throws IOException {
        Files.setLastModifiedTime(p, FileTime.fromMillis(Files.getLastModifiedTime(p).toMillis() + sekunden * 1000));
    }

    @Test
    void neuesDokumentWirdHochgeladenUndDannNichtNochEinmal() throws IOException {
        appDatei(PROJEKT + "/Planungsdokumente/Plan.pdf", "PLAN");

        Ergebnis erst = service.abgleichen(speicher, false);

        assertThat(erst.hochgeladen()).isEqualTo(1);
        assertThat(Files.readString(freigabe.resolve(PROJEKT + "/Planungsdokumente/Plan.pdf"))).isEqualTo("PLAN");
        assertThat(freigabe.resolve("_erp/abgleich.json")).exists();

        Ergebnis zweit = service.abgleichen(speicher, false);
        assertThat(zweit.hochgeladen()).isZero();
        assertThat(zweit.unveraendert()).isEqualTo(1);
    }

    @Test
    void dieVorschauAendertNichts() throws IOException {
        appDatei(PROJEKT + "/Planungsdokumente/Plan.pdf", "PLAN");
        geraetDatei(PROJEKT + "/Diverse Dokumente/Neu.pdf", "NEU", 60);

        Ergebnis e = service.abgleichen(speicher, true);

        assertThat(e.hochgeladen()).isEqualTo(1);
        assertThat(e.neuVomGeraet()).isEqualTo(1);
        assertThat(freigabe.resolve(PROJEKT + "/Planungsdokumente/Plan.pdf")).doesNotExist();
        assertThat(freigabe.resolve("_erp/abgleich.json")).doesNotExist();
        verify(dateien, never()).speichereErzeugteDatei(any(), any(), anyLong(), any(), any());
    }

    @Test
    void einGeaendertesDokumentInDerAppErsetztDieFassungAufDemGeraet() throws IOException {
        Path q = appDatei(PROJEKT + "/Planungsdokumente/Plan.pdf", "PLAN");
        service.abgleichen(speicher, false);

        Files.writeString(q, "PLAN 2 mit Änderung");
        spaeter(q, 10);
        Ergebnis e = service.abgleichen(speicher, false);

        assertThat(e.hochgeladen()).isEqualTo(1);
        assertThat(Files.readString(freigabe.resolve(PROJEKT + "/Planungsdokumente/Plan.pdf"))).isEqualTo("PLAN 2 mit Änderung");
    }

    @Test
    void eineAufDemGeraetGeaenderteDateiErsetztDenInhaltInDerApp() throws IOException {
        Path q = appDatei(PROJEKT + "/Planungsdokumente/Plan.pdf", "PLAN");
        service.abgleichen(speicher, false);

        Path fern = freigabe.resolve(PROJEKT + "/Planungsdokumente/Plan.pdf");
        Files.writeString(fern, "PLAN vom Büro bearbeitet");
        spaeter(fern, 10);
        Ergebnis e = service.abgleichen(speicher, false);

        assertThat(e.vomGeraetUebernommen()).isEqualTo(1);
        assertThat(Files.readString(q)).isEqualTo("PLAN vom Büro bearbeitet");
        assertThat(service.abgleichen(speicher, false).vomGeraetUebernommen()).as("beim nächsten Mal ist alles gleich").isZero();
    }

    @Test
    void beideSeitenGeaendertSichertDieFassungVomGeraetUndBehaeltDieDerApp() throws IOException {
        Path q = appDatei(PROJEKT + "/Planungsdokumente/Plan.pdf", "PLAN");
        service.abgleichen(speicher, false);
        Files.writeString(q, "APP-Fassung");
        spaeter(q, 10);
        Path fern = freigabe.resolve(PROJEKT + "/Planungsdokumente/Plan.pdf");
        Files.writeString(fern, "GERAET-Fassung!");
        spaeter(fern, 10);

        Ergebnis e = service.abgleichen(speicher, false);

        assertThat(e.konflikte()).isEqualTo(1);
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<byte[]> inhalt = ArgumentCaptor.forClass(byte[].class);
        verify(dateien).speichereErzeugteDatei(inhalt.capture(), name.capture(), eq(7L), eq(DokumentGruppe.PLANUNGSDOKUMENTE), any());
        assertThat(new String(inhalt.getValue())).isEqualTo("GERAET-Fassung!");
        assertThat(name.getValue()).startsWith("Plan (vom Gerät ").endsWith(").pdf");
        assertThat(Files.readString(q)).isEqualTo("APP-Fassung");
        assertThat(Files.readString(fern)).isEqualTo("APP-Fassung");
    }

    @Test
    void eineNeueDateiImProjektordnerWirdAlsDokumentDerRichtigenGruppeUebernommen() throws IOException {
        geraetDatei(PROJEKT + "/Planungsdokumente/Skizze Halle.pdf", "SKIZZE", 60);

        Ergebnis e = service.abgleichen(speicher, false);

        assertThat(e.neuVomGeraet()).isEqualTo(1);
        verify(dateien).speichereErzeugteDatei(any(), eq("Skizze Halle.pdf"), eq(7L), eq(DokumentGruppe.PLANUNGSDOKUMENTE), any());
        assertThat(freigabe.resolve(PROJEKT + "/Planungsdokumente/Skizze Halle.pdf")).as("die Datei bleibt auf dem Gerät").exists();

        Ergebnis zweit = service.abgleichen(speicher, false);
        assertThat(zweit.neuVomGeraet()).as("nicht noch einmal übernehmen").isZero();
        verify(dateien, org.mockito.Mockito.times(1)).speichereErzeugteDatei(any(), any(), anyLong(), any(), any());
    }

    @Test
    void eineDateiDirektImProjektordnerGehtInDiverseDokumente() throws IOException {
        geraetDatei(PROJEKT + "/Foto.jpg", "BILD", 60);
        service.abgleichen(speicher, false);
        verify(dateien).speichereErzeugteDatei(any(), eq("Foto.jpg"), eq(7L), eq(DokumentGruppe.DIVERSE_DOKUMENTE), any());
    }

    @Test
    void ungeeigneteDateienWerdenNichtUebernommenUndErklaert() throws IOException {
        geraetDatei(PROJEKT + "/Diverse Dokumente/setup.exe", "MZ", 60);
        geraetDatei(PROJEKT + "/Diverse Dokumente/~$Angebot.docx", "x", 60);
        geraetDatei(PROJEKT + "/Diverse Dokumente/Thumbs.db", "x", 60);
        geraetDatei(PROJEKT + "/Diverse Dokumente/gerade-kopiert.pdf", "x", 0);
        geraetDatei("Projekte/2026/Max Mustermann GmbH/Unbekannt - Projekt/Diverse Dokumente/Plan.pdf", "x", 60);
        geraetDatei("Sonstiges/Notiz.txt", "x", 60);
        geraetDatei(PROJEKT + "/Ausgangsdokumente/Rechnung 1.pdf", "x", 60);

        Ergebnis e = service.abgleichen(speicher, false);

        assertThat(e.neuVomGeraet()).isZero();
        verify(dateien, never()).speichereErzeugteDatei(any(), any(), anyLong(), any(), any());
        assertThat(String.join("\n", e.hinweise())).contains("setup.exe", "Unbekannt - Projekt", "Sonstiges/Notiz.txt", "Ausgangsdokumente")
                .doesNotContain("Thumbs.db", "~$Angebot", "gerade-kopiert");
    }

    @Test
    void esWirdAufKeinerSeiteEtwasGeloescht() throws IOException {
        Path q = appDatei(PROJEKT + "/Planungsdokumente/Plan.pdf", "PLAN");
        service.abgleichen(speicher, false);

        // auf dem Gerät gelöscht: nicht erneut hochladen, nicht in der App löschen
        Files.delete(freigabe.resolve(PROJEKT + "/Planungsdokumente/Plan.pdf"));
        Ergebnis a = service.abgleichen(speicher, false);
        assertThat(a.hochgeladen()).isZero();
        assertThat(q).exists();
        assertThat(a.hinweise()).anyMatch(h -> h.contains("Auf dem Gerät gelöscht"));

        // in der App nicht mehr vorhanden: bleibt auf dem Gerät
        appDatei(PROJEKT + "/Planungsdokumente/Zweit.pdf", "Z");
        service.abgleichen(speicher, false);
        eintraege.clear();
        Ergebnis b = service.abgleichen(speicher, false);
        assertThat(freigabe.resolve(PROJEKT + "/Planungsdokumente/Zweit.pdf")).exists();
        assertThat(b.hinweise()).anyMatch(h -> h.contains("Zweit.pdf"));
    }

    @Test
    void eineAnfrageDateiWirdHochgeladenAberNichtVomGeraetZurueckgeschrieben() throws IOException {
        Path q = appDatei("Anfragen/2026/Muster GmbH - Halle (A5)/Kalkulation/K.xlsx", "K1");
        service.abgleichen(speicher, false);
        Path fern = freigabe.resolve("Anfragen/2026/Muster GmbH - Halle (A5)/Kalkulation/K.xlsx");
        Files.writeString(fern, "K2 geändert");
        spaeter(fern, 10);

        Ergebnis e = service.abgleichen(speicher, false);

        assertThat(e.vomGeraetUebernommen()).isZero();
        assertThat(Files.readString(q)).isEqualTo("K1");
        assertThat(e.hinweise()).anyMatch(h -> h.contains("nur für Projekte"));
    }

    @Test
    void dieGruppeKommtAusDemUnterordner() {
        assertThat(SmbAbgleichService.gruppeAus(PROJEKT + "/Bilder/a.jpg", 4)).isEqualTo(DokumentGruppe.BILDER);
        assertThat(SmbAbgleichService.gruppeAus(PROJEKT + "/Dokumentation EN 1090/a.pdf", 4)).isEqualTo(DokumentGruppe.DOKUMENTATION_1090);
        assertThat(SmbAbgleichService.gruppeAus(PROJEKT + "/Irgendwas/a.pdf", 4)).isEqualTo(DokumentGruppe.DIVERSE_DOKUMENTE);
        assertThat(SmbAbgleichService.gruppeAus(PROJEKT + "/a.pdf", 4)).isEqualTo(DokumentGruppe.DIVERSE_DOKUMENTE);
        assertThat(SmbAbgleichService.gruppeAus(PROJEKT_ALT + "/Bilder/a.jpg", 3)).as("frühere Form ohne Kunde").isEqualTo(DokumentGruppe.BILDER);
    }

    @Test
    void fehlerBeimVerbindenSindImKlartextVerstaendlich() {
        assertThat(SmbAbgleichService.klartext(new RuntimeException("STATUS_LOGON_FAILURE (0xc000006d)"))).contains("Benutzername oder Passwort");
        assertThat(SmbAbgleichService.klartext(new RuntimeException("STATUS_BAD_NETWORK_NAME"))).contains("gibt es auf dem Gerät nicht");
        assertThat(SmbAbgleichService.klartext(new java.net.UnknownHostException("nas"))).contains("nicht gefunden");
        assertThat(SmbAbgleichService.klartext(new java.net.SocketTimeoutException("Read timed out"))).contains("antwortet nicht");
    }

    @Test
    void dieAngabenWerdenGeprueft() {
        assertThat(new SmbAbgleichEinstellungen(true, "smb://192.168.178.21", "volume2", "", "", "", "").fehler()).contains("ohne smb://");
        assertThat(new SmbAbgleichEinstellungen(true, "192.168.178.21", "volume2/Firma", "", "", "", "").fehler()).contains("Namen der Freigabe");
        assertThat(new SmbAbgleichEinstellungen(true, "192.168.178.21", "volume2", "../x", "", "", "").fehler()).contains("Unterordner");
        assertThat(new SmbAbgleichEinstellungen(true, "192.168.178.21", "volume2", "Firma/ERP", "", "", "").fehler()).isNull();
        assertThat(new SmbAbgleichEinstellungen(true, "nas", "erp", "\\Firma\\ERP\\", "", "", "").unterordnerSauber()).isEqualTo("Firma/ERP");
    }

    @Test
    void freigabenUndOrdnerWerdenZurAuswahlGeliefertUndFehlerSindVerstaendlich() throws IOException {
        SmbDurchsucher d = mock(SmbDurchsucher.class);
        service.setDurchsucher(d);
        SmbAbgleichEinstellungen e = new SmbAbgleichEinstellungen(false, "192.168.178.21", "", "", "erika", "geheim", "");
        when(d.freigaben(e)).thenReturn(List.of("Unternehmen", "Dokumente"));
        when(d.ordner(e, "Unternehmen", "test Ordner")).thenReturn(List.of("Unternehmen"));

        assertThat(service.freigaben(e)).containsExactly("Unternehmen", "Dokumente");
        assertThat(service.ordner(e, "Unternehmen", "test Ordner")).containsExactly("Unternehmen");

        when(d.freigaben(e)).thenThrow(new IOException("STATUS_LOGON_FAILURE"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.freigaben(e)).isInstanceOf(IllegalStateException.class).hasMessageContaining("Benutzername oder Passwort");
    }

    @Test
    void ungueltigeAngabenBeimAuswaehlenWerdenAbgelehnt() {
        SmbAbgleichEinstellungen schlecht = new SmbAbgleichEinstellungen(false, "smb://nas", "", "", "", "", "");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.freigaben(schlecht)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ohne smb://");
        SmbAbgleichEinstellungen gut = new SmbAbgleichEinstellungen(false, "nas", "", "", "", "", "");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.ordner(gut, "a/b", "")).hasMessageContaining("Freigabe");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.ordner(gut, "Firma", "../x")).hasMessageContaining("ungültige Zeichen");
    }

    @Test
    void dieBibliothekenFuerDieFreigabenListeLassenSichZusammenladen() {
        // smbj und dcerpc stammen aus verschiedenen Projekten: ein Versionsfehler würde sonst erst am echten Gerät auffallen
        assertThat(new com.rapid7.client.dcerpc.mssrvs.ServerService(mock(com.rapid7.client.dcerpc.transport.RPCTransport.class))).isNotNull();
        assertThat(com.rapid7.client.dcerpc.transport.SMBTransportFactories.SRVSVC).isNotNull();
        assertThat(com.hierynomus.smbj.auth.AuthenticationContext.guest()).isNotNull();
        assertThat(com.hierynomus.smbj.auth.AuthenticationContext.anonymous()).isNotNull();
    }

    @Test
    void einAbgleichMerktSichZeitpunktUndKurzergebnisAuchBeiFehlerNichtBeiVorschau() throws IOException {
        SmbAbgleichEinstellungen e = new SmbAbgleichEinstellungen(true, "192.168.178.21", "Unternehmen", "ERP", "", "", "");
        when(einstellungen.lade()).thenReturn(e);
        appDatei(PROJEKT + "/Planungsdokumente/Plan.pdf", "PLAN");
        service.setQuelle(x -> speicher);

        service.abgleichen(true);
        verify(einstellungen, never()).merkeLauf(any(), any());

        service.abgleichen(false);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(einstellungen).merkeLauf(any(), text.capture());
        assertThat(text.getValue()).isEqualTo("1 auf die Freigabe, 0 vom Gerät");

        service.setQuelle(x -> { throw new IOException("STATUS_LOGON_FAILURE"); });
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.abgleichen(false)).hasMessageContaining("Benutzername oder Passwort");
        verify(einstellungen, org.mockito.Mockito.times(2)).merkeLauf(any(), text.capture());
        assertThat(text.getValue()).startsWith("Fehler: Benutzername oder Passwort");
    }

    @Test
    void dieOrdnerstrukturIstJahrKundeProjektGruppe() throws IOException {
        appDatei(PROJEKT + "/Planungsdokumente/Plan.pdf", "PLAN");
        service.abgleichen(speicher, false);
        assertThat(freigabe.resolve("Projekte/2026/Max Mustermann GmbH/2026-10-00014 - Anbau Halle/Planungsdokumente/Plan.pdf")).exists();
    }

    @Test
    void eineDateiInDerFruehherenOrdnerFormOhneKundeWirdWeiterDemProjektZugeordnet() throws IOException {
        geraetDatei(PROJEKT_ALT + "/Planungsdokumente/Skizze.pdf", "SKIZZE", 60);
        service.abgleichen(speicher, false);
        verify(dateien).speichereErzeugteDatei(any(), eq("Skizze.pdf"), eq(7L), eq(DokumentGruppe.PLANUNGSDOKUMENTE), any());
    }

    @Test
    void ordnerNachDerUmstellungWerdenZusammengefasstUndNichtNochEinmalUebernommen() throws IOException {
        // Vor der Umstellung lag das Dokument unter dem alten Pfad auf dem Gerät und im Merkzettel
        appDatei(PROJEKT_ALT + "/Planungsdokumente/Plan.pdf", "PLAN");
        service.abgleichen(speicher, false);
        eintraege.clear();
        appDatei(PROJEKT + "/Planungsdokumente/Plan.pdf", "PLAN");

        Ergebnis e = service.abgleichen(speicher, false);

        assertThat(freigabe.resolve(PROJEKT + "/Planungsdokumente/Plan.pdf")).as("im neuen Ordner angelegt").exists();
        assertThat(freigabe.resolve(PROJEKT_ALT + "/Planungsdokumente/Plan.pdf")).as("der alte bleibt, es wird nichts gelöscht").exists();
        assertThat(e.neuVomGeraet()).isZero();
        assertThat(e.hinweise()).hasSize(1).first().asString().contains("1 Dateien liegen noch in der früheren Ordnerstruktur");
        verify(dateien, never()).speichereErzeugteDatei(any(), any(), anyLong(), any(), any());
    }

    @Test
    void ohneKundeStehtOhneKundeImPfad() {
        Projekt p = new Projekt();
        p.setId(8L);
        p.setAuftragsnummer("1");
        p.setBauvorhaben("Zaun");
        p.setAnlegedatum(LocalDate.of(2026, 3, 1));
        assertThat(LesbareAblageService.projektOrdner(p)).isEqualTo("Projekte/2026/Ohne Kunde/1 - Zaun");
        assertThat(LesbareAblageService.projektOrdnerAlt(p)).isEqualTo("Projekte/2026/1 - Zaun");
    }
}
