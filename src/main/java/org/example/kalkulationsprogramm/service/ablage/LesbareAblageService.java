package org.example.kalkulationsprogramm.service.ablage;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.example.kalkulationsprogramm.domain.Anfrage;
import org.example.kalkulationsprogramm.domain.AnfrageDokument;
import org.example.kalkulationsprogramm.domain.AnfrageNotizBild;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokument;
import org.example.kalkulationsprogramm.domain.AusgangsGeschaeftsDokumentTyp;
import org.example.kalkulationsprogramm.domain.DokumentGruppe;
import org.example.kalkulationsprogramm.domain.LieferantDokument;
import org.example.kalkulationsprogramm.domain.LieferantDokumentTyp;
import org.example.kalkulationsprogramm.domain.Projekt;
import org.example.kalkulationsprogramm.domain.ProjektDokument;
import org.example.kalkulationsprogramm.domain.ProjektNotizBild;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

/**
 * Die lesbare Ablage: ein Ordnerbaum mit verständlichen Namen, der auf dieselben Dateien zeigt wie die eigentliche Ablage mit ihren Zufallsnamen.
 *
 * <pre>
 *   _lesbar/Projekte/2026/Muster GmbH/2026-014 - Anbau Halle/Geschaeftsdokumente/Rechnung RE-2026-031.pdf
 *   _lesbar/Anfragen/2026/Muster GmbH - Geländer Hof (A87)/Kalkulation.xlsx
 *   _lesbar/Lieferanten/Stahl Müller/2026/Rechnungen/Rechnung 1234.pdf
 * </pre>
 *
 * <p><b>Die eigentliche Ablage wird nie angefasst.</b> Die Anwendung findet ihre Dateien weiter unter den Zufallsnamen. Die lesbare Ablage ist eine
 * <b>Spiegelung</b>: Nach dem Löschen des Ordners {@code _lesbar} ist nichts verloren, ein neuer Aufbau stellt sie wieder her. Sie entsteht per
 * <b>Hardlink</b> (derselbe Speicherplatz, kein zweites Exemplar). Geht das auf dem Datenträger nicht, wird <b>kopiert</b> (doppelter Platzbedarf, im
 * Ergebnis ausgewiesen).</p>
 *
 * <p>Nur Fachdokumente werden gespiegelt (Projekte, Anfragen und Angebote, Lieferantendokumente). Personalunterlagen, Lohnabrechnungen und
 * Kassenbelege bleiben bewusst draußen. Nach Aufbau sind Änderungen in der Anwendung (gelöscht, umbenannt, verschoben) erst nach dem nächsten
 * Aufbau sichtbar; veraltete Einträge werden dann entfernt.</p>
 */
@Service
@Slf4j
public class LesbareAblageService {

    static final String ORDNER = "_lesbar";

    /** Ergebnis eines Aufbaus oder einer Vorschau. */
    public record Ergebnis(boolean probelauf, String zielordner, int dateien, int neu, int unveraendert, int ersetzt, int entfernt,
            int kopiert, int fehlend, List<String> beispiele, List<String> fehler) {
    }

    /** Eine zu spiegelnde Datei: wo sie wirklich liegt und unter welchem lesbaren Pfad sie erscheinen soll. */
    record Eintrag(Path quelle, String relativ) {
    }

    /** Legt die Verknüpfung an. Austauschbar, damit der Rückfall aufs Kopieren testbar ist. */
    @FunctionalInterface
    interface Verknuepfer {
        void verknuepfe(Path ziel, Path quelle) throws IOException;
    }

    @PersistenceContext
    private EntityManager entityManager;

    private final Path uploadDir;
    private final Path anfrageDir;
    private final Path mailDir;
    /** Hier liegen die Fotos zu Anfrage-Notizen (die Handy-App speichert sie über den Bilder-Speicherplatz). */
    private final Path bilderDir;
    private final AtomicBoolean laeuft = new AtomicBoolean(false);
    private Verknuepfer verknuepfer = Files::createLink;

    public LesbareAblageService(@Value("${file.upload-dir}") String uploadDir,
            @Value("${file.offer-upload-dir:${file.upload-dir}}") String anfrageDir,
            @Value("${file.mail-attachment-dir}") String mailDir,
            @Value("${file.image-upload-dir:${file.upload-dir}}") String bilderDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
        this.anfrageDir = Path.of(anfrageDir).toAbsolutePath().normalize();
        this.mailDir = Path.of(mailDir).toAbsolutePath().normalize();
        this.bilderDir = Path.of(bilderDir).toAbsolutePath().normalize();
    }

    /** Nur für Tests: eigener Weg zum Verknüpfen. */
    void setVerknuepfer(Verknuepfer v) {
        this.verknuepfer = v;
    }

    public Path zielordner() {
        return uploadDir.resolve(ORDNER);
    }

    /**
     * Baut die lesbare Ablage auf (oder zeigt mit {@code probelauf} nur, was passieren würde).
     *
     * @throws ResponseStatusException 409, wenn gerade schon ein Aufbau läuft
     */
    @Transactional(readOnly = true)
    public Ergebnis aufbauen(boolean probelauf) {
        if (!laeuft.compareAndSet(false, true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Die lesbare Ablage wird gerade aufgebaut. Bitte einen Moment warten.");
        }
        try {
            int[] fehlend = { 0 };
            List<Eintrag> eintraege = sammle(fehlend);
            return spiegle(eintraege, probelauf, fehlend[0]);
        } finally {
            laeuft.set(false);
        }
    }

    /** Alle Dokumente mit ihrem lesbaren Pfad, wie sie auch die lesbare Ablage bekommt (für den Abgleich mit einer Netzwerkfreigabe). */
    @Transactional(readOnly = true)
    List<Eintrag> eintraege() {
        return sammle(new int[] { 0 });
    }

    // ------------------------------------------------------------------ Sammeln

    private List<Eintrag> sammle(int[] fehlend) {
        List<String> relativ = new ArrayList<>();
        List<Path> quellen = new ArrayList<>();

        for (ProjektDokument d : entityManager.createQuery(
                "select d from ProjektDokument d join fetch d.projekt p left join fetch p.kundenId order by d.id", ProjektDokument.class).getResultList()) {
            Path quelle = innerhalb(uploadDir, d.getGespeicherterDateiname());
            String pfad = projektOrdner(d.getProjekt()) + "/" + gruppe(d.getDokumentGruppe()) + "/"
                    + AblageNamen.datei(d.getOriginalDateiname(), "Dokument " + d.getId(), d.getGespeicherterDateiname());
            merke(quelle, pfad, relativ, quellen, fehlend);
        }

        for (AnfrageDokument d : entityManager.createQuery(
                "select d from AnfrageDokument d join fetch d.anfrage a left join fetch a.kunde order by d.id", AnfrageDokument.class).getResultList()) {
            Path quelle = innerhalb(anfrageDir, d.getGespeicherterDateiname());
            if (quelle == null || !Files.isRegularFile(quelle)) {
                quelle = innerhalb(uploadDir, d.getGespeicherterDateiname());   // ältere Anfragen liegen teils in der Hauptablage
            }
            String pfad = anfrageOrdner(d.getAnfrage()) + "/" + gruppe(d.getDokumentGruppe()) + "/"
                    + AblageNamen.datei(d.getOriginalDateiname(), "Dokument " + d.getId(), d.getGespeicherterDateiname());
            merke(quelle, pfad, relativ, quellen, fehlend);
        }

        // Fotos aus der Handy-App (Notizen am Projekt) liegen unter Bilder, neben den Dokumenten der Gruppe Bilder.
        for (ProjektNotizBild b : entityManager.createQuery(
                "select b from ProjektNotizBild b join fetch b.notiz n join fetch n.projekt p left join fetch p.kundenId order by b.id", ProjektNotizBild.class).getResultList()) {
            Path quelle = innerhalb(uploadDir, b.getGespeicherterDateiname());
            if (quelle == null || !Files.isRegularFile(quelle)) {
                quelle = innerhalb(bilderDir, b.getGespeicherterDateiname());   // beim Übernehmen einer Anfrage landen sie im Dokumenten-Ordner
            }
            String pfad = projektOrdner(b.getNotiz().getProjekt()) + "/" + gruppe(DokumentGruppe.BILDER) + "/"
                    + AblageNamen.datei(b.getOriginalDateiname(), "Foto " + b.getId(), b.getGespeicherterDateiname());
            merke(quelle, pfad, relativ, quellen, fehlend);
        }

        for (AnfrageNotizBild b : entityManager.createQuery(
                "select b from AnfrageNotizBild b join fetch b.notiz n join fetch n.anfrage a left join fetch a.kunde order by b.id", AnfrageNotizBild.class).getResultList()) {
            Path quelle = innerhalb(bilderDir, b.getGespeicherterDateiname());
            if (quelle == null || !Files.isRegularFile(quelle)) {
                quelle = innerhalb(uploadDir, b.getGespeicherterDateiname());
            }
            String pfad = anfrageOrdner(b.getNotiz().getAnfrage()) + "/" + gruppe(DokumentGruppe.BILDER) + "/"
                    + AblageNamen.datei(b.getOriginalDateiname(), "Foto " + b.getId(), b.getGespeicherterDateiname());
            merke(quelle, pfad, relativ, quellen, fehlend);
        }

        for (LieferantDokument d : entityManager.createQuery(
                "select d from LieferantDokument d join fetch d.lieferant left join fetch d.attachment where d.ausgeblendet = false order by d.id",
                LieferantDokument.class).getResultList()) {
            if (d.getTyp() == LieferantDokumentTyp.BELEG) {
                continue;   // Kassen- und Privatbelege bleiben draußen
            }
            Path quelle = findeLieferantendatei(d);
            String stored = d.getEffektiverGespeicherterDateiname();
            String original = d.getOriginalDateiname() != null ? d.getOriginalDateiname()
                    : d.getAttachment() != null ? d.getAttachment().getOriginalFilename() : null;
            String lieferant = AblageNamen.ordner(d.getLieferant().getLieferantenname(), "Lieferant " + d.getLieferant().getId());
            String pfad = "Lieferanten/" + lieferant + "/" + (d.getUploadDatum() == null ? "ohne Datum" : String.valueOf(d.getUploadDatum().getYear()))
                    + "/" + typ(d.getTyp()) + "/" + AblageNamen.datei(original, "Dokument " + d.getId(), stored);
            merke(quelle, pfad, relativ, quellen, fehlend);
        }

        // Eigene Angebote und Rechnungen als PDF (gebuchte Ausgangsdokumente). Liegt dieselbe Datei schon als Projektdokument vor, bleibt es dabei.
        java.util.Set<Path> bekannt = new HashSet<>(quellen);
        for (AusgangsGeschaeftsDokument d : entityManager.createQuery(
                "select d from AusgangsGeschaeftsDokument d left join fetch d.projekt pr left join fetch pr.kundenId left join fetch d.anfrage a left join fetch a.kunde where d.pdfDateiname is not null order by d.id",
                AusgangsGeschaeftsDokument.class).getResultList()) {
            Path quelle = innerhalb(uploadDir, d.getPdfDateiname());
            if (quelle != null && bekannt.contains(quelle)) {
                continue;
            }
            String ordner = d.getProjekt() != null ? projektOrdner(d.getProjekt())
                    : d.getAnfrage() != null ? anfrageOrdner(d.getAnfrage())
                    : "Ausgangsdokumente/" + jahr(d.getDatum());
            String name = (d.isStorniert() ? "STORNIERT " : "") + ausgangsTyp(d.getTyp()) + " " + nz(d.getDokumentNummer());
            String pfad = ordner + "/Ausgangsdokumente/" + AblageNamen.datei(name.strip() + ".pdf", "Dokument " + d.getId(), ".pdf");
            merke(quelle, pfad, relativ, quellen, fehlend);
            if (quelle != null) {
                bekannt.add(quelle);
            }
        }

        List<String> eindeutig = AblageNamen.eindeutig(relativ);
        List<Eintrag> ergebnis = new ArrayList<>(eindeutig.size());
        for (int i = 0; i < eindeutig.size(); i++) {
            ergebnis.add(new Eintrag(quellen.get(i), eindeutig.get(i)));
        }
        return ergebnis;
    }

    /** Ordner eines Projekts: Projekte/<Jahr>/<Kunde>/<Auftragsnummer> - <Bauvorhaben>; ohne Kunde steht „Ohne Kunde“. */
    static String projektOrdner(Projekt p) {
        String kunde = p.getKundenId() == null || nz(p.getKundenId().getName()).isBlank() ? "Ohne Kunde"
                : AblageNamen.ordner(nz(p.getKundenId().getName()), "Kunde " + p.getKundenId().getId());
        return "Projekte/" + jahr(p.getAnlegedatum()) + "/" + kunde + "/" + projektName(p);
    }

    /** Der frühere Ordner ohne Kunde: Projekte/<Jahr>/<Auftragsnummer> - <Bauvorhaben>. Nur noch zum Erkennen alter Ordner auf einer Netzwerkfreigabe. */
    static String projektOrdnerAlt(Projekt p) {
        return "Projekte/" + jahr(p.getAnlegedatum()) + "/" + projektName(p);
    }

    private static String projektName(Projekt p) {
        return AblageNamen.ordner((nz(p.getAuftragsnummer()) + " - " + nz(p.getBauvorhaben())).strip().replaceAll("^- | -$", ""), "Projekt " + p.getId());
    }

    /** Ordner einer Anfrage: Anfragen/<Jahr>/<Kunde> - <Bauvorhaben> (A<Nr>). */
    private static String anfrageOrdner(Anfrage a) {
        String kunde = a.getKunde() == null ? "" : nz(a.getKunde().getName());
        String name = AblageNamen.ordner((kunde + " - " + nz(a.getBauvorhaben())).strip().replaceAll("^- | -$", "") + " (A" + a.getId() + ")", "Anfrage " + a.getId());
        return "Anfragen/" + jahr(a.getAnlegedatum()) + "/" + name;
    }

    private static void merke(Path quelle, String pfad, List<String> relativ, List<Path> quellen, int[] fehlend) {
        if (quelle == null || !Files.isRegularFile(quelle)) {
            fehlend[0]++;
            return;
        }
        relativ.add(pfad);
        quellen.add(quelle);
    }

    /** Der Pfad zur Datei, nur wenn er wirklich unterhalb der Basis liegt (kein "../"). */
    static Path innerhalb(Path basis, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        Path p = basis.resolve(name).normalize();
        return p.startsWith(basis) ? p : null;
    }

    /** Dieselbe Suchreihenfolge wie beim Herunterladen eines Lieferantendokuments. */
    private Path findeLieferantendatei(LieferantDokument d) {
        String name = d.getEffektiverGespeicherterDateiname();
        if (name == null || name.isBlank()) {
            return null;
        }
        String lid = d.getLieferant() == null ? null : String.valueOf(d.getLieferant().getId());
        List<Path> kandidaten = new ArrayList<>();
        kandidaten.add(innerhalb(uploadDir, name));                                             // Mobile-Belege u. a. mit Unterordner im Namen
        if (lid != null) {
            kandidaten.add(innerhalb(mailDir.resolve("email").resolve(lid), name));              // E-Mail-Anhänge je Lieferant
            kandidaten.add(innerhalb(uploadDir.resolve("lieferanten").resolve(lid), name));      // manuell hochgeladen
            kandidaten.add(innerhalb(uploadDir.resolve("attachments").resolve("lieferanten").resolve(lid), name));
        }
        kandidaten.add(innerhalb(uploadDir.resolve("attachments").resolve("vendor-invoices"), name));
        kandidaten.add(innerhalb(mailDir, name));
        for (Path p : kandidaten) {
            if (p != null && Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Spiegeln

    Ergebnis spiegle(List<Eintrag> eintraege, boolean probelauf, int fehlend) {
        Path wurzel = zielordner().normalize();
        int neu = 0;
        int unveraendert = 0;
        int ersetzt = 0;
        int kopiert = 0;
        List<String> beispiele = new ArrayList<>();
        List<String> fehler = new ArrayList<>();
        Map<String, Path> soll = new LinkedHashMap<>();
        for (Eintrag e : eintraege) {
            soll.put(e.relativ().toLowerCase(Locale.ROOT), wurzel.resolve(e.relativ()).normalize());
        }

        for (Eintrag e : eintraege) {
            Path ziel = wurzel.resolve(e.relativ()).normalize();
            if (!ziel.startsWith(wurzel) || ziel.equals(wurzel)) {
                fehler.add("Übersprungen (ungültiger Pfad): " + e.relativ());
                continue;
            }
            try {
                boolean vorhanden = Files.exists(ziel, LinkOption.NOFOLLOW_LINKS);
                if (vorhanden && gleich(ziel, e.quelle())) {
                    unveraendert++;
                    continue;
                }
                if (vorhanden) {
                    ersetzt++;
                } else {
                    neu++;
                    if (beispiele.size() < 20) {
                        beispiele.add(e.relativ());
                    }
                }
                if (probelauf) {
                    continue;
                }
                Files.createDirectories(ziel.getParent());
                if (vorhanden) {
                    Files.delete(ziel);
                }
                try {
                    verknuepfer.verknuepfe(ziel, e.quelle());
                } catch (IOException | UnsupportedOperationException ex) {
                    // Hardlinks gehen nicht (anderes Laufwerk, Netzlaufwerk, Dateisystem): kopieren
                    Files.copy(e.quelle(), ziel, StandardCopyOption.COPY_ATTRIBUTES);
                    kopiert++;
                }
            } catch (IOException | RuntimeException ex) {
                fehler.add(e.relativ() + ": " + ex.getMessage());
                log.warn("Lesbare Ablage: {} konnte nicht angelegt werden", e.relativ(), ex);
            }
        }

        int entfernt = raeumeAuf(wurzel, soll, probelauf, fehler);
        if (!probelauf) {
            log.info("Lesbare Ablage aufgebaut: {} Dateien, neu {}, unverändert {}, ersetzt {}, entfernt {}, kopiert {}, fehlend {}",
                    eintraege.size(), neu, unveraendert, ersetzt, entfernt, kopiert, fehlend);
        }
        return new Ergebnis(probelauf, wurzel.toString(), eintraege.size(), neu, unveraendert, ersetzt, entfernt, kopiert, fehlend, beispiele, fehler);
    }

    /**
     * Ist das Ziel schon die richtige Datei? Hardlink: derselbe Speicher. Kopie: gleiche Größe und exakt gleiche Änderungszeit (die Kopie übernimmt sie).
     * Weicht die Zeit nur knapp ab (Dateisysteme runden), entscheidet der Inhalt, damit eine geänderte Datei nie übersehen wird.
     */
    private static boolean gleich(Path ziel, Path quelle) throws IOException {
        if (Files.isSymbolicLink(ziel) || !Files.isRegularFile(ziel, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        if (Files.isSameFile(ziel, quelle)) {
            return true;
        }
        if (Files.size(ziel) != Files.size(quelle)) {
            return false;
        }
        long abstand = Math.abs(Files.getLastModifiedTime(ziel).toMillis() - Files.getLastModifiedTime(quelle).toMillis());
        if (abstand == 0) {
            return true;
        }
        return abstand <= 2000 && Files.mismatch(ziel, quelle) == -1L;
    }

    /** Entfernt Einträge, die nicht mehr gebraucht werden, und leere Ordner. Rührt nur etwas unterhalb von {@code _lesbar} an. */
    private int raeumeAuf(Path wurzel, Map<String, Path> soll, boolean probelauf, List<String> fehler) {
        if (!Files.isDirectory(wurzel, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        Set<String> erwartet = new HashSet<>();
        soll.values().forEach(p -> erwartet.add(wurzel.relativize(p).toString().toLowerCase(Locale.ROOT)));
        int[] entfernt = { 0 };
        try {
            Files.walkFileTree(wurzel, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path datei, BasicFileAttributes attrs) {
                    String rel = wurzel.relativize(datei).toString().toLowerCase(Locale.ROOT);
                    if (!erwartet.contains(rel)) {
                        entfernt[0]++;
                        if (!probelauf) {
                            try {
                                Files.delete(datei);   // löscht den Eintrag, nie die Originaldatei (Hardlink) und folgt keinem Symlink
                            } catch (IOException ex) {
                                fehler.add("Konnte nicht entfernen: " + wurzel.relativize(datei) + ": " + ex.getMessage());
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path ordner, IOException exc) {
                    if (!probelauf && !ordner.equals(wurzel)) {
                        try {
                            Files.delete(ordner);   // klappt nur, wenn der Ordner leer ist
                        } catch (FileSystemException ignoriert) {
                            // nicht leer: bleibt
                        } catch (IOException ex) {
                            fehler.add("Konnte Ordner nicht entfernen: " + wurzel.relativize(ordner) + ": " + ex.getMessage());
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            fehler.add("Aufräumen unvollständig: " + ex.getMessage());
        }
        return entfernt[0];
    }

    // ------------------------------------------------------------------ Hilfen

    private static String jahr(LocalDate d) {
        return d == null ? "ohne Datum" : String.valueOf(d.getYear());
    }

    private static String nz(String s) {
        return s == null ? "" : s.strip();
    }

    static String gruppe(DokumentGruppe g) {
        if (g == null) {
            return "Diverse Dokumente";
        }
        return switch (g) {
            case BILDER -> "Bilder";
            case GESCHAEFTSDOKUMENTE -> "Geschaeftsdokumente";
            case PLANUNGSDOKUMENTE -> "Planungsdokumente";
            case KALKULATIONSDOKUMENTE -> "Kalkulation";
            case DOKUMENTATION_1090 -> "Dokumentation EN 1090";
            case EINGANGSRECHNUNGEN -> "Eingangsrechnungen";
            case DIVERSE_DOKUMENTE -> "Diverse Dokumente";
        };
    }

    private static String ausgangsTyp(AusgangsGeschaeftsDokumentTyp t) {
        if (t == null) {
            return "Dokument";
        }
        return switch (t) {
            case ANGEBOT -> "Angebot";
            case NACHTRAGSANGEBOT -> "Nachtragsangebot";
            case AUFTRAGSBESTAETIGUNG -> "Auftragsbestaetigung";
            case RECHNUNG -> "Rechnung";
            case TEILRECHNUNG -> "Teilrechnung";
            case ABSCHLAGSRECHNUNG -> "Abschlagsrechnung";
            case SCHLUSSRECHNUNG -> "Schlussrechnung";
            case GUTSCHRIFT -> "Gutschrift";
            case STORNO -> "Storno";
            case ZAHLUNGSERINNERUNG -> "Zahlungserinnerung";
            case ERSTE_MAHNUNG -> "1. Mahnung";
            case ZWEITE_MAHNUNG -> "2. Mahnung";
        };
    }

    private static String typ(LieferantDokumentTyp t) {
        if (t == null) {
            return "Sonstiges";
        }
        return switch (t) {
            case ANGEBOT -> "Angebote";
            case AUFTRAGSBESTAETIGUNG -> "Auftragsbestaetigungen";
            case LIEFERSCHEIN -> "Lieferscheine";
            case RECHNUNG -> "Rechnungen";
            case GUTSCHRIFT -> "Gutschriften";
            case WERKSTOFFZEUGNIS -> "Werkstoffzeugnisse";
            case SONSTIG, BELEG -> "Sonstiges";
        };
    }
}
