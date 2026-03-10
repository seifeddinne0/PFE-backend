package com.academique.backend.controller;

import com.academique.backend.dto.request.EtudiantRequest;
import com.academique.backend.dto.response.EtudiantResponse;
import com.academique.backend.service.EtudiantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EtudiantController {

    private final EtudiantService etudiantService;

    // US2.1 — Créer étudiant (Admin)
    @PostMapping("/admin/etudiants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EtudiantResponse> create(
            @Valid @RequestBody EtudiantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(etudiantService.create(request));
    }

    // US2.2 — Lister tous (Admin + Enseignant)
    @GetMapping("/admin/etudiants")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Page<EtudiantResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "nom") String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        return ResponseEntity.ok(etudiantService.getAll(pageable));
    }

    // US2.3 — Voir un étudiant
    @GetMapping("/admin/etudiants/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<EtudiantResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(etudiantService.getById(id));
    }

    // US2.4 — Modifier étudiant (Admin)
    @PutMapping("/admin/etudiants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EtudiantResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody EtudiantRequest request) {
        return ResponseEntity.ok(etudiantService.update(id, request));
    }

    // US2.5 — Supprimer étudiant (Admin)
    @DeleteMapping("/admin/etudiants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        etudiantService.delete(id);
        return ResponseEntity.ok("Étudiant supprimé avec succès");
    }

    // US2.6 — Recherche
    @GetMapping("/admin/etudiants/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Page<EtudiantResponse>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(etudiantService.search(q, pageable));
    }

    // US2.10 — Profil étudiant connecté
    @GetMapping("/etudiant/profil")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<EtudiantResponse> monProfil(
            org.springframework.security.core.Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(etudiantService.getByEmail(email));
    }
}