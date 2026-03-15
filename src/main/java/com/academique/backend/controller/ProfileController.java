package com.academique.backend.controller;


import com.academique.backend.entity.User;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String UPLOAD_DIR = "uploads/photos/";

    // ─── GET PROFILE ──────────────────────────────────────
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResourceNotFoundException("User non trouvé"));
        return ResponseEntity.ok(toProfileResponse(user));
    }

    // ─── UPDATE PROFILE ───────────────────────────────────
    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> updateProfile(
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResourceNotFoundException("User non trouvé"));

        if (body.containsKey("nom")) user.setNom(body.get("nom"));
        if (body.containsKey("prenom")) user.setPrenom(body.get("prenom"));
        if (body.containsKey("telephone")) user.setTelephone(body.get("telephone"));

        return ResponseEntity.ok(toProfileResponse(userRepository.save(user)));
    }

    // ─── CHANGE PASSWORD ──────────────────────────────────
    @PutMapping("/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> changePassword(
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResourceNotFoundException("User non trouvé"));

        String ancienPassword = body.get("ancienPassword");
        String nouveauPassword = body.get("nouveauPassword");

        if (!passwordEncoder.matches(ancienPassword, user.getPassword())) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Ancien mot de passe incorrect"));
        }

        if (nouveauPassword == null || nouveauPassword.length() < 6) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Le nouveau mot de passe doit contenir au moins 6 caractères"));
        }

        user.setPassword(passwordEncoder.encode(nouveauPassword));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "Mot de passe modifié avec succès"));
    }

    // ─── UPLOAD PHOTO ─────────────────────────────────────
    @PostMapping("/photo")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> uploadPhoto(
            @RequestParam("photo") MultipartFile file,
            Authentication authentication) throws IOException {

        User user = userRepository.findByEmail(authentication.getName())
            .orElseThrow(() -> new ResourceNotFoundException("User non trouvé"));

        // Valider le type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Format d'image invalide"));
        }

        // Valider la taille (max 5MB)
        if (file.getSize() > 5 * 1024 * 1024) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Image trop grande (max 5MB)"));
        }

        // Créer le dossier si nécessaire
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Supprimer l'ancienne photo
        if (user.getPhoto() != null) {
            Path oldPhoto = Paths.get(user.getPhoto().replace("/uploads/", "uploads/"));
            try { Files.deleteIfExists(oldPhoto); } catch (Exception ignored) {}
        }

        // Sauvegarder la nouvelle photo
        String extension = file.getOriginalFilename() != null ?
            file.getOriginalFilename().substring(
                file.getOriginalFilename().lastIndexOf(".")) : ".jpg";
        String filename = UUID.randomUUID() + extension;
        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        user.setPhoto("/uploads/photos/" + filename);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of(
            "message", "Photo mise à jour avec succès",
            "photo", user.getPhoto()
        ));
    }

    private Object toProfileResponse(User user) {
        return Map.of(
            "id", user.getId(),
            "nom", user.getNom() != null ? user.getNom() : "",
            "prenom", user.getPrenom() != null ? user.getPrenom() : "",
            "email", user.getEmail(),
            "telephone", user.getTelephone() != null ? user.getTelephone() : "",
            "photo", user.getPhoto() != null ? user.getPhoto() : "",
            "roles", user.getRoles().stream()
                .map(r -> r.getName().name()).toList(),
            "actif", user.isActif()
        );
    }
}