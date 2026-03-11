package com.academique.backend.controller;

import com.academique.backend.dto.request.MatiereRequest;
import com.academique.backend.dto.response.MatiereResponse;
import com.academique.backend.service.MatiereService;
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class MatiereController {

    private final MatiereService matiereService;

    @PostMapping("/admin/matieres")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MatiereResponse> create(
            @Valid @RequestBody MatiereRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(matiereService.create(request));
    }

    @GetMapping("/admin/matieres")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<List<MatiereResponse>> getAll() {
        return ResponseEntity.ok(matiereService.getAll());
    }

    @GetMapping("/admin/matieres/paged")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Page<MatiereResponse>> getAllPaged(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(matiereService.getAllPaged(pageable));
    }

    @GetMapping("/admin/matieres/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<MatiereResponse> getById(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(matiereService.getById(id));
    }

    @PutMapping("/admin/matieres/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MatiereResponse> update(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody MatiereRequest request) {
        return ResponseEntity.ok(matiereService.update(id, request));
    }

    @DeleteMapping("/admin/matieres/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> delete(
            @PathVariable(name = "id") Long id) {
        matiereService.delete(id);
        return ResponseEntity.ok("Matière supprimée avec succès");
    }
}