package org.example.kalkulationsprogramm.service.ablage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.repository.ProjektRepository;
import org.example.kalkulationsprogramm.service.DateiSpeicherService;
import org.example.kalkulationsprogramm.service.ablage.AbgleichSpeicher.Datei;
import org.example.kalkulationsprogramm.service.ablage.LesbareAblageService.Eintrag;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * Gleicht die Dokumente in beide Richtungen mit einer Netzwerkfreigabe ab (SMB), in der das Programm eine lesbare Ordnerstruktur anlegt
 * ({@code <Kunde>/<Jahr>/Projekte/<Auftragsnummer> - <Bauvorhaben>/<Gruppe>/<Datei>}, dieselbe wie die lesbare Ablage).
 *
 * <p><b>Die eigentliche Ablage des Programms bleibt unverändert.</b> Der Abgleich ist eine Zusatzfunktion: Es wird nie etwas in der Ablage gelöscht,
 * und auf der Freigabe wird ebenfalls nie etwas gelöscht. Was der Abgleich im Einzelnen tut:</p>
 * <ul>
 *   <li><b>Programm → Freigabe:</b> neue und geänderte Dokumente werden hochgeladen.</li>
 *   <li><b>Freigabe → Programm:</b> neue Dateien in einem Projektordner werden als Dokument des Projekts übernommen (die Untergruppe bestimmt die
 *       Dokumentgruppe); eine auf der Freigabe geänderte Datei ersetzt den Inhalt des Dokuments.</li>
 *   <li><b>Beides geändert:</b> Die Fassung von der Freigabe wird als eigenes Dokument gesichert (&quot;… (vom Gerät JJJJ-MM-TT)&quot;), danach gilt die Fassung
 *       des Programms. Nichts geht verloren.</li>
 *   <li><b>Gelöscht</b> wird auf keiner Seite etwas; Abweichungen stehen als Hinweis im Ergebnis.</li>
 * </ul>
 * Was zuletzt abgeglichen wurde, steht in {@code _erp/abgleich.json} auf der Freigabe.
 */
@Service
@Slf4j
public class SmbAbgleichService {

    static final String MANIFEST = "_erp/abgleich.json";
    static final long MAX_UEBERNAHME_BYTES = 100L * 1024 * 1024;
    /** Dateien, die gerade noch kopiert werden, werden beim nächsten Mal übernommen. */
    static final long RUHEZEIT_MS = 30_000;

    /** Ergebnis eines Abgleichs oder einer Vorschau. */
    public record Ergebnis(boolean probelauf, int hochgeladen, int vomGeraetUebernommen, int neuVomGeraet, int konflikte, int unveraendert,
            List<String> hinweise, List<String> fehler) {
    }

    /** Was beim letzten Abgleich von beiden Seiten bekannt war. */
    record Stand(long rGroesse, long rGeaendert, long lGroesse, long lGeaendert) {
    }

    @FunctionalInterface
    interface SpeicherQuelle {
        AbgleichSpeicher oeffne(SmbAbgleichEinstellungen e) throws IOException;
    }

    private final LesbareAblageService ablage;
    private final ProjektRepository projektRepository;
    private final DateiSpeicherService dateiSpeicherService;
    private final SmbAbgleichEinstellungenService einstellungen;
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicBoolean laeuft = new AtomicBoolean(false);
    private SpeicherQuelle quelle = SmbjAbgleichSpeicher::new;
    private java.time.Clock uhr = java.time.Clock.systemDefaultZone();
    private SmbDurchsucher durchsucher = new SmbDurchsucher();

    public SmbAbgleichService(LesbareAblageService ablage, ProjektRepository projektRepository, DateiSpeicherService dateiSpeicherService,
            SmbAbgleichEinstellungenService einstellungen) {
        this.ablage = ablage;
        this.projektRepository = projektRepository;
        this.dateiSpeicherService = dateiSpeicherService;
        this.einstellungen = einstellungen;
    }

    /** Nur für Tests. */
    void setQuelle(SpeicherQuelle q) {
        this.quelle = q;
    }

    void setDurchsucher(SmbDurchsucher d) {
        this.durchsucher = d;
    }

    void setUhr(java.time.Clock u) {
        this.uhr = u;
    }

    // ------------------------------------------------------------------ Auswählen statt kennen

    /** Meldet sich am Gerät an und nennt die Freigaben, aus denen man wählen kann. Nur Gerät, Benutzer und Passwort werden gebraucht. */
    public List<String> freigaben(SmbAbgleichEinstellungen e) {
        pruefeGeraet(e);
        try {
            return durchsucher.freigaben(e);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException(klartext(ex), ex);
        }
    }

    /** Die Unterordner eines Ordners in einer Freigabe, zum Auswählen. */
    public List<String> ordner(SmbAbgleichEinstellungen e, String freigabe, String pfad) {
        pruefeGeraet(e);
        if (freigabe == null || !freigabe.trim().matches("[^\\\\/:*?\"<>|]{1,80}")) {
            throw new IllegalArgumentException("Bitte eine Freigabe wählen.");
        }
        if (pfad != null && (pfad.contains("..") || pfad.length() > 300 || pfad.matches(".*[:*?\"<>|].*"))) {
            throw new IllegalArgumentException("Der Ordner enthält ungültige Zeichen.");
        }
        try {
            return durchsucher.ordner(e, freigabe, pfad);
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException(klartext(ex), ex);
        }
    }

    private static void pruefeGeraet(SmbAbgleichEinstellungen e) {
        if (e.server() == null || !e.server().trim().matches("[A-Za-z0-9._-]{1,253}")) {
            throw new IllegalArgumentException("Bitte den Namen oder die IP-Adresse des Geräts eintragen, zum Beispiel 192.168.178.21 (ohne smb:// und ohne Schrägstriche).");
        }
    }

    // ------------------------------------------------------------------ Verbindung testen

    /** Prüft den Zugang: anmelden, Freigabe öffnen, eine Probedatei schreiben, lesen und wieder überschreiben. Bei Fehlern kommt ein Klartext. */
    public String teste(SmbAbgleichEinstellungen e) {
        String fehler = e.fehler();
        if (fehler != null) {
            throw new IllegalArgumentException(fehler);
        }
        try (AbgleichSpeicher s = quelle.oeffne(e)) {
            byte[] probe = ("Verbindungstest " + Instant.now(uhr)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            s.schreibe("_erp/verbindungstest.txt", probe);
            byte[] zurueck = s.lies("_erp/verbindungstest.txt");
            if (!java.util.Arrays.equals(probe, zurueck)) {
                throw new IllegalStateException("Die Probedatei kam verändert zurück.");
            }
            return "Verbindung steht: Anmelden, Schreiben und Lesen funktionieren.";
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException(klartext(ex), ex);
        }
    }

    static String klartext(Throwable ex) {
        String text = ex.toString();
        for (Throwable t = ex; t != null; t = t.getCause()) {
            text += " " + t;
        }
        String l = text.toLowerCase(Locale.ROOT);
        if (l.contains("logon_failure") || l.contains("wrong_password") || l.contains("no_such_user")) {
            return "Benutzername oder Passwort stimmen nicht. Bei einem Gastzugang: Das Gerät erlaubt ihn vielleicht nicht, dann einen Benutzer anlegen.";
        }
        if (l.contains("access_denied") || l.contains("account_restriction")) {
            return "Der Zugriff wurde verweigert. Hat der Benutzer auf der Freigabe Schreibrechte?";
        }
        if (l.contains("bad_network_name")) {
            return "Diese Freigabe gibt es auf dem Gerät nicht. Bitte den Namen der Freigabe prüfen (nur der Name, zum Beispiel volume2).";
        }
        if (l.contains("unknownhost") || l.contains("unresolved")) {
            return "Das Gerät wurde im Netzwerk nicht gefunden. Bitte Namen oder IP-Adresse prüfen.";
        }
        if (l.contains("timeout") || l.contains("timed out") || l.contains("connectexception") || l.contains("connection refused") || l.contains("no route")) {
            return "Das Gerät antwortet nicht. Ist es eingeschaltet, im selben Netzwerk, und ist SMB freigegeben (Port 445)?";
        }
        return "Die Verbindung hat nicht geklappt: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
    }

    // ------------------------------------------------------------------ Abgleich

    @Transactional
    public Ergebnis abgleichen(boolean probelauf) {
        SmbAbgleichEinstellungen e = einstellungen.lade();
        if (!e.aktiv()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Der Abgleich ist ausgeschaltet.");
        }
        if (!e.vollstaendig() || e.fehler() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bitte zuerst die Netzwerkfreigabe eintragen.");
        }
        try (AbgleichSpeicher s = quelle.oeffne(e)) {
            Ergebnis ergebnis = abgleichen(s, probelauf);
            if (!probelauf) {
                einstellungen.merkeLauf(java.time.LocalDateTime.now(uhr), kurz(ergebnis));
            }
            return ergebnis;
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.CONFLICT) {
                throw rse;
            }
            if (!probelauf) {
                einstellungen.merkeLauf(java.time.LocalDateTime.now(uhr), "Fehler: " + klartext(ex));
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, klartext(ex));
        }
    }

    static String kurz(Ergebnis e) {
        String text = e.hochgeladen() + " auf die Freigabe, " + (e.neuVomGeraet() + e.vomGeraetUebernommen()) + " vom Gerät"
                + (e.konflikte() > 0 ? ", " + e.konflikte() + " Konflikte" : "") + (e.fehler().isEmpty() ? "" : ", " + e.fehler().size() + " Probleme");
        return text;
    }

    Ergebnis abgleichen(AbgleichSpeicher s, boolean probelauf) throws IOException {
        if (!laeuft.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Es läuft gerade ein Abgleich. Bitte einen Moment warten.");
        }
        try {
            return lauf(s, probelauf);
        } finally {
            laeuft.set(false);
        }
    }

    private Ergebnis lauf(AbgleichSpeicher s, boolean probelauf) throws IOException {
        List<Eintrag> eintraege = ablage.eintraege();
        Map<String, Datei> fern = new LinkedHashMap<>();
        for (Datei d : s.liste()) {
            fern.put(d.relativ().toLowerCase(Locale.ROOT), d);
        }
        Map<String, Stand> stand = ladeStand(s);
        Map<String, Projekt> projekte = projekteNachOrdner();
        List<String> hinweise = new ArrayList<>();
        List<String> fehler = new ArrayList<>();
        int[] zaehler = new int[5];   // 0 hoch, 1 vom Gerät, 2 neu, 3 Konflikt, 4 unverändert
        Set<String> inDerApp = new HashSet<>();

        for (Eintrag e : eintraege) {
            String schluessel = e.relativ().toLowerCase(Locale.ROOT);
            inDerApp.add(schluessel);
            Path q = e.quelle();
            try {
                if (!Files.isRegularFile(q)) {
                    continue;
                }
                long lg = Files.size(q);
                long lm = Files.getLastModifiedTime(q).toMillis();
                Datei r = fern.get(schluessel);
                Stand alt = stand.get(schluessel);
                boolean zurueckErlaubt = projektFuer(e.relativ(), projekte) != null;
                if (r == null) {
                    if (alt != null) {
                        hinweise.add("Auf dem Gerät gelöscht oder verschoben (bleibt in der App): " + e.relativ());
                        continue;
                    }
                    zaehler[0]++;
                    if (!probelauf) {
                        hochladen(s, e.relativ(), q, stand, schluessel);
                    }
                } else if (alt == null) {
                    if (r.groesse() == lg) {
                        zaehler[4]++;
                        stand.put(schluessel, new Stand(r.groesse(), r.geaendert(), lg, lm));
                    } else if (zurueckErlaubt && projektFuer(e.relativ(), projekte) != null) {
                        zaehler[3]++;
                        konflikt(s, e.relativ(), r, q, projekte, stand, schluessel, probelauf, hinweise);
                    } else {
                        hinweise.add("Gleicher Name, anderer Inhalt (nicht abgeglichen): " + e.relativ());
                    }
                } else {
                    boolean fernGeaendert = r.groesse() != alt.rGroesse() || r.geaendert() != alt.rGeaendert();
                    boolean lokalGeaendert = lg != alt.lGroesse() || lm != alt.lGeaendert();
                    if (!fernGeaendert && !lokalGeaendert) {
                        zaehler[4]++;
                    } else if (lokalGeaendert && !fernGeaendert) {
                        zaehler[0]++;
                        if (!probelauf) {
                            hochladen(s, e.relativ(), q, stand, schluessel);
                        }
                    } else if (fernGeaendert && !lokalGeaendert) {
                        if (zurueckErlaubt) {
                            zaehler[1]++;
                            if (!probelauf) {
                                Files.write(q, s.lies(e.relativ()));
                                stand.put(schluessel, new Stand(r.groesse(), r.geaendert(), Files.size(q), Files.getLastModifiedTime(q).toMillis()));
                            }
                        } else {
                            hinweise.add("Auf dem Gerät geändert, wird nur für Projekte übernommen: " + e.relativ());
                        }
                    } else if (zurueckErlaubt && projektFuer(e.relativ(), projekte) != null) {
                        zaehler[3]++;
                        konflikt(s, e.relativ(), r, q, projekte, stand, schluessel, probelauf, hinweise);
                    } else {
                        hinweise.add("Auf beiden Seiten geändert (nicht abgeglichen): " + e.relativ());
                    }
                }
            } catch (IOException | RuntimeException ex) {
                fehler.add(e.relativ() + ": " + ex.getMessage());
                log.warn("SMB-Abgleich: {} konnte nicht abgeglichen werden", e.relativ(), ex);
            }
        }

        // Frühere Ordnerformen: Diese Dateien gibt es jetzt unter dem neuen Pfad; sie werden nicht noch einmal übernommen
        Set<String> frueherePfade = new HashSet<>();
        for (String neuerPfad : inDerApp) {
            frueherePfade.addAll(frueherePfade(neuerPfad));
        }
        int inAlterStruktur = 0;

        // Dateien auf der Freigabe, die das Programm nicht kennt
        for (Map.Entry<String, Datei> en : fern.entrySet()) {
            if (inDerApp.contains(en.getKey())) {
                continue;
            }
            Datei d = en.getValue();
            if (frueherePfade.contains(en.getKey())) {
                inAlterStruktur++;
                continue;
            }
            if (stand.containsKey(en.getKey())) {
                hinweise.add("Auf dem Gerät vorhanden, in der App nicht mehr oder unter anderem Namen: " + d.relativ());
                continue;
            }
            try {
                String grund = nichtUebernehmbar(d, projekte);
                if (grund != null) {
                    if (!grund.isEmpty()) {
                        hinweise.add(grund + ": " + d.relativ());
                    }
                    continue;
                }
                zaehler[2]++;
                if (!probelauf) {
                    uebernehmeNeu(s, d, projekte, stand, en.getKey());
                }
            } catch (IOException | RuntimeException ex) {
                fehler.add(d.relativ() + ": " + ex.getMessage());
                log.warn("SMB-Abgleich: {} konnte nicht übernommen werden", d.relativ(), ex);
            }
        }

        if (inAlterStruktur > 0) {
            hinweise.add(0, inAlterStruktur + " Dateien liegen noch in einer früheren Ordnerstruktur auf dem Gerät. Sie sind jetzt unter Kunde/Jahr/Projekte/Projekt abgelegt; die alten Ordner können Sie auf dem Gerät löschen, das Programm löscht nichts.");
        }

        if (!probelauf) {
            speichereStand(s, stand);
            log.info("SMB-Abgleich: hochgeladen {}, vom Gerät übernommen {}, neu vom Gerät {}, Konflikte {}, unverändert {}, Fehler {}",
                    zaehler[0], zaehler[1], zaehler[2], zaehler[3], zaehler[4], fehler.size());
        }
        return new Ergebnis(probelauf, zaehler[0], zaehler[1], zaehler[2], zaehler[3], zaehler[4], begrenze(hinweise), begrenze(fehler));
    }

    /**
     * Frühhere Pfade (in Kleinbuchstaben) für den aktuellen Pfad – damit alte Dateien auf der Freigabe nicht erneut übernommen werden.
     * <ul>
     *   <li>Neu: {@code Kunde/Jahr/Projekte/Projekt/...} → {@code Projekte/Jahr/Kunde/Projekt/...} und {@code Projekte/Jahr/Projekt/...}</li>
     *   <li>Alt (mit Kunde): {@code Projekte/Jahr/Kunde/Projekt/...} → {@code Projekte/Jahr/Projekt/...}</li>
     * </ul>
     */
    static List<String> frueherePfade(String neuerPfadKlein) {
        String[] teile = neuerPfadKlein.split("/");
        // Neues Format: Kunde/Jahr/Projekte/Projekt/...
        if (teile.length >= 5 && teile[2].equals("projekte")) {
            // Alt 1: Projekte/Jahr/Kunde/Projekt/...
            StringBuilder alt1 = new StringBuilder("projekte").append('/').append(teile[1]).append('/').append(teile[0]);
            for (int i = 3; i < teile.length; i++) alt1.append('/').append(teile[i]);
            // Alt 2: Projekte/Jahr/Projekt/... (ohne Kunde)
            StringBuilder alt2 = new StringBuilder("projekte").append('/').append(teile[1]);
            for (int i = 3; i < teile.length; i++) alt2.append('/').append(teile[i]);
            return List.of(alt1.toString(), alt2.toString());
        }
        // Altes Format: Projekte/Jahr/Kunde/Projekt/... → Projekte/Jahr/Projekt/...
        if (teile.length >= 6 && teile[0].equals("projekte")) {
            StringBuilder sb = new StringBuilder(teile[0]).append('/').append(teile[1]);
            for (int i = 3; i < teile.length; i++) sb.append('/').append(teile[i]);
            return List.of(sb.toString());
        }
        return List.of();
    }

    private static List<String> begrenze(List<String> l) {
        return l.size() <= 50 ? l : new ArrayList<>(l.subList(0, 50));
    }

    private void hochladen(AbgleichSpeicher s, String relativ, Path q, Map<String, Stand> stand, String schluessel) throws IOException {
        long gr = Files.size(q);
        if (gr > MAX_UEBERNAHME_BYTES) {
            throw new IOException("Datei größer als 100 MB, wird nicht übertragen");
        }
        s.schreibe(relativ, Files.readAllBytes(q));
        Datei neu = s.info(relativ).orElseThrow(() -> new IOException("Nach dem Hochladen nicht auffindbar"));
        stand.put(schluessel, new Stand(neu.groesse(), neu.geaendert(), gr, Files.getLastModifiedTime(q).toMillis()));
    }

    /** Beide Seiten geändert: die Fassung vom Gerät als eigenes Dokument sichern, danach gilt die des Programms. */
    private void konflikt(AbgleichSpeicher s, String relativ, Datei fern, Path q, Map<String, Projekt> projekte, Map<String, Stand> stand, String schluessel,
            boolean probelauf, List<String> hinweise) throws IOException {
        hinweise.add("Auf beiden Seiten geändert, die Fassung vom Gerät wurde als eigenes Dokument gesichert: " + relativ);
        if (probelauf) {
            return;
        }
        String name = relativ.substring(relativ.lastIndexOf('/') + 1);
        int punkt = name.lastIndexOf('.');
        String neuerName = (punkt > 0 ? name.substring(0, punkt) : name) + " (vom Gerät " + LocalDate.now(uhr) + ")" + (punkt > 0 ? name.substring(punkt) : "");
        speichereAlsDokument(projektFuer(relativ, projekte), neuerName, gruppeAus(relativ, projekte), s.lies(relativ));
        hochladen(s, relativ, q, stand, schluessel);
    }

    // ------------------------------------------------------------------ Übernehmen vom Gerät

    private static final Set<String> GESPERRT = Set.of("exe", "bat", "cmd", "com", "msi", "scr", "sh", "ps1", "vbs", "js", "jar", "dll", "lnk", "html", "htm", "svg");

    /** @return {@code null} wenn die Datei übernommen werden darf, sonst ein Hinweistext ({@code ""} = stillschweigend überspringen). */
    private String nichtUebernehmbar(Datei d, Map<String, Projekt> projekte) {
        String[] teile = d.relativ().split("/");
        String name = teile[teile.length - 1];
        String lower = name.toLowerCase(Locale.ROOT);
        if (name.startsWith("~$") || name.startsWith(".") || lower.equals("thumbs.db") || lower.equals("desktop.ini") || lower.endsWith(".tmp")
                || lower.endsWith(".part") || lower.endsWith(".crdownload") || lower.endsWith(".lnk")) {
            return "";
        }
        int tiefe = tiefeFuer(d.relativ(), projekte);
        if (tiefe == 0) {
            // Sieht es aus wie ein Projektordner (bekanntes Format, aber unbekanntes Projekt)?
            boolean looksProjekt = (teile.length >= 4 && "projekte".equals(teile[0].toLowerCase(Locale.ROOT)))
                    || (teile.length >= 5 && "projekte".equals(teile[2].toLowerCase(Locale.ROOT)));
            if (looksProjekt) {
                return "Neue Datei auf dem Gerät ohne passendes Projekt (Ordner <Jahr>/<Kunde>/<Auftragsnummer> - <Bauvorhaben> stimmt nicht)";
            }
            return "Neue Datei auf dem Gerät ausserhalb des Projektordners, wird nicht übernommen";
        }
        int punkt = lower.lastIndexOf('.');
        if (punkt >= 0 && GESPERRT.contains(lower.substring(punkt + 1))) {
            return "Dateityp wird aus Sicherheitsgründen nicht übernommen";
        }
        if (d.groesse() == 0) {
            return "";
        }
        if (d.groesse() > MAX_UEBERNAHME_BYTES) {
            return "Neue Datei auf dem Gerät ist größer als 100 MB, wird nicht übernommen";
        }
        if (uhr.millis() - d.geaendert() < RUHEZEIT_MS) {
            return "";   // wird gerade noch kopiert, kommt beim nächsten Mal
        }
        if ("Ausgangsdokumente".equals(teile.length >= tiefe + 2 ? teile[tiefe] : "")) {
            return "Ausgangsdokumente (eigene Rechnungen und Angebote) werden nicht von Hand übernommen";
        }
        return null;
    }

    private void uebernehmeNeu(AbgleichSpeicher s, Datei d, Map<String, Projekt> projekte, Map<String, Stand> stand, String schluessel) throws IOException {
        byte[] inhalt = s.lies(d.relativ());
        String name = d.relativ().substring(d.relativ().lastIndexOf('/') + 1);
        speichereAlsDokument(projektFuer(d.relativ(), projekte), name, gruppeAus(d.relativ(), projekte), inhalt);
        // Die Datei bleibt auf dem Gerät; der Vermerk verhindert, dass sie noch einmal übernommen wird
        stand.put(schluessel, new Stand(d.groesse(), d.geaendert(), inhalt.length, 0));
    }

    private void speichereAlsDokument(Projekt projekt, String name, DokumentGruppe gruppe, byte[] inhalt) {
        String sauber = Path.of(name.replace('\\', '/')).getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
        String typ;
        try {
            typ = Files.probeContentType(Path.of(sauber));
        } catch (IOException | RuntimeException ex) {
            typ = null;
        }
        dateiSpeicherService.speichereErzeugteDatei(inhalt, sauber, projekt.getId(), gruppe, typ == null ? "application/octet-stream" : typ);
    }

    /** Die Projektordner, auch die frühere Form ohne Kunde (Dateien, die jemand dort ablegt, werden weiter dem Projekt zugeordnet). */
    private Map<String, Projekt> projekteNachOrdner() {
        Map<String, Projekt> m = new HashMap<>();
        for (Projekt p : projektRepository.findAll()) {
            m.put(LesbareAblageService.projektOrdnerAlt2(p).toLowerCase(Locale.ROOT), p);
        }
        for (Projekt p : projektRepository.findAll()) {
            m.put(LesbareAblageService.projektOrdnerAlt(p).toLowerCase(Locale.ROOT), p);
        }
        for (Projekt p : projektRepository.findAll()) {
            m.put(LesbareAblageService.projektOrdner(p).toLowerCase(Locale.ROOT), p);
        }
        return m;
    }

    /** Wie viele Ordnerebenen der Projektordner hat: 4 (Projekte/Jahr/Kunde/Projekt), 3 in der früheren Form ohne Kunde, 0 wenn kein Projekt passt. */
    private static int tiefeFuer(String relativ, Map<String, Projekt> projekte) {
        String[] teile = relativ.split("/");
        if (teile.length >= 5 && projekte.containsKey(String.join("/", teile[0], teile[1], teile[2], teile[3]).toLowerCase(Locale.ROOT))) {
            return 4;
        }
        if (teile.length >= 4 && projekte.containsKey(String.join("/", teile[0], teile[1], teile[2]).toLowerCase(Locale.ROOT))) {
            return 3;
        }
        return 0;
    }

    private static Projekt projektFuer(String relativ, Map<String, Projekt> projekte) {
        int tiefe = tiefeFuer(relativ, projekte);
        if (tiefe == 0) {
            return null;
        }
        String[] teile = relativ.split("/");
        return projekte.get(String.join("/", java.util.Arrays.copyOf(teile, tiefe)).toLowerCase(Locale.ROOT));
    }

    /** Ordner unter dem Projekt ist die Dokumentgruppe; liegt die Datei direkt im Projektordner oder ist der Ordner unbekannt, gilt "Diverse Dokumente". */
    static DokumentGruppe gruppeAus(String relativ, int tiefe) {
        String[] teile = relativ.split("/");
        if (tiefe > 0 && teile.length >= tiefe + 2) {
            for (DokumentGruppe g : DokumentGruppe.values()) {
                if (LesbareAblageService.gruppe(g).equalsIgnoreCase(teile[tiefe])) {
                    return g;
                }
            }
        }
        return DokumentGruppe.DIVERSE_DOKUMENTE;
    }

    private static DokumentGruppe gruppeAus(String relativ, Map<String, Projekt> projekte) {
        return gruppeAus(relativ, tiefeFuer(relativ, projekte));
    }

    // ------------------------------------------------------------------ Merkzettel auf der Freigabe

    private Map<String, Stand> ladeStand(AbgleichSpeicher s) throws IOException {
        Optional<Datei> d = s.info(MANIFEST);
        if (d.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return json.readValue(s.lies(MANIFEST), new TypeReference<HashMap<String, Stand>>() {
            });
        } catch (IOException ex) {
            throw new IOException("Der Merkzettel _erp/abgleich.json auf der Freigabe ist unlesbar. Bitte die Datei umbenennen oder löschen (dann wird neu abgeglichen).", ex);
        }
    }

    private void speichereStand(AbgleichSpeicher s, Map<String, Stand> stand) throws IOException {
        s.schreibe(MANIFEST, json.writerWithDefaultPrettyPrinter().writeValueAsBytes(stand));
    }
}
