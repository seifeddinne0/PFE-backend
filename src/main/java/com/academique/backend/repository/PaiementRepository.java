package com.academique.backend.repository;

import com.academique.backend.entity.Paiement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaiementRepository extends JpaRepository<Paiement, Long> {
    List<Paiement> findByFactureId(Long factureId);
    List<Paiement> findByEtudiantIdOrderByCreatedAtDesc(Long etudiantId);
}
