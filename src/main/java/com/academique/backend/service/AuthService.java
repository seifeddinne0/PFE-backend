package com.academique.backend.service;

import com.academique.backend.dto.request.LoginRequest;
import com.academique.backend.dto.request.RefreshTokenRequest;
import com.academique.backend.dto.response.AuthResponse;
import com.academique.backend.entity.User;
import com.academique.backend.repository.UserRepository;
import com.academique.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

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
}