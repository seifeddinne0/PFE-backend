package com.academique.backend.controller;

import com.academique.backend.dto.request.FactureRequest;
import com.academique.backend.dto.request.BatchFactureRequest;
import com.academique.backend.dto.response.FactureResponse;
import com.academique.backend.service.FactureService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FactureController {

    private final FactureService factureService;

    @PostMapping("/admin/factures")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FactureResponse> create(
            @Valid @RequestBody FactureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(factureService.create(request));
    }

        @PostMapping("/admin/factures/batch")
        @PreAuthorize("hasRole('ADMIN')")
        public ResponseEntity<Map<String, Object>> createBatch(
            @Valid @RequestBody BatchFactureRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(factureService.createBatch(request));
        }

    // ✅ Routes spécifiques AVANT /{id}
    @GetMapping("/admin/factures/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(factureService.getStats());
    }

    @GetMapping("/admin/factures/export/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportPdf() {
        byte[] pdf = factureService.exportPdf(null);
        return ResponseEntity.ok()
            .header("Content-Type", "application/pdf")
            .header("Content-Disposition", "attachment; filename=factures.pdf")
            .body(pdf);
    }

    @GetMapping("/admin/factures/export/pdf/{etudiantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> exportPdfByEtudiant(
            @PathVariable(name = "etudiantId") Long etudiantId) {
        byte[] pdf = factureService.exportPdf(etudiantId);
        return ResponseEntity.ok()
            .header("Content-Type", "application/pdf")
            .header("Content-Disposition", "attachment; filename=factures-etudiant.pdf")
            .body(pdf);
    }

    @GetMapping("/admin/factures/statut/{statut}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<FactureResponse>> getByStatut(
            @PathVariable(name = "statut") String statut,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(factureService.getByStatut(statut, pageable));
    }

    @GetMapping("/admin/factures/etudiant/{etudiantId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FactureResponse>> getByEtudiant(
            @PathVariable(name = "etudiantId") Long etudiantId) {
        return ResponseEntity.ok(factureService.getByEtudiant(etudiantId));
    }

    @GetMapping("/admin/factures")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<FactureResponse>> getAll(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(factureService.getAll(pageable));
    }

    @GetMapping("/admin/factures/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FactureResponse> getById(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(factureService.getById(id));
    }

    @PutMapping("/admin/factures/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FactureResponse> update(
            @PathVariable(name = "id") Long id,
            @Valid @RequestBody FactureRequest request) {
        return ResponseEntity.ok(factureService.update(id, request));
    }

    @PatchMapping("/admin/factures/{id}/payer")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FactureResponse> marquerPayee(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(factureService.marquerPayee(id));
    }

    @PatchMapping("/admin/factures/{id}/annuler")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FactureResponse> cancel(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(factureService.cancel(id));
    }

    @PatchMapping("/admin/factures/{id}/rejeter")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FactureResponse> reject(
            @PathVariable(name = "id") Long id) {
        return ResponseEntity.ok(factureService.reject(id));
    }

    @DeleteMapping("/admin/factures/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> delete(
            @PathVariable(name = "id") Long id) {
        factureService.delete(id);
        return ResponseEntity.ok("Facture supprimée avec succès");
    }

    @GetMapping("/etudiant/factures")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<List<FactureResponse>> mesFactures(
            org.springframework.security.core.Authentication authentication) {
        return ResponseEntity.ok(
            factureService.getByEtudiantEmail(authentication.getName()));
    }

    @PostMapping(value = "/etudiant/factures/{id}/confirmation-paiement", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<FactureResponse> confirmerPaiementEtudiant(
            @PathVariable(name = "id") Long id,
            @RequestParam(name = "datePaiement") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datePaiement,
            @RequestParam(name = "image") MultipartFile image,
            Authentication authentication) {
        return ResponseEntity.ok(
            factureService.confirmerPaiementEtudiant(id, authentication.getName(), datePaiement, image));
    }
}