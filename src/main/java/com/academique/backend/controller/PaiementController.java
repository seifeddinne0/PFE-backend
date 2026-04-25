package com.academique.backend.controller;

import com.academique.backend.dto.response.PaiementResponse;
import com.academique.backend.service.PaiementService;
import com.academique.backend.service.ReceiptAnalysisException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PaiementController {

    private final PaiementService paiementService;

    @PostMapping(value = "/paiements/soumettre", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<?> soumettrePaiement(
        @RequestParam("factureId") Long factureId,
        @RequestParam("etudiantId") Long etudiantId,
        @RequestParam("receipt") MultipartFile receipt,
        Authentication authentication
    ) {
        try {
            PaiementResponse response = paiementService.soumettrePaiement(factureId, etudiantId, receipt, authentication.getName());
            return ResponseEntity.ok(response);
        } catch (ReceiptAnalysisException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Erreur lors du traitement du paiement"));
        }
    }

    @GetMapping("/paiements/facture/{factureId}")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<?> getByFacture(@PathVariable Long factureId, Authentication authentication) {
        try {
            List<PaiementResponse> responses = paiementService.getByFacture(factureId, authentication.getName());
            return ResponseEntity.ok(responses);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Erreur lors du chargement"));
        }
    }

    @GetMapping("/paiements/etudiant/{etudiantId}")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<?> getByEtudiant(@PathVariable Long etudiantId, Authentication authentication) {
        try {
            List<PaiementResponse> responses = paiementService.getByEtudiant(etudiantId, authentication.getName());
            return ResponseEntity.ok(responses);
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Erreur lors du chargement"));
        }
    }

    @GetMapping("/admin/paiements")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAll() {
        try {
            return ResponseEntity.ok(paiementService.getAll());
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Erreur lors du chargement"));
        }
    }
}
