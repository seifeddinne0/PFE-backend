package com.academique.backend.config;

import com.academique.backend.entity.Classe;
import com.academique.backend.entity.Etudiant;
import com.academique.backend.entity.Filiere;
import com.academique.backend.entity.Matiere;
import com.academique.backend.entity.Niveau;
import com.academique.backend.entity.Role;
import com.academique.backend.entity.Seance;
import com.academique.backend.entity.User;
import com.academique.backend.repository.ClasseRepository;
import com.academique.backend.repository.DocumentConfigRepository;
import com.academique.backend.repository.EtudiantRepository;
import com.academique.backend.repository.FiliereRepository;
import com.academique.backend.repository.MatiereRepository;
import com.academique.backend.repository.NiveauRepository;
import com.academique.backend.repository.RoleRepository;
import com.academique.backend.repository.SeanceRepository;
import com.academique.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FiliereRepository filiereRepository;
    private final NiveauRepository niveauRepository;
    private final ClasseRepository classeRepository;
    private final EtudiantRepository etudiantRepository;
    private final SeanceRepository seanceRepository;
    private final MatiereRepository matiereRepository;
    private final DocumentConfigRepository documentConfigRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            
            // Drop outdated database constraint safely
            try {
                jdbcTemplate.execute("ALTER TABLE factures DROP CONSTRAINT IF EXISTS factures_statut_check");
                System.out.println("✅ Constraint 'factures_statut_check' dropped safely.");
            } catch (Exception e) {
                System.err.println("⚠️ Could not drop constraint: " + e.getMessage());
            }

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

            // Crée les filières, niveaux et classes si ils n'existent pas
            if (filiereRepository.count() == 0) {
                String[][] filiereData = {
                    {"LCS", "Licence Computer Science"},
                    {"LBC", "Licence Business Computing"},
                    {"LCE", "Licence Computer Engineering"}
                };

                String[] sections = {"A", "B", "C", "D"};

                for (String[] fd : filiereData) {
                    Filiere filiere = Filiere.builder()
                        .code(fd[0])
                        .nom(fd[1])
                        .build();
                    filiere = filiereRepository.save(filiere);

                    for (int n = 1; n <= 3; n++) {
                        String niveauCode = fd[0] + n;
                        Niveau niveau = Niveau.builder()
                            .code(niveauCode)
                            .nom(fd[1] + " - Année " + n)
                            .filiere(filiere)
                            .build();
                        niveau = niveauRepository.save(niveau);

                        for (String section : sections) {
                            String classeCode = niveauCode + section;
                            Classe classe = Classe.builder()
                                .code(classeCode)
                                .nom(fd[1] + " - Année " + n + " - Groupe " + section)
                                .niveau(niveau)
                                .build();
                            classeRepository.save(classe);
                        }
                    }
                }

                System.out.println("✅ Filières, niveaux et classes créés (3 filières, 9 niveaux, 36 classes) !");
            }

            // Corrige les anciens étudiants sans classe_id à partir du matricule (ex: ETU-26-LCS1B-03)
            List<Etudiant> toPatch = new ArrayList<>();
            for (Etudiant etudiant : etudiantRepository.findAll()) {
                if (etudiant.getClasse() != null) {
                    continue;
                }

                String classCode = extractClasseCodeFromMatricule(etudiant.getMatricule());
                if (classCode == null) {
                    continue;
                }

                Optional<Classe> classe = classeRepository.findByCode(classCode);
                classe.ifPresent(value -> {
                    etudiant.setClasse(value);
                    toPatch.add(etudiant);
                });
            }

            if (!toPatch.isEmpty()) {
                etudiantRepository.saveAll(toPatch);
                System.out.println("✅ Etudiants rattaches a leurs classes: " + toPatch.size());
            }

            // Corrige les séances incohérentes: niveau de classe aligné sur le semestre matière
            // S1/S2 -> LCS1, S3/S4 -> LCS2, S5 -> LCS3 (en conservant la section A/B/C/D)
            List<Seance> seancesToPatch = new ArrayList<>();
            for (Seance seance : seanceRepository.findAll()) {
                if (seance.getMatiere() == null || seance.getClasse() == null) {
                    continue;
                }

                String targetNiveauPrefix = niveauPrefixFromSemestre(seance.getMatiere().getSemestre());
                String section = extractSectionFromClasseCode(seance.getClasse().getCode());
                if (targetNiveauPrefix == null || section == null) {
                    continue;
                }

                String targetClasseCode = targetNiveauPrefix + section;
                if (targetClasseCode.equalsIgnoreCase(seance.getClasse().getCode())) {
                    continue;
                }

                classeRepository.findByCode(targetClasseCode).ifPresent(targetClasse -> {
                    seance.setClasse(targetClasse);
                    seancesToPatch.add(seance);
                });
            }

            if (!seancesToPatch.isEmpty()) {
                seanceRepository.saveAll(seancesToPatch);
                System.out.println("✅ Seances reaffectees aux bonnes classes: " + seancesToPatch.size());
            }

            // 4. Rattachement des Matières aux Niveaux (Groupement hiérarchique)
            System.out.println("🔍 Scan des matières pour rattachement hiérarchique...");
            List<Matiere> allMatieres = matiereRepository.findAll();
            System.out.println("   📊 Total matières trouvées: " + allMatieres.size());
            
            List<Matiere> matieresToPatch = new ArrayList<>();
            for (Matiere matiere : allMatieres) {
                String code = matiere.getCode();
                if (code == null) {
                    System.out.println("   ⚠️ Matière sans code (ID: " + matiere.getId() + ")");
                    continue;
                }

                String cleanCode = code.trim().toUpperCase();
                String targetNiveauCode = null;

                // Mapping logic based on Semester (S1-S5) found in the code
                if (cleanCode.contains("-S1") || cleanCode.contains("-S2") || cleanCode.contains("LCS1") || cleanCode.contains("LBC1") || cleanCode.contains("LCE1")) {
                    targetNiveauCode = cleanCode.contains("LBC") ? "LBC1" : (cleanCode.contains("LCE") ? "LCE1" : "LCS1");
                }
                if (cleanCode.contains("-S3") || cleanCode.contains("-S4") || cleanCode.contains("LCS2") || cleanCode.contains("LBC2") || cleanCode.contains("LCE2")) {
                    targetNiveauCode = cleanCode.contains("LBC") ? "LBC2" : (cleanCode.contains("LCE") ? "LCE2" : "LCS2");
                }
                if (cleanCode.contains("-S5") || cleanCode.contains("LCS3") || cleanCode.contains("LBC3") || cleanCode.contains("LCE3")) {
                    targetNiveauCode = cleanCode.contains("LBC") ? "LBC3" : (cleanCode.contains("LCE") ? "LCE3" : "LCS3");
                }

                // Override based on explicit Semester mention to satisfy user request:
                // S5 -> Level 3 | S3, S4 -> Level 2 | S1, S2 -> Level 1
                String prefix = cleanCode.contains("LBC") ? "LBC" : (cleanCode.contains("LCE") ? "LCE" : "LCS");
                if (cleanCode.contains("-S1") || cleanCode.contains("-S2")) targetNiveauCode = prefix + "1";
                else if (cleanCode.contains("-S3") || cleanCode.contains("-S4")) targetNiveauCode = prefix + "2";
                else if (cleanCode.contains("-S5")) targetNiveauCode = prefix + "3";

                if (targetNiveauCode != null) {
                    final String codeToSearch = targetNiveauCode;
                    Optional<Niveau> nivOpt = niveauRepository.findByCode(codeToSearch);
                    if (nivOpt.isPresent()) {
                        matiere.setNiveau(nivOpt.get());
                        matieresToPatch.add(matiere);
                        System.out.println("   🔗 MATCH: " + code + " -> " + codeToSearch);
                    } else {
                        System.err.println("   ❌ ERREUR: Niveau '" + codeToSearch + "' introuvable pour matière " + code);
                    }
                } else {
                    System.out.println("   ❓ PAS DE MATCH pour code: " + code);
                }
            }

            if (!matieresToPatch.isEmpty()) {
                matiereRepository.saveAll(matieresToPatch);
                System.out.println("✅ TOTAL Matieres rattachees: " + matieresToPatch.size());
            } else {
                System.out.println("ℹ️ Fin du scan: Aucune nouvelle matière à rattacher.");
            }

            // 5. Document Configs
            if (documentConfigRepository.count() == 0) {
                String[] types = {
                    "ATTESTATION_PRESENCE", "RELEVE_NOTES", 
                    "DEMANDE_STAGE", "VALIDATION_STAGE", 
                    "ATTESTATION_REUSSITE", "ATTESTATION_AFFECTATION"
                };
                for (String t : types) {
                    documentConfigRepository.save(com.academique.backend.entity.DocumentConfig.builder()
                        .typeDocument(t)
                        .enabled(true)
                        .levelRequired(t.contains("STAGE") ? 3 : null)
                        .build());
                }
                System.out.println("✅ Document Configs initialisés");
            }
        };
    }

    private String extractClasseCodeFromMatricule(String matricule) {
        if (matricule == null || matricule.isBlank()) {
            return null;
        }

        String[] parts = matricule.trim().toUpperCase().split("-");
        if (parts.length < 3) {
            return null;
        }

        String candidate = parts[2];
        if (!candidate.matches("[A-Z]{3}[1-3][A-Z]")) {
            return null;
        }

        return candidate;
    }

    private String extractSectionFromClasseCode(String classeCode) {
        if (classeCode == null || classeCode.isBlank()) {
            return null;
        }

        String normalized = classeCode.trim().toUpperCase();
        if (!normalized.matches("[A-Z]{3}[1-3][A-D]")) {
            return null;
        }

        return normalized.substring(normalized.length() - 1);
    }

    private String niveauPrefixFromSemestre(Matiere.Semestre semestre) {
        if (semestre == null) {
            return null;
        }

        return switch (semestre) {
            case S1, S2 -> "LCS1";
            case S3, S4 -> "LCS2";
            case S5 -> "LCS3";
        };
    }
}
