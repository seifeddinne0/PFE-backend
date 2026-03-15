package com.academique.backend.controller;

import com.academique.backend.repository.*;
import com.academique.backend.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/statistiques")
@RequiredArgsConstructor
public class StatistiquesController {

    private final EtudiantRepository etudiantRepository;
    private final EnseignantRepository enseignantRepository;
    private final NoteRepository noteRepository;
    private final AbsenceRepository absenceRepository;
    private final FactureRepository factureRepository;
    private final DemandeDocumentRepository demandeDocumentRepository;

    // ─── DASHBOARD GLOBAL ─────────────────────────────────
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();

        // Étudiants
        stats.put("totalEtudiants", etudiantRepository.count());
        stats.put("etudiantsActifs", etudiantRepository.countByStatut(Etudiant.Statut.ACTIF));
        stats.put("etudiantsSuspendus", etudiantRepository.countByStatut(Etudiant.Statut.SUSPENDU));

        // Enseignants
        stats.put("totalEnseignants", enseignantRepository.count());

        // Absences
        stats.put("totalAbsences", absenceRepository.count());
        stats.put("absencesNonJustifiees",
            absenceRepository.countByStatut(Absence.Statut.NON_JUSTIFIEE));
        stats.put("absencesJustifiees",
            absenceRepository.countByStatut(Absence.Statut.JUSTIFIEE));

        // Factures
        stats.put("totalFactures", factureRepository.count());
        stats.put("facturesPayees",
            factureRepository.countByStatut(Facture.Statut.PAYEE));
        stats.put("facturesNonPayees",
            factureRepository.countByStatut(Facture.Statut.NON_PAYEE));
        stats.put("montantTotalPaye",
            factureRepository.totalPaye() != null ? factureRepository.totalPaye() : 0.0);
        stats.put("montantTotalImpaye",
            factureRepository.totalImpaye() != null ? factureRepository.totalImpaye() : 0.0);

        // Documents
        stats.put("totalDocuments", demandeDocumentRepository.count());
        stats.put("documentsEnAttente",
            demandeDocumentRepository.countByStatut(DemandeDocument.Statut.EN_ATTENTE));
        stats.put("documentsValides",
            demandeDocumentRepository.countByStatut(DemandeDocument.Statut.VALIDEE));
        stats.put("documentsEnvoyes",
            demandeDocumentRepository.countByStatut(DemandeDocument.Statut.ENVOYEE));

        return ResponseEntity.ok(stats);
    }

    // ─── STATS ÉTUDIANTS ──────────────────────────────────
    @GetMapping("/etudiants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatsEtudiants() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", etudiantRepository.count());
        stats.put("actifs", etudiantRepository.countByStatut(Etudiant.Statut.ACTIF));
        stats.put("inactifs", etudiantRepository.countByStatut(Etudiant.Statut.INACTIF));
        stats.put("suspendus", etudiantRepository.countByStatut(Etudiant.Statut.SUSPENDU));
        return ResponseEntity.ok(stats);
    }

    // ─── STATS ABSENCES ───────────────────────────────────
    @GetMapping("/absences")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatsAbsences() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", absenceRepository.count());
        stats.put("justifiees", absenceRepository.countByStatut(Absence.Statut.JUSTIFIEE));
        stats.put("nonJustifiees", absenceRepository.countByStatut(Absence.Statut.NON_JUSTIFIEE));
        stats.put("enAttente", absenceRepository.countByStatut(Absence.Statut.EN_ATTENTE));
        stats.put("avecAlertes", absenceRepository.countByAlerte(true));
        return ResponseEntity.ok(stats);
    }

    // ─── STATS FACTURES ───────────────────────────────────
    @GetMapping("/factures")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatsFactures() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", factureRepository.count());
        stats.put("payees", factureRepository.countByStatut(Facture.Statut.PAYEE));
        stats.put("nonPayees", factureRepository.countByStatut(Facture.Statut.NON_PAYEE));
        stats.put("enAttente", factureRepository.countByStatut(Facture.Statut.EN_ATTENTE));
        stats.put("annulees", factureRepository.countByStatut(Facture.Statut.ANNULEE));
        stats.put("montantPaye",
            factureRepository.totalPaye() != null ? factureRepository.totalPaye() : 0.0);
        stats.put("montantImpaye",
            factureRepository.totalImpaye() != null ? factureRepository.totalImpaye() : 0.0);
        return ResponseEntity.ok(stats);
    }

    // ─── STATS DOCUMENTS ──────────────────────────────────
    @GetMapping("/documents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStatsDocuments() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", demandeDocumentRepository.count());
        stats.put("enAttente",
            demandeDocumentRepository.countByStatut(DemandeDocument.Statut.EN_ATTENTE));
        stats.put("enCoursValidation",
            demandeDocumentRepository.countByStatut(DemandeDocument.Statut.EN_COURS_VALIDATION));
        stats.put("validees",
            demandeDocumentRepository.countByStatut(DemandeDocument.Statut.VALIDEE));
        stats.put("rejetees",
            demandeDocumentRepository.countByStatut(DemandeDocument.Statut.REJETEE));
        stats.put("envoyees",
            demandeDocumentRepository.countByStatut(DemandeDocument.Statut.ENVOYEE));

        // Par type
        Map<String, Long> parType = new LinkedHashMap<>();
        for (DemandeDocument.TypeDocument type : DemandeDocument.TypeDocument.values()) {
            parType.put(type.name(), demandeDocumentRepository.countByTypeDocument(type));
        }
        stats.put("parType", parType);

        return ResponseEntity.ok(stats);
    }
}