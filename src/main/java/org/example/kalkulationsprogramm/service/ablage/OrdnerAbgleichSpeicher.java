package org.example.kalkulationsprogramm.service.ablage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Abgleich mit einem Ordner im Dateisystem (zum Beispiel ein schon eingebundenes Netzlaufwerk). In den Tests steht er für die Freigabe. */
public class OrdnerAbgleichSpeicher implements AbgleichSpeicher {

    private static final String INTERN = "_erp";

    private final Path basis;

    public OrdnerAbgleichSpeicher(Path basis) {
        this.basis = basis.toAbsolutePath().normalize();
    }

    private Path pfad(String relativ) throws IOException {
        Path p = basis.resolve(relativ).normalize();
        if (!p.startsWith(basis) || p.equals(basis)) {
            throw new IOException("Ungültiger Pfad: " + relativ);
        }
        return p;
    }

    @Override
    public List<Datei> liste() throws IOException {
        List<Datei> ergebnis = new ArrayList<>();
        if (!Files.isDirectory(basis)) {
            return ergebnis;
        }
        try (Stream<Path> s = Files.walk(basis)) {
            for (Path p : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                String rel = basis.relativize(p).toString().replace('\\', '/');
                if (rel.equals(INTERN) || rel.startsWith(INTERN + "/")) {
                    continue;
                }
                ergebnis.add(new Datei(rel, Files.size(p), Files.getLastModifiedTime(p).toMillis()));
            }
        }
        return ergebnis;
    }

    @Override
    public Optional<Datei> info(String relativ) throws IOException {
        Path p = pfad(relativ);
        if (!Files.isRegularFile(p)) {
            return Optional.empty();
        }
        return Optional.of(new Datei(relativ, Files.size(p), Files.getLastModifiedTime(p).toMillis()));
    }

    @Override
    public byte[] lies(String relativ) throws IOException {
        return Files.readAllBytes(pfad(relativ));
    }

    @Override
    public void schreibe(String relativ, byte[] inhalt) throws IOException {
        Path p = pfad(relativ);
        Files.createDirectories(p.getParent());
        Files.write(p, inhalt);
    }

    @Override
    public void close() {
        // nichts offen
    }
}
