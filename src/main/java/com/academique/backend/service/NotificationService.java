package com.academique.backend.service;

import com.academique.backend.entity.Notification;
import com.academique.backend.entity.Role;
import com.academique.backend.entity.User;
import com.academique.backend.repository.NotificationRepository;
import com.academique.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.notifications.enseignant.test-email:}")
    private String defaultEnseignantEmail;

    @Value("${app.notifications.etudiant.test-email:}")
    private String defaultEtudiantEmail;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public void createNotification(Long userId, String userRole, String title, String message, String type, String email) {
        String normalizedRole = normalizeRole(userRole);

        // Admin notifications created with userId=0 are fan-out notifications for all admins.
        if ("ADMIN".equals(normalizedRole) && (userId == null || userId == 0L)) {
            List<User> admins = userRepository.findByRoles_Name(Role.RoleName.ROLE_ADMIN);
            for (User admin : admins) {
                saveNotification(admin.getId(), normalizedRole, title, message, type);
            }

            if (admins.isEmpty()) {
                saveNotification(0L, normalizedRole, title, message, type);
            }
        } else {
            saveNotification(userId, normalizedRole, title, message, type);
        }

        // Send email to explicit recipient or configured fallback by role.
        String recipientEmail = resolveRecipientEmail(normalizedRole, email);
        if (recipientEmail != null) {
            sendEmail(recipientEmail, title, message);
        }
    }

    public void sendEmail(String to, String subject, String content) {
        try {
            String normalizedMailUsername = normalizeMailUsername(mailUsername);
            String normalizedMailPassword = normalizeMailPassword(mailPassword);

            if (mailSender instanceof JavaMailSenderImpl senderImpl) {
                if (!normalizedMailUsername.isBlank()) {
                    senderImpl.setUsername(normalizedMailUsername);
                }
                if (!normalizedMailPassword.isBlank()) {
                    senderImpl.setPassword(normalizedMailPassword);
                }
            }

            SimpleMailMessage message = new SimpleMailMessage();
            if (!normalizedMailUsername.isBlank()) {
                message.setFrom(normalizedMailUsername);
            } else {
                message.setFrom("gestionac.noreply@gmail.com");
            }
            message.setTo(to);
            message.setSubject("[GestionAc] " + subject);
            message.setText(content + "\n\nCordialement,\nL'équipe GestionAc");
            mailSender.send(message);
            log.info("Email sent to {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    public List<Notification> getNotifications(Long userId, String userRole) {
        String normalizedRole = normalizeRole(userRole);
        if ("ADMIN".equals(normalizedRole)) {
            List<Long> adminIds = new ArrayList<>();
            if (userId != null) {
                adminIds.add(userId);
            }
            if (!adminIds.contains(0L)) {
                adminIds.add(0L);
            }
            return notificationRepository.findByUserIdInAndUserRoleOrderByCreatedAtDesc(adminIds, normalizedRole);
        }
        return notificationRepository.findByUserIdAndUserRoleOrderByCreatedAtDesc(userId, normalizedRole);
    }

    public long getUnreadCount(Long userId, String userRole) {
        String normalizedRole = normalizeRole(userRole);
        if ("ADMIN".equals(normalizedRole)) {
            List<Long> adminIds = new ArrayList<>();
            if (userId != null) {
                adminIds.add(userId);
            }
            if (!adminIds.contains(0L)) {
                adminIds.add(0L);
            }
            return notificationRepository.countByUserIdInAndUserRoleAndIsReadFalse(adminIds, normalizedRole);
        }
        return notificationRepository.countByUserIdAndUserRoleAndIsReadFalse(userId, normalizedRole);
    }

    public void markAsRead(Long id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    public void markAllAsRead(Long userId, String userRole) {
        List<Notification> notifications = getNotifications(userId, userRole);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    private void saveNotification(Long userId, String userRole, String title, String message, String type) {
        Notification notification = Notification.builder()
                .userId(userId)
                .userRole(userRole)
                .title(title)
                .message(message)
                .type(type)
                .build();
        notificationRepository.save(notification);
    }

    private String normalizeRole(String userRole) {
        if (userRole == null || userRole.isBlank()) {
            return "ETUDIANT";
        }
        return userRole.replace("ROLE_", "").trim().toUpperCase(Locale.ROOT);
    }

    private String resolveRecipientEmail(String normalizedRole, String explicitEmail) {
        if ("ENSEIGNANT".equals(normalizedRole) && defaultEnseignantEmail != null && !defaultEnseignantEmail.isBlank()) {
            return defaultEnseignantEmail;
        }

        if (explicitEmail != null && !explicitEmail.isBlank()) {
            return explicitEmail;
        }

        if ("ETUDIANT".equals(normalizedRole) && defaultEtudiantEmail != null && !defaultEtudiantEmail.isBlank()) {
            return defaultEtudiantEmail;
        }

        return null;
    }

    private String normalizeMailUsername(String rawUsername) {
        if (rawUsername == null) {
            return "";
        }

        return rawUsername.trim().replace("\"", "").replace("'", "");
    }

    private String normalizeMailPassword(String rawPassword) {
        if (rawPassword == null) {
            return "";
        }

        String normalized = rawPassword.trim();

        if ((normalized.startsWith("\"") && normalized.endsWith("\""))
            || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        return normalized.replace(" ", "");
    }
}
