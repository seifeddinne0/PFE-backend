package com.academique.backend.repository;

import com.academique.backend.entity.Enseignant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface EnseignantRepository extends JpaRepository<Enseignant, Long> {
    Optional<Enseignant> findByEmail(String email);
    Optional<Enseignant> findByMatricule(String matricule);
    boolean existsByEmail(String email);
    boolean existsByMatricule(String matricule);

    @Query("SELECT e FROM Enseignant e WHERE " +
           "LOWER(e.nom) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.prenom) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.matricule) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<Enseignant> search(@Param("query") String query, Pageable pageable);

    Page<Enseignant> findByStatut(Enseignant.Statut statut, Pageable pageable);
}