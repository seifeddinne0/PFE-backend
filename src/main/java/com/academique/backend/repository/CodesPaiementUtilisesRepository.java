package com.academique.backend.repository;

import com.academique.backend.entity.CodesPaiementUtilises;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodesPaiementUtilisesRepository extends JpaRepository<CodesPaiementUtilises, Long> {
    boolean existsByCodePaiement(String codePaiement);
}
