package com.academique.backend.repository;

import com.academique.backend.entity.ConfigPaiement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ConfigPaiementRepository extends JpaRepository<ConfigPaiement, Long> {
    Optional<ConfigPaiement> findByActifTrue();
}
