package com.academique.backend.controller;

import com.academique.backend.dto.request.EnseignantRequest;
import com.academique.backend.dto.response.EnseignantResponse;
import com.academique.backend.service.EnseignantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EnseignantController {

    private final EnseignantService enseignantService;

    @PostMapping("/admin/enseignants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnseignantResponse> create(
            @Valid @RequestBody EnseignantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(enseignantService.create(request));
    }

    @GetMapping("/admin/enseignants")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Page<EnseignantResponse>> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sortBy", defaultValue = "nom") String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        return ResponseEntity.ok(enseignantService.getAll(pageable));
    }

    // ✅ search et export AVANT /{id}
    @GetMapping("/admin/enseignants/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Page<EnseignantResponse>> search(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(enseignantService.search(q, pageable));
    }

    @GetMapping("/admin/enseignants/export/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportPdf() {
        byte[] pdf = enseignantService.exportPdf();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=enseignants.pdf")
                .body(pdf);
    }

    @GetMapping("/admin/enseignants/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<EnseignantResponse> getById(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(enseignantService.getById(id));
    }

    @PutMapping("/admin/enseignants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EnseignantResponse> update(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody EnseignantRequest request) {
        return ResponseEntity.ok(enseignantService.update(id, request));
    }

    @DeleteMapping("/admin/enseignants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> delete(
            @PathVariable(name = "id") Long id) {
        enseignantService.delete(id);
        return ResponseEntity.ok("Enseignant supprimé avec succès");
    }

    @GetMapping("/enseignant/profil")
    @PreAuthorize("hasRole('ENSEIGNANT')")
    public ResponseEntity<EnseignantResponse> monProfil(
            org.springframework.security.core.Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(enseignantService.getByEmail(email));
    }
}