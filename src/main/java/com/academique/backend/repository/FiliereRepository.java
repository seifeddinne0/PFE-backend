package com.academique.backend.repository;

import com.academique.backend.entity.Filiere;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface FiliereRepository extends JpaRepository<Filiere, Long> {
    Optional<Filiere> findByCode(String code);
    boolean existsByCode(String code);
}
