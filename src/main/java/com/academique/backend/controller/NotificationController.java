package com.academique.backend.controller;

import com.academique.backend.entity.Notification;
import com.academique.backend.entity.User;
import com.academique.backend.repository.UserRepository;
import com.academique.backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @GetMapping
    public List<Notification> getMyNotifications(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        String role = resolveRole(auth);
        return notificationService.getNotifications(user.getId(), role);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        String role = resolveRole(auth);
        return Map.of("count", notificationService.getUnreadCount(user.getId(), role));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
        String role = resolveRole(auth);
        notificationService.markAllAsRead(user.getId(), role);
        return ResponseEntity.ok().build();
    }

    private String resolveRole(Authentication auth) {
        if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()))) {
            return "ADMIN";
        }
        if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ENSEIGNANT".equals(a.getAuthority()))) {
            return "ENSEIGNANT";
        }
        if (auth.getAuthorities().stream().anyMatch(a -> "ROLE_ETUDIANT".equals(a.getAuthority()))) {
            return "ETUDIANT";
        }

        return auth.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", "").toUpperCase(Locale.ROOT))
                .orElse("ETUDIANT");
    }
}
