package com.academique.backend.repository;

import com.academique.backend.entity.Matiere;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface MatiereRepository extends JpaRepository<Matiere, Long> {
    Optional<Matiere> findByNom(String nom);
    boolean existsByNom(String nom);
    java.util.List<Matiere> findBySemestre(Matiere.Semestre semestre);
}