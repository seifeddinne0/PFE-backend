package com.academique.backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    // US1.11 — Dashboard Étudiant
    @GetMapping("/etudiant/dashboard")
    @PreAuthorize("hasRole('ETUDIANT')")
    public ResponseEntity<Map<String, Object>> dashboardEtudiant(
            Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "role", "ETUDIANT",
            "email", authentication.getName(),
            "message", "Bienvenue sur votre dashboard étudiant"
        ));
    }

    // US1.12 — Dashboard Enseignant
    @GetMapping("/enseignant/dashboard")
    @PreAuthorize("hasRole('ENSEIGNANT')")
    public ResponseEntity<Map<String, Object>> dashboardEnseignant(
            Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "role", "ENSEIGNANT",
            "email", authentication.getName(),
            "message", "Bienvenue sur votre dashboard enseignant"
        ));
    }

    // US1.13 — Dashboard Admin
    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> dashboardAdmin(
            Authentication authentication) {
        return ResponseEntity.ok(Map.of(
            "role", "ADMIN",
            "email", authentication.getName(),
            "message", "Bienvenue sur votre dashboard admin"
        ));
    }
}