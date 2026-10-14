package com.academique.backend.repository;

import com.academique.backend.entity.DocumentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DocumentConfigRepository extends JpaRepository<DocumentConfig, Long> {
    Optional<DocumentConfig> findByTypeDocument(String typeDocument);
}
