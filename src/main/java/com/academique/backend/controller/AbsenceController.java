package com.academique.backend.controller;

import com.academique.backend.dto.request.AbsenceRequest;
import com.academique.backend.dto.response.AbsenceResponse;
import com.academique.backend.service.AbsenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AbsenceController {

    private final AbsenceService absenceService;

    // ✅ Enseignant enregistre une absence
    @PostMapping("/enseignant/absences")
    @PreAuthorize("hasAnyRole('ENSEIGNANT', 'ADMIN')")
    public ResponseEntity<AbsenceResponse> create(
            @Valid @RequestBody AbsenceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(absenceService.create(request));
    }

    // ✅ Admin liste toutes les absences
    @GetMapping("/admin/absences")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Page<AbsenceResponse>> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(absenceService.getAll(pageable));
    }

    // ✅ Routes spécifiques AVANT /{id}
    @GetMapping("/admin/absences/etudiant/{etudiantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<List<AbsenceResponse>> getByEtudiant(
            @PathVariable(name = "etudiantId") Long etudiantId) {
        return ResponseEntity.ok(absenceService.getByEtudiant(etudiantId));
    }

    @GetMapping("/admin/absences/statut/{statut}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Page<AbsenceResponse>> getByStatut(
            @PathVariable(name = "statut") String statut,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(absenceService.getByStatut(statut, pageable));
    }

    @GetMapping("/admin/absences/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<AbsenceResponse> getById(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(absenceService.getById(id));
    }

    @PutMapping("/admin/absences/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<AbsenceResponse> update(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody AbsenceRequest request) {
        return ResponseEntity.ok(absenceService.update(id, request));
    }

    // ✅ Justifier une absence
    @PatchMapping("/admin/absences/{id}/justifier")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AbsenceResponse> justifier(
            @PathVariable(name = "id") Long id,
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(
            absenceService.justifier(id, body.get("justification")));
    }

    @DeleteMapping("/admin/absences/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<String> delete(
            @PathVariable(name = "id") Long id) {
        absenceService.delete(id);
        return ResponseEntity.ok("Absence supprimée avec succès");
    }

    // ✅ Étudiant consulte ses propres absences
    @GetMapping("/etudiant/absences")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<List<AbsenceResponse>> mesAbsences(
            org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(
            absenceService.getByEtudiantEmail(authentication.getName()));
    }
}