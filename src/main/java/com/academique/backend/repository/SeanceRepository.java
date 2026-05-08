package com.academique.backend.repository;

import com.academique.backend.entity.Matiere;
import com.academique.backend.entity.Seance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SeanceRepository extends JpaRepository<Seance, Long> {
    List<Seance> findByEnseignantId(Long enseignantId);

    List<Seance> findByClasseId(Long classeId);

    List<Seance> findByMatiereId(Long matiereId);

    List<Seance> findByEnseignantIdAndJourSemaine(Long enseignantId, Seance.JourSemaine jour);

    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Seance s SET s.enseignant = :enseignant WHERE s.matiere.id = :matiereId AND s.semestre = :semestre")
    int assignEnseignant(
            @Param("matiereId") Long matiereId,
            @Param("semestre") Matiere.Semestre semestre,
            @Param("enseignant") com.academique.backend.entity.Enseignant enseignant);

    @Query("""
            SELECT s
            FROM Seance s
            JOIN FETCH s.matiere m
            LEFT JOIN FETCH s.classe c
            JOIN FETCH s.niveau n
            WHERE (c.id = :classeId OR (s.typeSeance = com.academique.backend.entity.Seance.TypeSeance.COURS AND n.id = :niveauId))
            ORDER BY s.jourSemaine, s.heureDebut
            """)
    List<Seance> findForClasseAndNiveau(
            @Param("classeId") Long classeId,
            @Param("niveauId") Long niveauId);

    @Query("""
            SELECT s
            FROM Seance s
            JOIN FETCH s.matiere m
            LEFT JOIN FETCH s.classe c
            JOIN FETCH s.niveau n
            JOIN FETCH n.filiere f
            WHERE s.enseignant.id = :enseignantId
              AND m.semestre = :semestre
              AND (:filiereCode IS NULL OR UPPER(f.code) = UPPER(:filiereCode))
              AND (:niveauCode IS NULL OR UPPER(n.code) = UPPER(:niveauCode))
            ORDER BY s.jourSemaine, s.heureDebut
            """)
    List<Seance> findAgendaByEnseignantAndSemestre(
            @Param("enseignantId") Long enseignantId,
            @Param("semestre") Matiere.Semestre semestre,
            @Param("filiereCode") String filiereCode,
            @Param("niveauCode") String niveauCode);
}
