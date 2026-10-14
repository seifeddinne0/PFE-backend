package com.academique.backend.controller;

import com.academique.backend.entity.Niveau;
import com.academique.backend.repository.NiveauRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NiveauController {

    private final NiveauRepository niveauRepository;

    @GetMapping("/admin/niveaux")
    @PreAuthorize("hasAnyRole('ADMIN', 'ENSEIGNANT')")
    public ResponseEntity<List<Map<String, Object>>> getAll() {
        List<Niveau> niveaux = niveauRepository.findAll();
        List<Map<String, Object>> result = niveaux.stream().map(n -> Map.<String, Object>of(
            "id", n.getId(),
            "code", n.getCode(),
            "nom", n.getNom(),
            "filiereId", n.getFiliere().getId(),
            "filiereCode", n.getFiliere().getCode(),
            "filiereNom", n.getFiliere().getNom()
        )).collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }
}
