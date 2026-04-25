package com.academique.backend.controller;

import com.academique.backend.dto.request.SeanceRequest;
import com.academique.backend.dto.response.AgendaAbsenceResponse;
import com.academique.backend.dto.response.EtudiantResponse;
import com.academique.backend.dto.response.SeanceResponse;
import com.academique.backend.entity.Classe;
import com.academique.backend.entity.Etudiant;
import com.academique.backend.entity.Filiere;
import com.academique.backend.entity.Seance;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.ClasseRepository;
import com.academique.backend.repository.EtudiantRepository;
import com.academique.backend.repository.FiliereRepository;
import com.academique.backend.repository.SeanceRepository;
import com.academique.backend.service.SeanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SeanceController {

    private final SeanceService seanceService;
    private final SeanceRepository seanceRepository;
    private final EtudiantRepository etudiantRepository;
    private final ClasseRepository classeRepository;
    private final FiliereRepository filiereRepository;

    // ─── Admin CRUD ─────────────────────────────────────────

    @PostMapping("/admin/seances")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SeanceResponse> create(@Valid @RequestBody SeanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(seanceService.create(request));
    }

    @GetMapping("/admin/seances")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<List<SeanceResponse>> getAll() {
        return ResponseEntity.ok(seanceService.getAll());
    }

    @GetMapping("/admin/seances/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<SeanceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(seanceService.getById(id));
    }

    @DeleteMapping("/admin/seances/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        seanceService.delete(id);
        return ResponseEntity.ok("Séance supprimée avec succès");
    }

    // ─── Enseignant: get my séances ─────────────────────────

    @GetMapping("/enseignant/seances")
    @PreAuthorize("hasRole('ENSEIGNANT')")
    public ResponseEntity<List<SeanceResponse>> mesSeances(Authentication authentication) {
        return ResponseEntity.ok(seanceService.getByEnseignantEmail(authentication.getName()));
    }

    // ─── Étudiant: get only my teachers ───────────────────────

    @GetMapping("/etudiant/enseignants")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<List<Map<String, Object>>> mesEnseignants(Authentication authentication) {
        Etudiant etudiant = etudiantRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        if (etudiant.getClasse() == null) {
            return ResponseEntity.ok(List.of());
        }

        List<Seance> seances = seanceRepository.findByClasseId(etudiant.getClasse().getId());
        Map<Long, Map<String, Object>> uniqueTeachers = new LinkedHashMap<>();

        for (Seance seance : seances) {
            if (seance.getEnseignant() == null) {
                continue;
            }

            Long enseignantId = seance.getEnseignant().getId();
            uniqueTeachers.putIfAbsent(enseignantId, Map.of(
                "id", enseignantId,
                "nom", seance.getEnseignant().getNom(),
                "prenom", seance.getEnseignant().getPrenom()
            ));
        }

        return ResponseEntity.ok(new ArrayList<>(uniqueTeachers.values()));
    }

    @GetMapping("/etudiant/seances")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<List<SeanceResponse>> mesSeancesEtudiant(Authentication authentication) {
        Etudiant etudiant = etudiantRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        if (etudiant.getClasse() == null) return ResponseEntity.ok(List.of());
        return ResponseEntity.ok(seanceService.getByClasseId(etudiant.getClasse().getId()));
    }

    @GetMapping("/enseignant/agenda-absences")
    @PreAuthorize("hasRole('ENSEIGNANT')")
    public ResponseEntity<AgendaAbsenceResponse> agendaAbsences(
        Authentication authentication,
        @RequestParam(required = false) String filiereCode,
        @RequestParam(required = false) String niveauCode,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate
    ) {
        return ResponseEntity.ok(
            seanceService.getAgendaAbsencesForTeacher(
                authentication.getName(),
                filiereCode,
                niveauCode,
                referenceDate
            )
        );
    }

    // ─── Get students for a séance (by classe) ──────────────

    @GetMapping("/enseignant/seances/{seanceId}/etudiants")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'ADMIN')")
    public ResponseEntity<List<EtudiantResponse>> getStudentsBySeance(@PathVariable Long seanceId) {
        Seance seance = seanceRepository.findById(seanceId)
            .orElseThrow(() -> new ResourceNotFoundException("Séance non trouvée"));

        List<Etudiant> etudiants = etudiantRepository.findByClasseId(seance.getClasse().getId());

        List<EtudiantResponse> responses = etudiants.stream().map(e ->
            EtudiantResponse.builder()
                .id(e.getId())
                .matricule(e.getMatricule())
                .nom(e.getNom())
                .prenom(e.getPrenom())
                .email(e.getEmail())
                .classeId(e.getClasse() != null ? e.getClasse().getId() : null)
                .classeCode(e.getClasse() != null ? e.getClasse().getCode() : null)
                .build()
        ).toList();

        return ResponseEntity.ok(responses);
    }

    // ─── Get students by selected class ────────────────────

    @GetMapping("/enseignant/classes/{classeId}/etudiants")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'ADMIN')")
    public ResponseEntity<List<EtudiantResponse>> getStudentsByClasse(@PathVariable Long classeId) {
        List<Etudiant> etudiants = etudiantRepository.findByClasseId(classeId);

        List<EtudiantResponse> responses = etudiants.stream().map(e ->
            EtudiantResponse.builder()
                .id(e.getId())
                .matricule(e.getMatricule())
                .nom(e.getNom())
                .prenom(e.getPrenom())
                .email(e.getEmail())
                .classeId(e.getClasse() != null ? e.getClasse().getId() : null)
                .classeCode(e.getClasse() != null ? e.getClasse().getCode() : null)
                .build()
        ).toList();

        return ResponseEntity.ok(responses);
    }

    // ─── Admin: get all classes ──────────────────────────────

    @GetMapping("/admin/classes")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<List<Map<String, Object>>> getAllClasses() {
        List<Classe> classes = classeRepository.findAll();
        List<Map<String, Object>> result = classes.stream().map(c -> Map.<String, Object>of(
            "id", c.getId(),
            "code", c.getCode(),
            "nom", c.getNom(),
            "niveauCode", c.getNiveau().getCode(),
            "filiereCode", c.getNiveau().getFiliere().getCode()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    // ─── Admin: get all filières ─────────────────────────────

    @GetMapping("/admin/filieres")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<List<Map<String, Object>>> getAllFilieres() {
        List<Filiere> filieres = filiereRepository.findAll();
        List<Map<String, Object>> result = filieres.stream().map(f -> Map.<String, Object>of(
            "id", f.getId(),
            "code", f.getCode(),
            "nom", f.getNom()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
