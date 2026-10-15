package org.example.kalkulationsprogramm.controller;

import org.example.kalkulationsprogramm.service.ki.assistent.KiDokumentService;
import org.example.kalkulationsprogramm.service.ki.assistent.KiDokumentService.Dokument;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;

/** Dokument im KI-Chat (Issue #182): liest PDF und Textdateien und gibt den Text zurück. Speichert nichts. */
@RestController
@RequestMapping("/api/ki-assistent/dokument")
@RequiredArgsConstructor
public class KiDokumentController {

    private final KiDokumentService service;
    private final org.example.kalkulationsprogramm.service.ki.assistent.KiEmailAnhangService emailAnhang;

    @PostMapping(value = "/lesen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Dokument lesen(@RequestPart("datei") MultipartFile datei) {
        return service.lies(datei);
    }

    /** Eine E-Mail aus dem E-Mail-Center als Chat-Anhang (Text, Absender, Betreff, Nummer). Verändert nichts. */
    @org.springframework.web.bind.annotation.GetMapping("/email/{id}")
    public Dokument email(@org.springframework.web.bind.annotation.PathVariable Long id) {
        try {
            return emailAnhang.anhang(id);
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
