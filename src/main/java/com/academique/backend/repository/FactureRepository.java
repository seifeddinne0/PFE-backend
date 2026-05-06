package com.academique.backend.repository;

import com.academique.backend.entity.Facture;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface FactureRepository extends JpaRepository<Facture, Long> {
    Optional<Facture> findByNumero(String numero);
    List<Facture> findByEtudiantId(Long etudiantId);
    Page<Facture> findByStatut(Facture.Statut statut, Pageable pageable);
    List<Facture> findByEtudiantIdAndStatut(Long etudiantId, Facture.Statut statut);
    long countByStatut(Facture.Statut statut);

    @Query("SELECT SUM(f.montant) FROM Facture f WHERE f.statut = 'PAYEE'")
    Double totalPaye();

    @Query("SELECT SUM(f.montant) FROM Facture f WHERE f.statut = 'NON_PAYEE'")
    Double totalImpaye();

    @Query("""
        SELECT EXTRACT(YEAR FROM f.datePaiement), EXTRACT(MONTH FROM f.datePaiement), SUM(f.montant)
        FROM Facture f
        WHERE f.statut = 'PAYEE' AND f.datePaiement IS NOT NULL
        GROUP BY EXTRACT(YEAR FROM f.datePaiement), EXTRACT(MONTH FROM f.datePaiement)
        ORDER BY EXTRACT(YEAR FROM f.datePaiement), EXTRACT(MONTH FROM f.datePaiement)
        """)
    List<Object[]> totalPayeParMois();
}