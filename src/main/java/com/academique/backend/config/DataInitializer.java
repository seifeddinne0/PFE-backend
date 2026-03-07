package com.academique.backend.config;

import com.academique.backend.entity.Role;
import com.academique.backend.entity.User;
import com.academique.backend.repository.RoleRepository;
import com.academique.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner initData() {
        return args -> {

            // Crée les rôles si ils n'existent pas
            if (roleRepository.count() == 0) {
                Role adminRole = new Role();
                adminRole.setName(Role.RoleName.ROLE_ADMIN);

                Role enseignantRole = new Role();
                enseignantRole.setName(Role.RoleName.ROLE_ENSEIGNANT);

                Role etudiantRole = new Role();
                etudiantRole.setName(Role.RoleName.ROLE_ETUDIANT);

                roleRepository.saveAll(Set.of(adminRole, enseignantRole, etudiantRole));
                System.out.println("✅ Roles créés !");
            }

            // Crée les users de test si ils n'existent pas
            if (userRepository.count() == 0) {
                Role adminRole = roleRepository
                    .findByName(Role.RoleName.ROLE_ADMIN).orElseThrow();
                Role enseignantRole = roleRepository
                    .findByName(Role.RoleName.ROLE_ENSEIGNANT).orElseThrow();
                Role etudiantRole = roleRepository
                    .findByName(Role.RoleName.ROLE_ETUDIANT).orElseThrow();

                User admin = new User();
                admin.setEmail("admin@academique.com");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setNom("Admin");
                admin.setPrenom("System");
                admin.setRoles(Set.of(adminRole));

                User enseignant = new User();
                enseignant.setEmail("enseignant@academique.com");
                enseignant.setPassword(passwordEncoder.encode("enseignant123"));
                enseignant.setNom("Dupont");
                enseignant.setPrenom("Jean");
                enseignant.setRoles(Set.of(enseignantRole));

                User etudiant = new User();
                etudiant.setEmail("etudiant@academique.com");
                etudiant.setPassword(passwordEncoder.encode("etudiant123"));
                etudiant.setNom("Martin");
                etudiant.setPrenom("Alice");
                etudiant.setRoles(Set.of(etudiantRole));

                userRepository.saveAll(Set.of(admin, enseignant, etudiant));
                System.out.println("✅ Users de test créés !");
            }
        };
    }
}