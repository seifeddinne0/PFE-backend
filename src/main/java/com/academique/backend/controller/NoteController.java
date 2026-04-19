package com.academique.backend.controller;

import com.academique.backend.dto.request.NoteRequest;
import com.academique.backend.dto.response.NoteResponse;
import com.academique.backend.service.NoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @PostMapping("/enseignant/notes")
    @PreAuthorize("hasAnyRole('ENSEIGNANT')")
    public ResponseEntity<NoteResponse> create(
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(noteService.create(request));
    }

    @GetMapping("/admin/notes")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Page<NoteResponse>> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(noteService.getAll(pageable));
    }

    // ✅ Routes spécifiques AVANT /{id}
    @GetMapping("/admin/notes/etudiant/{etudiantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<List<NoteResponse>> getByEtudiant(
            @PathVariable(name = "etudiantId") Long etudiantId) {
        return ResponseEntity.ok(noteService.getByEtudiant(etudiantId));
    }

    @GetMapping("/admin/notes/etudiant/{etudiantId}/semestre/{semestre}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<List<NoteResponse>> getByEtudiantAndSemestre(
            @PathVariable(name = "etudiantId") Long etudiantId,
            @PathVariable(name = "semestre") String semestre) {
        return ResponseEntity.ok(noteService.getByEtudiantAndSemestre(etudiantId, semestre));
    }

    @GetMapping("/admin/notes/etudiant/{etudiantId}/moyenne/{semestre}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<Double> getMoyenne(
            @PathVariable(name = "etudiantId") Long etudiantId,
            @PathVariable(name = "semestre") String semestre) {
        return ResponseEntity.ok(noteService.calculerMoyenne(etudiantId, semestre));
    }

    @GetMapping("/admin/notes/bulletin/{etudiantId}/{semestre}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<byte[]> getBulletin(
            @PathVariable(name = "etudiantId") Long etudiantId,
            @PathVariable(name = "semestre") String semestre) {
        byte[] pdf = noteService.exportBulletinPdf(etudiantId, semestre);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                    "attachment; filename=bulletin_" + etudiantId + "_" + semestre + ".pdf")
                .body(pdf);
    }

    @GetMapping("/admin/notes/bulletin/complet/{etudiantId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<byte[]> getBulletinComplet(
            @PathVariable(name = "etudiantId") Long etudiantId) {
        byte[] pdf = noteService.exportBulletinPdfComplet(etudiantId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition",
                    "attachment; filename=bulletin_complet_" + etudiantId + ".pdf")
                .body(pdf);
    }

    @PostMapping("/admin/notes/bulletins/envoyer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> envoyerBulletinsParNiveau(
            @RequestParam(name = "niveauCode") String niveauCode) {
        return ResponseEntity.ok(noteService.envoyerBulletinsParNiveau(niveauCode));
    }

    @GetMapping("/admin/notes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<NoteResponse> getById(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(noteService.getById(id));
    }

    @PutMapping("/admin/notes/{id}")
    @PreAuthorize("hasAnyRole('ENSEIGNANT')")
    public ResponseEntity<NoteResponse> update(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.ok(noteService.update(id, request));
    }

    @DeleteMapping("/admin/notes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<String> delete(
            @PathVariable(name = "id") Long id) {
        noteService.delete(id);
        return ResponseEntity.ok("Note supprimée avec succès");
    }

    // Étudiant consulte ses propres notes
    @GetMapping("/etudiant/notes")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<List<NoteResponse>> mesNotes(
            org.springframework.security.core.Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(noteService.getByEtudiantEmail(email));
    }

    @GetMapping("/etudiant/notes/bulletin")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<byte[]> monBulletinComplet(
            org.springframework.security.core.Authentication authentication) {
        String email = authentication.getName();
        byte[] pdf = noteService.exportBulletinPdfCompletByEmail(email);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "attachment; filename=mon_bulletin_complet.pdf")
                .body(pdf);
    }
}