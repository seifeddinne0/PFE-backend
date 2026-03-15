package com.academique.backend.config;

import com.academique.backend.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                
                .requestMatchers("/uploads/**").permitAll()
                .requestMatchers("/api/profile/**").authenticated()
                // Documents
                .requestMatchers(HttpMethod.GET, "/api/admin/documents").hasAnyRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/admin/documents/**").hasAnyRole("ADMIN")
                .requestMatchers(HttpMethod.PATCH, "/api/admin/documents/**").hasAnyRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/admin/documents/**").hasAnyRole("ADMIN")
                // ✅ Enseignant et Étudiant peuvent lire les enseignants
                .requestMatchers(HttpMethod.GET, "/api/admin/etudiants").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers(HttpMethod.GET, "/api/admin/etudiants/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers(HttpMethod.GET, "/api/admin/enseignants").hasAnyRole("ADMIN", "ENSEIGNANT", "ETUDIANT")
                .requestMatchers(HttpMethod.GET, "/api/admin/enseignants/**").hasAnyRole("ADMIN", "ENSEIGNANT", "ETUDIANT")

                // Enseignant peut lire et gérer les absences
                .requestMatchers(HttpMethod.GET, "/api/admin/absences").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers(HttpMethod.GET, "/api/admin/absences/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers(HttpMethod.PUT, "/api/admin/absences/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers(HttpMethod.DELETE, "/api/admin/absences/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                // ✅ Enseignant peut lire matières
                .requestMatchers(HttpMethod.GET, "/api/admin/matieres").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers(HttpMethod.GET, "/api/admin/matieres/**").hasAnyRole("ADMIN", "ENSEIGNANT")

                // ✅ Enseignant peut lire, modifier et supprimer les notes
                .requestMatchers(HttpMethod.GET, "/api/admin/notes").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers(HttpMethod.GET, "/api/admin/notes/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers(HttpMethod.PUT, "/api/admin/notes/**").hasAnyRole("ADMIN", "ENSEIGNANT")
                .requestMatchers(HttpMethod.DELETE, "/api/admin/notes/**").hasAnyRole("ADMIN", "ENSEIGNANT")

                // Reste — admin seulement
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/enseignant/**").hasRole("ENSEIGNANT")
                .requestMatchers("/api/etudiant/**").hasRole("ETUDIANT")
                .anyRequest().authenticated()
            )
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(passwordEncoder());
        provider.setUserDetailsService(userDetailsService);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}