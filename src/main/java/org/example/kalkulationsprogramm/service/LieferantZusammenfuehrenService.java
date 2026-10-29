package org.example.kalkulationsprogramm.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.example.kalkulationsprogramm.domain.Lieferanten;
import org.example.kalkulationsprogramm.repository.BelegRepository;
import org.example.kalkulationsprogramm.repository.LieferantenBestellungRepository;
import org.example.kalkulationsprogramm.repository.LieferantenRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class LieferantZusammenfuehrenService {

    private final LieferantenRepository lieferantenRepo;
    private final LieferantenBestellungRepository bestellungRepo;
    private final BelegRepository belegRepo;
    @PersistenceContext
    private EntityManager em;

    public void zusammenfuehren(Long quellId, Long zielId) {
        if (quellId.equals(zielId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Quell und Ziel dürfen nicht identisch sein.");
        }
        Lieferanten quell = lieferantenRepo.findById(quellId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Quell-Lieferant nicht gefunden."));
        Lieferanten ziel = lieferantenRepo.findById(zielId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ziel-Lieferant nicht gefunden."));

        if (!quell.isArchiviert()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nur archivierte Lieferanten können zusammengeführt werden.");
        }

        long offeneBestellungen = bestellungRepo.countByLieferant_Id(quellId);
        if (offeneBestellungen > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Der Lieferant hat " + offeneBestellungen + " Bestellung(en) – diese müssen zuerst storniert werden.");
        }

        long belege = belegRepo.countByLieferant_Id(quellId);
        if (belege > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Der Lieferant hat " + belege + " Eingangsbeleg(e) – zusammenführen nicht möglich.");
        }

        // Stammdaten des Ziels um fehlende Werte aus der Quelle ergänzen
        if (ziel.getTelefon() == null && quell.getTelefon() != null)
            ziel.setTelefon(quell.getTelefon());
        if (ziel.getMobiltelefon() == null && quell.getMobiltelefon() != null)
            ziel.setMobiltelefon(quell.getMobiltelefon());
        if (ziel.getVertreter() == null && quell.getVertreter() != null)
            ziel.setVertreter(quell.getVertreter());
        if (ziel.getEigeneKundennummer() == null && quell.getEigeneKundennummer() != null)
            ziel.setEigeneKundennummer(quell.getEigeneKundennummer());

        // ElementCollections zusammenführen
        quell.getKundenEmails().stream()
                .filter(e -> !ziel.getKundenEmails().contains(e))
                .forEach(ziel.getKundenEmails()::add);
        ziel.getRollen().addAll(quell.getRollen());

        em.flush(); // ElementCollection-Änderungen wegschreiben

        // JPQL-Bulk-Updates für alle @ManyToOne Referenzen
        reassign("LieferantNotiz", quell, ziel);
        reassign("LieferantBild", quell, ziel);
        reassign("LieferantReklamation", quell, ziel);
        reassign("LieferantDokument", quell, ziel);
        reassign("Email", quell, ziel);
        reassign("KontaktRufnummer", quell, ziel);
        reassign("TelefonAnruf", quell, ziel);
        reassign("ArtikelInProjekt", quell, ziel);
        reassign("Fremdleistung", quell, ziel);
        reassign("KalenderEintrag", quell, ziel);
        reassign("Materialkosten", quell, ziel);
        reassign("ProjektDokument", quell, ziel);
        reassign("Sprachnachricht", quell, ziel);

        // ArtikelPreise: Konflikte (selber Artikel bereits beim Ziel) löschen, Rest umhängen
        em.createQuery("""
                DELETE FROM LieferantenArtikelPreise qp
                 WHERE qp.lieferant = :quell
                   AND EXISTS (SELECT zp FROM LieferantenArtikelPreise zp
                                WHERE zp.lieferant = :ziel AND zp.artikel = qp.artikel)
                """)
                .setParameter("quell", quell)
                .setParameter("ziel", ziel)
                .executeUpdate();
        reassign("LieferantenArtikelPreise", quell, ziel);

        // PlanVorgang speichert lieferant_id als plain Long → native SQL
        em.createNativeQuery("UPDATE plan_vorgang SET lieferant_id = :ziel WHERE lieferant_id = :quell")
                .setParameter("ziel", zielId)
                .setParameter("quell", quellId)
                .executeUpdate();

        em.flush();
        em.clear();

        lieferantenRepo.deleteById(quellId);
    }

    private void reassign(String entityName, Lieferanten quell, Lieferanten ziel) {
        em.createQuery("UPDATE " + entityName + " e SET e.lieferant = :ziel WHERE e.lieferant = :quell")
                .setParameter("ziel", ziel)
                .setParameter("quell", quell)
                .executeUpdate();
    }
}
