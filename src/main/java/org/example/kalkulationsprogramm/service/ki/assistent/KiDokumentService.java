package org.example.kalkulationsprogramm.service.ki.assistent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.kalkulationsprogramm.service.ki.KiAnbieterService;
import org.example.kalkulationsprogramm.service.ki.KiObergrenze;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;

/**
 * Ein Dokument im KI-Chat (Issue #182): Die Datei wird <b>nur gelesen</b> (PDF oder Textdatei) und der Text an die Oberfläche zurückgegeben. Es wird nichts gespeichert.
 * Die Oberfläche hängt den Text an die Fragen des Gesprächs an, damit man dem Dokument eine Aufgabe stellen kann. Bilder können noch nicht gelesen werden; sie lassen sich
 * weiter als Beleg ablegen.
 * Die zentrale Obergrenze gilt: Ist die KI aus oder dürfen Programmdaten nicht zum Anbieter, wird das Dokument gar nicht erst angenommen.
 */
@Service
@RequiredArgsConstructor
public class KiDokumentService {

    static final long MAX_BYTES = 15L * 1024 * 1024;
    static final int MAX_ZEICHEN = 12_000;
    static final int MAX_SEITEN = 30;

    private final KiAnbieterService anbieterService;

    /** {@code text} ist {@code null}, wenn die Datei nicht gelesen werden kann (zum Beispiel ein Bild); {@code hinweis} sagt dann warum. */
    public record Dokument(String name, String text, boolean gekuerzt, int seiten, String hinweis) { }

    public Dokument lies(MultipartFile datei) {
        if (datei == null || datei.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte eine Datei auswählen.");
        }
        if (datei.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Datei ist zu groß (höchstens 15 MB).");
        }
        sperreBeachten();
        String name = dateiname(datei.getOriginalFilename());
        String endung = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        try {
            byte[] bytes = datei.getBytes();
            return switch (endung) {
                case "pdf" -> aus(name, pdfText(bytes), seiten(bytes));
                case "txt", "csv", "xml", "json", "md" -> aus(name, new String(bytes, StandardCharsets.UTF_8), 1);
                default -> new Dokument(name, null, false, 0, "Diese Datei kann die KI hier noch nicht lesen (nur PDF und Textdateien). Als Beleg ablegen geht trotzdem.");
            };
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Die Datei konnte nicht gelesen werden.");
        }
    }

    private static Dokument aus(String name, String roh, int seiten) {
        String text = roh == null ? "" : roh.replace("\u0000", "").replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        if (text.isEmpty()) {
            return new Dokument(name, null, false, seiten, "In dieser Datei steht kein lesbarer Text (zum Beispiel ein Scan). Als Beleg ablegen geht trotzdem.");
        }
        boolean gekuerzt = text.length() > MAX_ZEICHEN;
        return new Dokument(name, gekuerzt ? text.substring(0, MAX_ZEICHEN) : text, gekuerzt, seiten, gekuerzt ? "Das Dokument ist lang: Die KI sieht nur den Anfang." : null);
    }

    static String pdfText(byte[] bytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(pdf.getNumberOfPages(), MAX_SEITEN));
            return stripper.getText(pdf);
        }
    }

    static int seiten(byte[] bytes) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            return pdf.getNumberOfPages();
        }
    }

    private void sperreBeachten() {
        if (anbieterService.eingriff() == KiObergrenze.Eingriff.AUS) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Die KI ist ausgeschaltet (Einstellungen → KI → Grenzen).");
        }
        var typ = anbieterService.einstellung(org.example.kalkulationsprogramm.service.ki.KiFunktion.ASSISTENT).anbieter();
        if (anbieterService.sperrtProgrammdaten(typ)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Nach der Einstellung „Wie weit darf die KI gehen?“ darf ein Dokument nicht an diesen KI-Anbieter gehen. Bitte einen lokalen Anbieter wählen.");
        }
    }

    private static String dateiname(String original) {
        String n = original == null || original.isBlank() ? "dokument.pdf" : Path.of(original).getFileName().toString();
        return n.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
