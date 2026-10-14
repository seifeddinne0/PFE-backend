package com.academique.backend.repository;

import com.academique.backend.entity.Etudiant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EtudiantRepository extends JpaRepository<Etudiant, Long> {
    long countByStatut(Etudiant.Statut statut);
    Optional<Etudiant> findByEmail(String email);

    Optional<Etudiant> findByMatricule(String matricule);

    boolean existsByEmail(String email);

    boolean existsByMatricule(String matricule);

    List<Etudiant> findByClasseId(Long classeId);
    List<Etudiant> findByClasseIdIn(List<Long> classeIds);

    // Remplace l'ancienne méthode par celle-ci :
Page<Etudiant> findByClasse(String classe, Pageable pageable);

// Et ajoute une version qui combine recherche texte + classe si besoin
@Query("SELECT e FROM Etudiant e WHERE e.classe = :classe AND (" +
       "LOWER(e.nom) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
       "LOWER(e.prenom) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
       "LOWER(e.matricule) LIKE LOWER(CONCAT('%', :query, '%')))")
Page<Etudiant> searchInClasse(@Param("query") String query, @Param("classe") String classe, Pageable pageable);

    @Query("SELECT e FROM Etudiant e JOIN e.classe c WHERE UPPER(c.code) LIKE CONCAT(UPPER(:niveauCode), '%')")
    List<Etudiant> findByNiveauCode(@Param("niveauCode") String niveauCode);

    // Filtrer par statut
    Page<Etudiant> findByStatut(Etudiant.Statut statut, Pageable pageable);
}