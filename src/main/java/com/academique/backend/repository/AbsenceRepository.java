package com.academique.backend.repository;

import com.academique.backend.entity.Absence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {
    long countByAlerte(Boolean alerte);
    long countByStatut(Absence.Statut statut);
    List<Absence> findByEtudiantId(Long etudiantId);
    Page<Absence> findByEtudiantId(Long etudiantId, Pageable pageable);
    Page<Absence> findByStatut(Absence.Statut statut, Pageable pageable);
    List<Absence> findByEtudiantIdAndStatut(Long etudiantId, Absence.Statut statut);
    long countByEtudiantId(Long etudiantId);
    long countByEtudiantIdAndStatut(Long etudiantId, Absence.Statut statut);
    long countByEnseignantId(Long enseignantId);

    @Query("SELECT a FROM Absence a WHERE a.etudiant.id = :etudiantId " +
           "ORDER BY a.dateAbsence DESC")
    List<Absence> findByEtudiantIdOrderByDate(@Param("etudiantId") Long etudiantId);

    List<Absence> findBySeanceIdAndDateAbsence(Long seanceId, java.time.LocalDate dateAbsence);
    List<Absence> findBySeanceId(Long seanceId);

    long countByEtudiantIdAndMatiereIdAndStatutNot(Long etudiantId, Long matiereId, Absence.Statut statut);
    
    @Query("SELECT a FROM Absence a WHERE a.etudiant.id = :etudiantId AND a.matiere.id = :matiereId")
    List<Absence> findByEtudiantIdAndMatiereId(@Param("etudiantId") Long etudiantId, @Param("matiereId") Long matiereId);
}