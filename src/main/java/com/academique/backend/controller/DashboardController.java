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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final EtudiantRepository etudiantRepository;
    private final EnseignantRepository enseignantRepository;
    private final AbsenceRepository absenceRepository;
    private final NoteRepository noteRepository;
    private final MatiereRepository matiereRepository;

    // US1.11 — Dashboard Étudiant
    @GetMapping("/etudiant/dashboard")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<Map<String, Object>> dashboardEtudiant(Authentication authentication) {
        String email = authentication.getName();
        Etudiant etudiant = etudiantRepository.findByEmail(email).orElse(null);
        
        long totalMatieres = 0;
        if (etudiant != null) {
            java.util.List<Matiere.Semestre> targetSemestres = new java.util.ArrayList<>();
            String refCode = (etudiant.getClasse() != null) ? etudiant.getClasse().getCode() : etudiant.getMatricule();
            
            if (refCode != null) {
                if (refCode.contains("LCS1")) {
                    targetSemestres.add(Matiere.Semestre.S1);
                    targetSemestres.add(Matiere.Semestre.S2);
                } else if (refCode.contains("LCS2")) {
                    targetSemestres.add(Matiere.Semestre.S3);
                    targetSemestres.add(Matiere.Semestre.S4);
                } else if (refCode.contains("LCS3")) {
                    targetSemestres.add(Matiere.Semestre.S5);
                }
            }

            if (!targetSemestres.isEmpty()) {
                totalMatieres = matiereRepository.countBySemestreIn(targetSemestres);
            } else {
                totalMatieres = matiereRepository.count();
            }
        } else {
            totalMatieres = matiereRepository.count();
        }
        long totalAbsences = 0;
        long totalEvaluations = 0;
        
        if (etudiant != null) {
            totalAbsences = absenceRepository.countByEtudiantId(etudiant.getId());
            totalEvaluations = noteRepository.findByEtudiantId(etudiant.getId()).size();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("role", "ETUDIANT");
        response.put("email", email);
        response.put("totalMatieres", totalMatieres);
        response.put("totalAbsences", totalAbsences);
        response.put("totalEvaluations", totalEvaluations);
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
        
        if (enseignant != null) {
            notesSaisies = noteRepository.countByEnseignantId(enseignant.getId());
            absencesRenseignees = absenceRepository.countByEnseignantId(enseignant.getId());
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
        
        return ResponseEntity.ok(response);
    }

    // US1.13 — Dashboard Admin
    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> dashboardAdmin(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "role", "ADMIN",
            "email", authentication.getName(),
            "message", "Bienvenue sur votre dashboard admin"
        ));
    }
}