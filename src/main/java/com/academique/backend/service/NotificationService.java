package com.academique.backend.service;

import com.academique.backend.entity.Notification;
import com.academique.backend.entity.Role;
import com.academique.backend.entity.User;
import com.academique.backend.repository.NotificationRepository;
import com.academique.backend.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Optional<JavaMailSender> mailSender;

    @Value("${app.notifications.enseignant.test-email:}")
    private String defaultEnseignantEmail;

    @Value("${app.notifications.etudiant.test-email:}")
    private String defaultEtudiantEmail;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public void createNotification(Long userId, String userRole, String title, String message, String type,
            String email) {
        String normalizedRole = normalizeRole(userRole);

        // Admin notifications created with userId=0 are fan-out notifications for all
        // admins.
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

        if (!"CHAT".equalsIgnoreCase(type)
                && !"NOTE".equalsIgnoreCase(type)
                && !"BULLETIN".equalsIgnoreCase(type)) {
            // Send email to explicit recipient or configured fallback by role.
            String recipientEmail = resolveRecipientEmail(normalizedRole, email);
            if (recipientEmail != null) {
                sendEmail(recipientEmail, title, message);
            }
        }
    }

    public void sendEmail(String to, String subject, String content) {
        if (mailSender.isEmpty()) {
            log.warn("JavaMailSender non configuré. Email ignoré pour {}", to);
            return;
        }

        String normalizedMailUsername = normalizeMailUsername(mailUsername);
        String normalizedMailPassword = normalizeMailPassword(mailPassword);

        if (normalizedMailUsername.isBlank() || normalizedMailPassword.isBlank()) {
            log.warn("SMTP non configuré correctement. Email ignoré pour {}", to);
            return;
        }

        if (!normalizedMailPassword.matches("^[A-Za-z]{16}$")) {
            log.warn("MAIL_PASSWORD invalide. Utilisez un Google App Password de 16 lettres. Email ignoré pour {}", to);
            return;
        }

        try {
            log.warn("=== MAIL DEBUG === USERNAME:[{}] PASS_LEN:{}", mailUsername,
                    mailPassword != null ? mailPassword.length() : "NULL");

            JavaMailSender sender = mailSender.get();
            if (sender instanceof JavaMailSenderImpl senderImpl) {
                senderImpl.setUsername(normalizedMailUsername);
                senderImpl.setPassword(normalizedMailPassword);
            }

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(normalizedMailUsername);
            helper.setTo(to);
            helper.setSubject("[GestionAc] " + subject);
            helper.setText(buildPlainEmailBody(content), false);

            sender.send(message);
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

    @Transactional
    public void markAllAsRead(Long userId, String userRole) {
        List<Notification> notifications = getNotifications(userId, userRole);
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void deleteNotification(Long id, Long userId, String userRole) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUserId() != null
                    && n.getUserId().equals(userId)
                    && normalizeRole(n.getUserRole()).equals(normalizeRole(userRole))) {
                notificationRepository.delete(n);
            }
        });
    }

    @Transactional
    public void deleteAllForUser(Long userId, String userRole) {
        notificationRepository.deleteByUserIdAndUserRole(userId, normalizeRole(userRole));
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
        if ("ENSEIGNANT".equals(normalizedRole) && defaultEnseignantEmail != null
                && !defaultEnseignantEmail.isBlank()) {
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

    private String buildPlainEmailBody(String content) {
        return content + "\n\nCordialement,\nL'équipe GestionAc";
    }
}
