package com.academique.backend.repository;

import com.academique.backend.entity.User;
import com.academique.backend.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByResetTokenAndResetTokenExpiryAfter(String resetToken, LocalDateTime dateTime);
    boolean existsByEmail(String email);
    List<User> findByRoles_Name(Role.RoleName roleName);
}