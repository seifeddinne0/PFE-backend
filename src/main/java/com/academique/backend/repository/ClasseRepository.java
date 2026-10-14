package com.academique.backend.repository;

import com.academique.backend.entity.Classe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClasseRepository extends JpaRepository<Classe, Long> {
    Optional<Classe> findByCode(String code);
    boolean existsByCode(String code);
    List<Classe> findByNiveauId(Long niveauId);
    List<Classe> findByNiveauFiliereId(Long filiereId);
}
