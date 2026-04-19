package com.academique.backend.service;

import com.academique.backend.dto.request.LoginRequest;
import com.academique.backend.dto.request.ForgotPasswordRequest;
import com.academique.backend.dto.request.RefreshTokenRequest;
import com.academique.backend.dto.request.ResetPasswordRequest;
import com.academique.backend.dto.response.AuthResponse;
import com.academique.backend.entity.User;
import com.academique.backend.repository.UserRepository;
import com.academique.backend.security.JwtTokenProvider;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String FORGOT_PASSWORD_GENERIC_MESSAGE = "Si cet email existe, un lien a été envoyé.";
    private static final String RESET_PASSWORD_SUCCESS_MESSAGE = "Mot de passe réinitialisé avec succès.";
    private static final String INVALID_OR_EXPIRED_LINK_MESSAGE = "Lien invalide ou expiré.";
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 30;

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Optional<JavaMailSender> mailSender;

    @Value("${app.reset-password-url:http://localhost:3000/reset-password}")
    private String resetPasswordUrl;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                request.getEmail(),
                request.getPassword()
            )
        );

        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow();

        String role = user.getRoles().stream()
            .findFirst()
            .map(r -> r.getName().name())
            .orElse("ROLE_ETUDIANT");

        String accessToken = jwtTokenProvider.generateToken(user.getEmail(), role);
        String refreshToken = jwtTokenProvider.generateToken(user.getEmail(), role);

        return new AuthResponse(accessToken, refreshToken, role, user.getEmail());
    }

    public AuthResponse refresh(RefreshTokenRequest request) {
        String token = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(token)) {
            throw new RuntimeException("Refresh token invalide ou expiré");
        }

        String email = jwtTokenProvider.getEmailFromToken(token);
        String role = jwtTokenProvider.getRoleFromToken(token);

        String newAccessToken = jwtTokenProvider.generateToken(email, role);
        String newRefreshToken = jwtTokenProvider.generateToken(email, role);

        return new AuthResponse(newAccessToken, newRefreshToken, role, email);
    }

    public Map<String, String> forgotPassword(ForgotPasswordRequest request) {
        String email = request.getEmail().trim();

        userRepository.findByEmail(email).ifPresent(user -> {
            String token = generateResetToken();
            LocalDateTime expiry = LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES);

            user.setResetToken(token);
            user.setResetTokenExpiry(expiry);
            userRepository.save(user);

            sendResetEmailSafely(user.getEmail(), token);
        });

        return Map.of("message", FORGOT_PASSWORD_GENERIC_MESSAGE);
    }

    public Map<String, String> resetPassword(ResetPasswordRequest request) {
        User user = userRepository
            .findByResetTokenAndResetTokenExpiryAfter(request.getToken(), LocalDateTime.now())
            .orElseThrow(() -> new IllegalArgumentException(INVALID_OR_EXPIRED_LINK_MESSAGE));

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        return Map.of("message", RESET_PASSWORD_SUCCESS_MESSAGE);
    }

    public Map<String, Object> validateResetToken(String token) {
        if (token == null || token.isBlank()) {
            return Map.of("valid", false);
        }

        return userRepository
            .findByResetTokenAndResetTokenExpiryAfter(token.trim(), LocalDateTime.now())
            .<Map<String, Object>>map(user -> Map.of(
                "valid", true,
                "expiresAt", user.getResetTokenExpiry()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toString()
            ))
            .orElseGet(() -> Map.of("valid", false));
    }

    private String generateResetToken() {
        String rawToken = UUID.randomUUID() + "-" + System.currentTimeMillis();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashedBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashedBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Impossible de générer un token sécurisé", ex);
        }
    }

    private void sendResetEmailSafely(String toEmail, String token) {
        if (mailSender.isEmpty()) {
            System.err.println("[FORGOT_PASSWORD] JavaMailSender non configuré. Email ignoré pour: " + toEmail);
            return;
        }

        String normalizedMailUsername = normalizeMailUsername(mailUsername);
        String normalizedMailPassword = normalizeMailPassword(mailPassword);

        if (normalizedMailUsername.isBlank() || normalizedMailPassword.isBlank()) {
            System.err.println("[FORGOT_PASSWORD] SMTP non configuré: définissez MAIL_USERNAME et MAIL_PASSWORD (App Password Google)");
            return;
        }

        // Les App Passwords Google sont composés de 16 lettres (les espaces de copie sont supprimés automatiquement).
        if (!normalizedMailPassword.matches("^[A-Za-z]{16}$")) {
            System.err.println("[FORGOT_PASSWORD] MAIL_PASSWORD invalide: utilisez un Google App Password de 16 lettres (pas le mot de passe Gmail). ");
            return;
        }

        try {
            JavaMailSender sender = mailSender.get();
            if (sender instanceof JavaMailSenderImpl senderImpl) {
                senderImpl.setUsername(normalizedMailUsername);
                senderImpl.setPassword(normalizedMailPassword);
            }

            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            if (!normalizedMailUsername.isBlank()) {
                helper.setFrom(normalizedMailUsername);
            }

            helper.setTo(toEmail);
            helper.setSubject("GestionAc — Réinitialisation du mot de passe");
            helper.setText(buildResetEmailHtml(buildResetLink(token)), true);

            sender.send(message);
        } catch (Exception ex) {
            System.err.println("[FORGOT_PASSWORD] Echec envoi email vers " + toEmail + ": " + ex.getMessage());
        }
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

    private String buildResetLink(String token) {
        String separator = resetPasswordUrl.contains("?") ? "&" : "?";
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        return resetPasswordUrl + separator + "token=" + encodedToken;
    }

    private String buildResetEmailHtml(String resetLink) {
        return """
            <div style=\"font-family: Arial, sans-serif; max-width: 500px; margin: auto; padding: 30px; border-radius: 10px; border: 1px solid #e0e0e0;\">
              <h2 style=\"color: #1e2a45;\">GestionAc — Réinitialisation du mot de passe</h2>
              <p>Bonjour,</p>
              <p>Vous avez demandé à réinitialiser votre mot de passe. Cliquez sur le bouton ci-dessous :</p>
              <a href=\"%s\"
                 style=\"display:inline-block; padding: 12px 24px; background-color: #F97316; color: white; border-radius: 6px; text-decoration: none; font-weight: bold;\">
                Réinitialiser mon mot de passe
              </a>
              <p style=\"margin-top: 20px; color: #e53e3e;\">⚠️ Ce lien expire dans <strong>30 minutes</strong>. Passé ce délai, vous devrez faire une nouvelle demande.</p>
              <hr style=\"margin: 20px 0; border: none; border-top: 1px solid #e0e0e0;\" />
              <p style=\"color: #999; font-size: 12px;\">Si vous n'avez pas demandé cette réinitialisation, ignorez cet email. Votre mot de passe ne sera pas modifié.</p>
              <p style=\"color: #999; font-size: 12px;\">© 2026 GestionAc — Système de Gestion Académique</p>
            </div>
            """.formatted(resetLink);
    }
}