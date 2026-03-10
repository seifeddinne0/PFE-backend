package com.academique.backend.repository;

import com.academique.backend.entity.Etudiant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EtudiantRepository extends JpaRepository<Etudiant, Long> {

    Optional<Etudiant> findByEmail(String email);

    Optional<Etudiant> findByMatricule(String matricule);

    boolean existsByEmail(String email);

    boolean existsByMatricule(String matricule);

    // Recherche par nom, prenom ou matricule
    @Query("SELECT e FROM Etudiant e WHERE " +
           "LOWER(e.nom) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.prenom) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.matricule) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Etudiant> search(@Param("query") String query, Pageable pageable);

    // Filtrer par statut
    Page<Etudiant> findByStatut(Etudiant.Statut statut, Pageable pageable);
}