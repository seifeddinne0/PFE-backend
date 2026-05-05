package com.academique.backend.controller;

import com.academique.backend.repository.*;
import com.academique.backend.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final EtudiantRepository etudiantRepository;
    private final EnseignantRepository enseignantRepository;
    private final AbsenceRepository absenceRepository;
    private final NoteRepository noteRepository;
    private final MatiereRepository matiereRepository;
    private final DemandeDocumentRepository demandeDocumentRepository;
    private final FactureRepository factureRepository;

    // US1.11 — Dashboard Étudiant
    @GetMapping("/etudiant/dashboard")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<Map<String, Object>> dashboardEtudiant(Authentication authentication) {
        String email = authentication.getName();
        Etudiant etudiant = etudiantRepository.findByEmail(email).orElse(null);

        long totalMatieres = 0;
        String semestreActuel = null;
        boolean isPfe = false;
        if (etudiant != null) {
            String niveauCode = null;
            if (etudiant.getClasse() != null && etudiant.getClasse().getNiveau() != null) {
                niveauCode = etudiant.getClasse().getNiveau().getCode();
            } else if (etudiant.getClasse() != null) {
                niveauCode = etudiant.getClasse().getCode();
            } else {
                niveauCode = etudiant.getMatricule();
            }

            String normalizedNiveau = extractNiveauCode(niveauCode);
            LocalDate today = LocalDate.now();
            boolean afterCutoff = isAfterJanuaryCutoff(today);

            if ("LCS1".equals(normalizedNiveau)) {
                semestreActuel = afterCutoff ? "S2" : "S1";
            } else if ("LCS2".equals(normalizedNiveau)) {
                semestreActuel = afterCutoff ? "S4" : "S3";
            } else if ("LCS3".equals(normalizedNiveau)) {
                if (afterCutoff) {
                    isPfe = true;
                    semestreActuel = "STAGE_PFE";
                } else {
                    semestreActuel = "S5";
                }
            }

            if (!isPfe && semestreActuel != null && !"STAGE_PFE".equals(semestreActuel)) {
                totalMatieres = matiereRepository.countBySemestreIn(
                    java.util.List.of(Matiere.Semestre.valueOf(semestreActuel))
                );
            }
        } else {
            totalMatieres = matiereRepository.count();
        }
        long totalAbsences = 0;
        long totalEvaluations = 0;
        long totalDocuments = 0;
        long documentsEnAttente = 0;
        long totalFactures = 0;
        long facturesNonPayees = 0;
        long facturesPayees = 0;

        if (etudiant != null) {
            totalAbsences = absenceRepository.countByEtudiantId(etudiant.getId());
            totalEvaluations = noteRepository.findByEtudiantId(etudiant.getId()).size();

            List<DemandeDocument> documents = demandeDocumentRepository.findByEtudiantId(etudiant.getId());
            totalDocuments = documents.size();
            documentsEnAttente = documents.stream()
                    .filter(doc -> doc.getStatut() == DemandeDocument.Statut.EN_ATTENTE
                            || doc.getStatut() == DemandeDocument.Statut.EN_COURS_VALIDATION)
                    .count();

            List<Facture> factures = factureRepository.findByEtudiantId(etudiant.getId());
            totalFactures = factures.size();
            facturesNonPayees = factures.stream()
                    .filter(facture -> facture.getStatut() == Facture.Statut.NON_PAYEE
                            || facture.getStatut() == Facture.Statut.EN_ATTENTE)
                    .count();
            facturesPayees = factures.stream()
                    .filter(facture -> facture.getStatut() == Facture.Statut.PAYEE)
                    .count();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("role", "ETUDIANT");
        response.put("email", email);
        response.put("totalMatieres", totalMatieres);
        response.put("semestreActuel", semestreActuel);
        response.put("isPfe", isPfe);
        response.put("totalAbsences", totalAbsences);
        response.put("totalEvaluations", totalEvaluations);
        response.put("totalDocuments", totalDocuments);
        response.put("documentsEnAttente", documentsEnAttente);
        response.put("totalFactures", totalFactures);
        response.put("facturesNonPayees", facturesNonPayees);
        response.put("facturesPayees", facturesPayees);
        if (etudiant != null) {
            response.put("nom", etudiant.getNom());
            response.put("prenom", etudiant.getPrenom());
            if (etudiant.getUser() != null && etudiant.getUser().getPhoto() != null) {
                response.put("photo", etudiant.getUser().getPhoto());
            }
            if (etudiant.getClasse() != null && etudiant.getClasse().getNiveau() != null) {
                response.put("niveauCode", etudiant.getClasse().getNiveau().getCode());
            }
        }
        return ResponseEntity.ok(response);
    }

    // US1.12 — Dashboard Enseignant
    @GetMapping("/enseignant/dashboard")
    @PreAuthorize("hasRole('ENSEIGNANT')")
    public ResponseEntity<Map<String, Object>> dashboardEnseignant(Authentication authentication) {
        String email = authentication.getName();
        Enseignant enseignant = enseignantRepository.findByEmail(email).orElse(null);

        long totalMatieres = 0;
        if (enseignant != null) {
            totalMatieres = matiereRepository.countByEnseignantId(enseignant.getId());
        } else {
            totalMatieres = matiereRepository.count();
        }
        long notesSaisies = 0;
        long absencesRenseignees = 0;
        long totalDocuments = 0;
        long documentsEnAttente = 0;

        if (enseignant != null) {
            notesSaisies = noteRepository.countByEnseignantId(enseignant.getId());
            absencesRenseignees = absenceRepository.countByEnseignantId(enseignant.getId());

            List<DemandeDocument> documents = demandeDocumentRepository.findByValidateursId(enseignant.getId());
            totalDocuments = documents.size();
            documentsEnAttente = documents.stream()
                    .filter(doc -> doc.getStatut() == DemandeDocument.Statut.EN_ATTENTE
                            || doc.getStatut() == DemandeDocument.Statut.EN_COURS_VALIDATION)
                    .count();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("role", "ENSEIGNANT");
        response.put("email", email);
        if (enseignant != null) {
            response.put("nom", enseignant.getNom());
            response.put("prenom", enseignant.getPrenom());
            if (enseignant.getUser() != null && enseignant.getUser().getPhoto() != null) {
                response.put("photo", enseignant.getUser().getPhoto());
            }
        }
        response.put("totalMatieres", totalMatieres);
        response.put("notesSaisies", notesSaisies);
        response.put("absencesRenseignees", absencesRenseignees);
        response.put("totalDocuments", totalDocuments);
        response.put("documentsEnAttente", documentsEnAttente);

        return ResponseEntity.ok(response);
    }

    // US1.13 — Dashboard Admin
    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> dashboardAdmin(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "role", "ADMIN",
                "email", authentication.getName(),
                "message", "Bienvenue sur votre dashboard admin"));
    }

    private String extractNiveauCode(String value) {
        if (value == null) return null;
        String upper = value.toUpperCase();
        if (upper.contains("LCS1")) return "LCS1";
        if (upper.contains("LCS2")) return "LCS2";
        if (upper.contains("LCS3")) return "LCS3";
        return null;
    }

    private boolean isAfterJanuaryCutoff(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();
        if (month == 1) {
            return day > 15;
        }
        return month >= 2 && month <= 5;
    }
}