package com.academique.backend.repository;

import com.academique.backend.entity.DemandeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DemandeDocumentRepository extends JpaRepository<DemandeDocument, Long> {
    long countByTypeDocument(DemandeDocument.TypeDocument type);
    List<DemandeDocument> findByEtudiantId(Long etudiantId);
    Page<DemandeDocument> findByStatut(DemandeDocument.Statut statut, Pageable pageable);
    Page<DemandeDocument> findByTypeDocument(DemandeDocument.TypeDocument type, Pageable pageable);
    List<DemandeDocument> findByValidateursId(Long enseignantId);
    long countByStatut(DemandeDocument.Statut statut);
}