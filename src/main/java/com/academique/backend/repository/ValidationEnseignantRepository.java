package com.academique.backend.repository;

import com.academique.backend.entity.ValidationEnseignant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ValidationEnseignantRepository extends JpaRepository<ValidationEnseignant, Long> {
    List<ValidationEnseignant> findByDemandeId(Long demandeId);
    Optional<ValidationEnseignant> findByDemandeIdAndEnseignantId(Long demandeId, Long enseignantId);
}