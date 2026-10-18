package org.example.kalkulationsprogramm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.example.kalkulationsprogramm.domain.Firmeninformation;
import org.example.kalkulationsprogramm.dto.FirmeninformationDto;
import org.example.kalkulationsprogramm.repository.FirmeninformationRepository;
import org.example.kalkulationsprogramm.repository.GewerkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

class FirmeninformationBriefpapierTest {

    @TempDir Path tmp;
    FirmeninformationService service;
    Firmeninformation firma;

    @BeforeEach
    void setUp() {
        FirmeninformationRepository repo = mock(FirmeninformationRepository.class);
        firma = new Firmeninformation();
        firma.setId(1L);
        firma.setFirmenname("Musterbetrieb");
        when(repo.getOrCreate()).thenReturn(firma);
        when(repo.findFirmeninformation()).thenReturn(Optional.of(firma));
        when(repo.save(any(Firmeninformation.class))).thenAnswer(i -> i.getArgument(0));
        service = new FirmeninformationService(repo, mock(GewerkRepository.class));
        ReflectionTestUtils.setField(service, "briefpapierUploadDir", tmp.resolve("bp").toString());
        ReflectionTestUtils.setField(service, "logoUploadDir", tmp.resolve("logo").toString());
    }

    private static byte[] png(int b, int h) throws IOException {
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(b, h, BufferedImage.TYPE_INT_RGB), "png", aus);
        return aus.toByteArray();
    }

    private static byte[] pdf() throws Exception {
        ByteArrayOutputStream aus = new ByteArrayOutputStream();
        Document d = new Document();
        PdfWriter.getInstance(d, aus);
        d.open();
        d.add(new Paragraph("Briefkopf"));
        d.close();
        return aus.toByteArray();
    }

    @Test
    void speichertPngUndLiefertEsZurueck() throws Exception {
        FirmeninformationDto dto = service.speichereBriefpapier(new MockMultipartFile("datei", "mein briefpapier.png", MediaType.IMAGE_PNG_VALUE, png(600, 800)));

        assertEquals("briefpapier.png", dto.getBriefpapierDateiname(), "der Name kommt vom Server, nie vom Client");
        FirmeninformationService.Briefpapier bp = service.loadBriefpapier();
        assertNotNull(bp);
        assertFalse(bp.istPdf());
        assertEquals(MediaType.IMAGE_PNG_VALUE, bp.contentType());
    }

    @Test
    void riesigesBildWirdBeimSpeichernVerkleinert() throws Exception {
        byte[] gross = png(6200, 8800);

        service.speichereBriefpapier(new MockMultipartFile("datei", "x.png", MediaType.IMAGE_PNG_VALUE, gross));

        BufferedImage gespeichert = ImageIO.read(new java.io.ByteArrayInputStream(service.loadBriefpapier().bytes()));
        assertTrue(gespeichert.getWidth() <= BriefpapierBild.MAX_BREITE && gespeichert.getHeight() <= BriefpapierBild.MAX_HOEHE);
    }

    @Test
    void speichertEinPdfAlsBriefpapier() throws Exception {
        service.speichereBriefpapier(new MockMultipartFile("datei", "x.pdf", MediaType.APPLICATION_PDF_VALUE, pdf()));

        FirmeninformationService.Briefpapier bp = service.loadBriefpapier();
        assertTrue(bp.istPdf());
        assertEquals("briefpapier.pdf", firma.getBriefpapierDateiname());
    }

    @Test
    void ersetztDasBisherigeBriefpapierAuchBeiAndererEndung() throws Exception {
        service.speichereBriefpapier(new MockMultipartFile("datei", "a.png", MediaType.IMAGE_PNG_VALUE, png(300, 400)));
        service.speichereBriefpapier(new MockMultipartFile("datei", "a.pdf", MediaType.APPLICATION_PDF_VALUE, pdf()));

        assertFalse(Files.exists(tmp.resolve("bp/briefpapier.png")), "das alte Bild bleibt nicht liegen");
        assertTrue(Files.exists(tmp.resolve("bp/briefpapier.pdf")));
    }

    @Test
    void lehntUngueltigeDateienAb() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.speichereBriefpapier(null));
        assertThrows(IllegalArgumentException.class, () -> service.speichereBriefpapier(new MockMultipartFile("datei", "x.png", MediaType.IMAGE_PNG_VALUE, new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> service.speichereBriefpapier(new MockMultipartFile("datei", "x.exe", "application/octet-stream", new byte[] { 1, 2 })));
        assertThrows(IllegalArgumentException.class, () -> service.speichereBriefpapier(new MockMultipartFile("datei", "x.js", "text/javascript", "alert(1)".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> service.speichereBriefpapier(new MockMultipartFile("datei", "x.png", MediaType.IMAGE_PNG_VALUE, "kein Bild".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> service.speichereBriefpapier(new MockMultipartFile("datei", "x.pdf", MediaType.APPLICATION_PDF_VALUE, "kein PDF".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> service.speichereBriefpapier(
                new MockMultipartFile("datei", "x.png", MediaType.IMAGE_PNG_VALUE, new byte[(int) FirmeninformationService.MAX_BRIEFPAPIER_BYTES + 1])));
        assertNull(firma.getBriefpapierDateiname());
    }

    @Test
    void pfadAusbruchImClientDateinamenWirktNicht() throws Exception {
        service.speichereBriefpapier(new MockMultipartFile("datei", "../../../etc/passwd.png", MediaType.IMAGE_PNG_VALUE, png(100, 100)));

        assertEquals("briefpapier.png", firma.getBriefpapierDateiname());
        assertTrue(Files.exists(tmp.resolve("bp/briefpapier.png")));
        try (var s = Files.list(tmp)) {
            assertEquals(1, s.filter(p -> !p.getFileName().toString().equals("bp")).count() + 1, "außerhalb von bp/ wurde nichts angelegt");
        }
    }

    @Test
    void loeschenEntferntDateiUndEintragUndIstWiederholbar() throws Exception {
        service.speichereBriefpapier(new MockMultipartFile("datei", "x.png", MediaType.IMAGE_PNG_VALUE, png(100, 100)));
        firma.setBriefpapierEnthaeltKopf(true);

        service.loescheBriefpapier();
        service.loescheBriefpapier();

        assertNull(service.loadBriefpapier());
        assertNull(firma.getBriefpapierDateiname());
        assertFalse(firma.isBriefpapierEnthaeltKopf(), "ohne Briefpapier gibt es keinen Kopf darin");
    }

    @Test
    void fehlendeDateiAufDerPlatteIstKeinFehler() throws Exception {
        firma.setBriefpapierDateiname("briefpapier.png");

        assertNull(service.loadBriefpapier());
    }

    @Test
    void dasKopfFlagWirdMitDerFirmaGespeichertUndGeliefert() {
        FirmeninformationDto dto = new FirmeninformationDto();
        dto.setFirmenname("Musterbetrieb");
        dto.setBriefpapierEnthaeltKopf(true);
        dto.setBriefpapierDateiname("irgendwas.png");   // wird vom Client nie übernommen

        FirmeninformationDto antwort = service.speichern(dto);

        assertTrue(antwort.isBriefpapierEnthaeltKopf());
        assertNull(firma.getBriefpapierDateiname(), "der Dateiname ändert sich nur über den Upload");
    }

    @Test
    void dasKopfFlagBrauchtEinBriefpapier() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.setzeBriefpapierEnthaeltKopf(true));
        assertFalse(service.setzeBriefpapierEnthaeltKopf(false).isBriefpapierEnthaeltKopf());

        service.speichereBriefpapier(new MockMultipartFile("datei", "x.png", MediaType.IMAGE_PNG_VALUE, png(100, 100)));
        assertTrue(service.setzeBriefpapierEnthaeltKopf(true).isBriefpapierEnthaeltKopf());
    }
}
