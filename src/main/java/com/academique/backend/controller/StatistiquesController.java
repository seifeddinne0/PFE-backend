package com.academique.backend.controller;

import com.academique.backend.repository.*;
import com.academique.backend.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import java.util.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;

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

        Map<String, Long> documentsByType = new LinkedHashMap<>();
        for (DemandeDocument.TypeDocument type : DemandeDocument.TypeDocument.values()) {
            documentsByType.put(type.name(), demandeDocumentRepository.countByTypeDocument(type));
        }
        stats.put("documentsByType", documentsByType);

        stats.put("documentsAvgProcessingDays", calculateDocumentsAvgProcessingDays());

        double currentMonthAvg = calculateDocumentsAvgProcessingDaysForMonth(LocalDate.now());
        double previousMonthAvg = calculateDocumentsAvgProcessingDaysForMonth(LocalDate.now().minusMonths(1));
        double changePercent = 0.0;
        String trend = "stable";

        if (previousMonthAvg > 0) {
            double delta = currentMonthAvg - previousMonthAvg;
            changePercent = Math.round(Math.abs(delta / previousMonthAvg * 1000.0)) / 10.0;
            if (delta > 0.01) {
                trend = "up";
            } else if (delta < -0.01) {
                trend = "down";
            }
        }

        stats.put("documentsAvgProcessingChangePercent", changePercent);
        stats.put("documentsAvgProcessingTrend", trend);

        // Recettes par mois (factures payees)
        List<Map<String, Object>> recettesParMois = new ArrayList<>();
        for (Object[] row : factureRepository.totalPayeParMois()) {
            Integer year = row[0] != null ? ((Number) row[0]).intValue() : null;
            Integer month = row[1] != null ? ((Number) row[1]).intValue() : null;
            Double total = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            if (year == null || month == null) continue;
            String label = String.format("%04d-%02d", year, month);
            recettesParMois.add(Map.of("month", label, "total", total));
        }
        stats.put("recettesParMois", recettesParMois);

        // Top 5 classes par absences
        List<Map<String, Object>> topClassesAbsences = new ArrayList<>();
        for (Object[] row : absenceRepository.findTopClassesByAbsences(PageRequest.of(0, 5))) {
            String classeCode = row[0] != null ? row[0].toString() : "-";
            long total = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            topClassesAbsences.add(Map.of("classeCode", classeCode, "totalAbsences", total));
        }
        stats.put("topClassesAbsences", topClassesAbsences);

        return ResponseEntity.ok(stats);
    }

    private double calculateDocumentsAvgProcessingDays() {
        List<DemandeDocument> all = demandeDocumentRepository.findAll();
        long totalMinutes = 0L;
        long resolvedCount = 0L;

        for (DemandeDocument doc : all) {
            if (!isResolved(doc.getStatut())) {
                continue;
            }
            if (doc.getCreatedAt() == null || doc.getUpdatedAt() == null) {
                continue;
            }
            long minutes = Duration.between(doc.getCreatedAt(), doc.getUpdatedAt()).toMinutes();
            if (minutes < 0) {
                continue;
            }
            totalMinutes += minutes;
            resolvedCount += 1;
        }

        if (resolvedCount == 0) {
            return 0.0;
        }

        double avgDays = (totalMinutes / 60.0) / 24.0;
        return Math.round(avgDays * 10.0) / 10.0;
    }

    private double calculateDocumentsAvgProcessingDaysForMonth(LocalDate referenceDate) {
        YearMonth targetMonth = YearMonth.from(referenceDate);
        List<DemandeDocument> all = demandeDocumentRepository.findAll();
        long totalMinutes = 0L;
        long resolvedCount = 0L;

        for (DemandeDocument doc : all) {
            if (!isResolved(doc.getStatut())) {
                continue;
            }
            if (doc.getCreatedAt() == null || doc.getUpdatedAt() == null) {
                continue;
            }
            YearMonth updatedMonth = YearMonth.from(doc.getUpdatedAt());
            if (!updatedMonth.equals(targetMonth)) {
                continue;
            }
            long minutes = Duration.between(doc.getCreatedAt(), doc.getUpdatedAt()).toMinutes();
            if (minutes < 0) {
                continue;
            }
            totalMinutes += minutes;
            resolvedCount += 1;
        }

        if (resolvedCount == 0) {
            return 0.0;
        }

        double avgDays = (totalMinutes / 60.0) / 24.0;
        return Math.round(avgDays * 10.0) / 10.0;
    }

    private boolean isResolved(DemandeDocument.Statut statut) {
        return statut == DemandeDocument.Statut.VALIDEE
            || statut == DemandeDocument.Statut.REJETEE
            || statut == DemandeDocument.Statut.ENVOYEE;
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