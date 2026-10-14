package com.academique.backend.repository;

import com.academique.backend.entity.Niveau;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface NiveauRepository extends JpaRepository<Niveau, Long> {
    Optional<Niveau> findByCode(String code);
    boolean existsByCode(String code);
    List<Niveau> findByFiliereId(Long filiereId);
}
