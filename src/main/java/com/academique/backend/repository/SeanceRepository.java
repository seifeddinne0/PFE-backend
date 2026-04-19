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

    @Query("""
        SELECT s
        FROM Seance s
        JOIN FETCH s.matiere m
        JOIN FETCH s.classe c
        JOIN FETCH c.niveau n
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
        @Param("niveauCode") String niveauCode
    );
}
