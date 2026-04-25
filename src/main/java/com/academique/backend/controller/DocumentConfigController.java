package com.academique.backend.controller;

import com.academique.backend.entity.DocumentConfig;
import com.academique.backend.repository.DocumentConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/document-configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DocumentConfigController {

    private final DocumentConfigRepository repository;

    @GetMapping
    public ResponseEntity<List<DocumentConfig>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<DocumentConfig> toggle(@PathVariable Long id) {
        DocumentConfig config = repository.findById(id).orElseThrow();
        config.setEnabled(!config.isEnabled());
        return ResponseEntity.ok(repository.save(config));
    }
}
