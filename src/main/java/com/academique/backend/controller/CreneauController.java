package com.academique.backend.controller;

import com.academique.backend.entity.Creneau;
import com.academique.backend.repository.CreneauRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class CreneauController {

    private final CreneauRepository creneauRepository;

    @GetMapping("/creneaux")
    @PreAuthorize("hasAnyRole('ADMIN','ENSEIGNANT')")
    public ResponseEntity<List<Creneau>> getAll() {
        return ResponseEntity.ok(creneauRepository.findAll());
    }
}
