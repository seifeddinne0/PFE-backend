package com.academique.backend.repository;

import com.academique.backend.entity.Note;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface NoteRepository extends JpaRepository<Note, Long> {

       @Query("SELECT AVG(n.valeur) FROM Note n")
Double calculerMoyenneGenerale();

@Query("SELECT AVG(n.valeur) FROM Note n WHERE n.semestre = :semestre")
Double calculerMoyenneParSemestre(@Param("semestre") Note.Semestre semestre);

long countByTypeNote(Note.TypeNote typeNote);
    long countByEnseignantId(Long enseignantId);
    List<Note> findByEtudiantId(Long etudiantId);
    Page<Note> findByEtudiantId(Long etudiantId, Pageable pageable);
    List<Note> findByMatiereId(Long matiereId);
    List<Note> findByEtudiantIdAndSemestre(Long etudiantId, Note.Semestre semestre);

    @Query("SELECT AVG(n.valeur * m.coefficient) / AVG(m.coefficient) " +
           "FROM Note n JOIN n.matiere m " +
           "WHERE n.etudiant.id = :etudiantId AND n.semestre = :semestre")
    Double calculerMoyenne(@Param("etudiantId") Long etudiantId,
                           @Param("semestre") Note.Semestre semestre);
}   