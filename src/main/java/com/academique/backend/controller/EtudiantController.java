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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EtudiantController {

    private final EtudiantService etudiantService;

    @PostMapping("/admin/etudiants")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EtudiantResponse> create(
            @Valid @RequestBody EtudiantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(etudiantService.create(request));
    }

    @GetMapping("/admin/etudiants")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Page<EtudiantResponse>> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            @RequestParam(name = "sortBy", defaultValue = "nom") String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy));
        return ResponseEntity.ok(etudiantService.getAll(pageable));
    }

    @GetMapping("/admin/etudiants/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Page<EtudiantResponse>> search(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(etudiantService.search(q, pageable));
    }

    @GetMapping("/admin/etudiants/export/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportPdf() {
        byte[] pdf = etudiantService.exportPdf();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=etudiants.pdf")
                .body(pdf);
    }

    @GetMapping("/admin/etudiants/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<EtudiantResponse> getById(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(etudiantService.getById(id));
    }

    @PutMapping("/admin/etudiants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EtudiantResponse> update(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody EtudiantRequest request) {
        return ResponseEntity.ok(etudiantService.update(id, request));
    }

    @DeleteMapping("/admin/etudiants/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> delete(
            @PathVariable(name = "id") Long id) {
        etudiantService.delete(id);
        return ResponseEntity.ok("Étudiant supprimé avec succès");
    }

    @GetMapping("/etudiant/profil")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<EtudiantResponse> monProfil(
            org.springframework.security.core.Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(etudiantService.getByEmail(email));
    }
}