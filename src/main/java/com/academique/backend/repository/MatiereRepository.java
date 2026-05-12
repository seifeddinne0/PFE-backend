package com.academique.backend.repository;

import com.academique.backend.entity.Matiere;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface MatiereRepository extends JpaRepository<Matiere, Long> {
    Optional<Matiere> findByNom(String nom);
    boolean existsByNom(String nom);
    java.util.List<Matiere> findBySemestre(Matiere.Semestre semestre);
    long countByCodeStartingWith(String prefix);
    long countByEnseignantId(Long enseignantId);
    long countBySemestreIn(java.util.List<Matiere.Semestre> semestres);
    java.util.List<Matiere> findByEnseignant(com.academique.backend.entity.Enseignant enseignant);

        @Query("""
            SELECT COUNT(DISTINCT m.id)
            FROM Matiere m
            JOIN m.niveau n
            JOIN Classe c ON c.niveau = n
            JOIN Etudiant e ON e.classe = c
            WHERE e.id = :etudiantId
              AND m.semestre = :semestre
            """)
        long countByEtudiantAndSemestre(
            @Param("etudiantId") Long etudiantId,
            @Param("semestre") Matiere.Semestre semestre);
}