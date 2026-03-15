package com.academique.backend.controller;

import com.academique.backend.dto.request.DemandeDocumentRequest;
import com.academique.backend.dto.response.DemandeDocumentResponse;
import com.academique.backend.service.DemandeDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DemandeDocumentController {

    private final DemandeDocumentService demandeService;

    // ✅ Étudiant crée une demande
    @PostMapping("/etudiant/documents")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<DemandeDocumentResponse> create(
            @Valid @RequestBody DemandeDocumentRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(demandeService.create(request, authentication.getName()));
    }

    // ✅ Étudiant consulte ses demandes
    @GetMapping("/etudiant/documents")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<List<DemandeDocumentResponse>> mesDemandes(
            Authentication authentication) {
        return ResponseEntity.ok(
            demandeService.getByEtudiantEmail(authentication.getName()));
    }

    // ✅ Enseignant consulte ses validations à faire
    @GetMapping("/enseignant/documents")
    @PreAuthorize("hasRole('ENSEIGNANT')")
    public ResponseEntity<List<DemandeDocumentResponse>> mesValidations(
            Authentication authentication) {
        return ResponseEntity.ok(
            demandeService.getByEnseignantEmail(authentication.getName()));
    }

    // ✅ Enseignant valide ou rejette
    @PatchMapping("/enseignant/documents/{id}/valider")
    @PreAuthorize("hasRole('ENSEIGNANT')")
    public ResponseEntity<DemandeDocumentResponse> validerParEnseignant(
            @PathVariable(name = "id") Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        boolean valide = "true".equals(body.get("valide"));
        String commentaire = body.get("commentaire");
        return ResponseEntity.ok(
            demandeService.validerParEnseignant(id, authentication.getName(), valide, commentaire));
    }

    // ✅ Routes spécifiques Admin AVANT /{id}
    @GetMapping("/admin/documents/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(demandeService.getStats());
    }

    @GetMapping("/admin/documents")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<DemandeDocumentResponse>> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(demandeService.getAll(pageable));
    }

    @GetMapping("/admin/documents/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DemandeDocumentResponse> getById(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(demandeService.getById(id));
    }

    // ✅ Admin valide ou rejette (documents sans enseignants)
    @PatchMapping("/admin/documents/{id}/valider")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DemandeDocumentResponse> validerParAdmin(
            @PathVariable(name = "id") Long id,
            @RequestBody Map<String, String> body) {
        boolean valide = "true".equals(body.get("valide"));
        String commentaire = body.get("commentaire");
        return ResponseEntity.ok(demandeService.validerParAdmin(id, valide, commentaire));
    }

    // ✅ Admin génère et télécharge le PDF
    @GetMapping("/admin/documents/{id}/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> genererPdf(
            @PathVariable(name = "id") Long id) {
        byte[] pdf = demandeService.genererPdf(id);
        DemandeDocumentResponse demande = demandeService.getById(id);
        String filename = demande.getTypeDocument().toLowerCase() + "_" +
                          demande.getEtudiantMatricule() + ".pdf";
        return ResponseEntity.ok()
            .header("Content-Type", "application/pdf")
            .header("Content-Disposition", "attachment; filename=" + filename)
            .body(pdf);
    }

    // ✅ Admin marque comme envoyée après téléchargement
    @PatchMapping("/admin/documents/{id}/envoyer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DemandeDocumentResponse> marquerEnvoyee(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(demandeService.marquerEnvoyee(id));
    }

    // ✅ Étudiant télécharge son document (si ENVOYEE)
    @GetMapping("/etudiant/documents/{id}/pdf")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<byte[]> telechargerPdf(
            @PathVariable(name = "id") Long id,
            Authentication authentication) {
        DemandeDocumentResponse demande = demandeService.getById(id);
        if (!demande.getEtudiantEmail().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (!"ENVOYEE".equals(demande.getStatut()) && !"VALIDEE".equals(demande.getStatut())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        byte[] pdf = demandeService.genererPdf(id);
        String filename = demande.getTypeDocument().toLowerCase() + ".pdf";
        return ResponseEntity.ok()
            .header("Content-Type", "application/pdf")
            .header("Content-Disposition", "attachment; filename=" + filename)
            .body(pdf);
    }

    @DeleteMapping("/admin/documents/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> delete(
            @PathVariable(name = "id") Long id) {
        demandeService.delete(id);
        return ResponseEntity.ok("Demande supprimée avec succès");
    }
}