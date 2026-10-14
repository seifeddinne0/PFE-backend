package com.academique.backend.service;

import com.academique.backend.dto.request.EtudiantRequest;
import com.academique.backend.dto.response.EtudiantResponse;
import com.academique.backend.entity.*;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.ClasseRepository;
import com.academique.backend.repository.EtudiantRepository;
import com.academique.backend.repository.RoleRepository;
import com.academique.backend.repository.UserRepository;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EtudiantService {

    private final EtudiantRepository etudiantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ClasseRepository classeRepository;
    private final PasswordEncoder passwordEncoder;

    // ─── CREATE ───────────────────────────────────────────
    public EtudiantResponse create(EtudiantRequest request) {
        if (etudiantRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email déjà utilisé");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setActif(true);

        Role role = roleRepository.findByName(Role.RoleName.ROLE_ETUDIANT)
            .orElseThrow(() -> new ResourceNotFoundException("Rôle ETUDIANT non trouvé"));
        user.setRoles(Set.of(role));

        User savedUser = userRepository.save(user);

        Etudiant etudiant = new Etudiant();
        etudiant.setNom(request.getNom());
        etudiant.setPrenom(request.getPrenom());
        etudiant.setEmail(request.getEmail());
        etudiant.setTelephone(request.getTelephone());
        etudiant.setDateNaissance(request.getDateNaissance());
        etudiant.setAdresse(request.getAdresse());
        etudiant.setMatricule(generateMatricule());
        etudiant.setUser(savedUser);

        if (request.getClasseId() == null) {
            throw new IllegalArgumentException("La classe est obligatoire");
        }

        Classe classe = classeRepository.findById(request.getClasseId())
            .orElseThrow(() -> new ResourceNotFoundException("Classe non trouvée"));
        etudiant.setClasse(classe);

        return toResponse(etudiantRepository.save(etudiant));
    }

    // ─── GET BY EMAIL ─────────────────────────────────────
    public EtudiantResponse getByEmail(String email) {
        Etudiant etudiant = etudiantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        return toResponse(etudiant);
    }

    // ─── READ ALL ──────────────────────────────────────────
    public Page<EtudiantResponse> getAll(Pageable pageable) {
        Pageable safePageable = Objects.requireNonNull(pageable, "pageable ne peut pas être null");
        return etudiantRepository.findAll(safePageable).map(this::toResponse);
    }

    // ─── READ ONE ──────────────────────────────────────────
    public EtudiantResponse getById(Long id) {
        Long safeId = Objects.requireNonNull(id, "id ne peut pas être null");
        Etudiant etudiant = etudiantRepository.findById(safeId)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        return toResponse(etudiant);
    }

    // ─── UPDATE ───────────────────────────────────────────
    public EtudiantResponse update(Long id, EtudiantRequest request) {
        Long safeId = Objects.requireNonNull(id, "id ne peut pas être null");
        Etudiant etudiant = etudiantRepository.findById(safeId)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        etudiant.setNom(request.getNom());
        etudiant.setPrenom(request.getPrenom());
        etudiant.setEmail(request.getEmail());
        etudiant.setTelephone(request.getTelephone());
        etudiant.setDateNaissance(request.getDateNaissance());
        etudiant.setAdresse(request.getAdresse());

        if (request.getClasseId() != null) {
            Long classeId = Objects.requireNonNull(request.getClasseId(), "classeId ne peut pas être null");
            Classe classe = classeRepository.findById(classeId)
                .orElseThrow(() -> new ResourceNotFoundException("Classe non trouvée"));
            etudiant.setClasse(classe);
        }

        if (etudiant.getUser() != null) {
            User user = etudiant.getUser();
            user.setNom(request.getNom());
            user.setPrenom(request.getPrenom());
            user.setEmail(request.getEmail());
            if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
            }
            userRepository.save(user);
        }

        return toResponse(etudiantRepository.save(etudiant));
    }

    public EtudiantResponse setNotesAccess(Long id, boolean enabled) {
        Long safeId = Objects.requireNonNull(id, "id ne peut pas être null");
        Etudiant etudiant = etudiantRepository.findById(safeId)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        etudiant.setNotesAccessEnabled(enabled);
        return toResponse(etudiantRepository.save(etudiant));
    }

    // ─── DELETE ───────────────────────────────────────────
    public void delete(Long id) {
        Long safeId = Objects.requireNonNull(id, "id ne peut pas être null");
        if (!etudiantRepository.existsById(safeId)) {
            throw new ResourceNotFoundException("Étudiant non trouvé");
        }
        etudiantRepository.deleteById(safeId);
    }

    // ─── SEARCH ───────────────────────────────────────────
    public Page<EtudiantResponse> search(String query, Pageable pageable) {
        Pageable safePageable = Objects.requireNonNull(pageable, "pageable ne peut pas être null");
        String safeQuery = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        if (safeQuery.isEmpty()) {
            return etudiantRepository.findAll(safePageable).map(this::toResponse);
        }

        List<Etudiant> filtered = etudiantRepository.findAll().stream()
            .filter(e -> containsIgnoreCase(e.getNom(), safeQuery)
                || containsIgnoreCase(e.getPrenom(), safeQuery)
                || containsIgnoreCase(e.getMatricule(), safeQuery)
                || containsIgnoreCase(e.getEmail(), safeQuery))
            .toList();

        int start = (int) safePageable.getOffset();
        if (start >= filtered.size()) {
            return new PageImpl<>(List.of(), safePageable, filtered.size());
        }

        int end = Math.min(start + safePageable.getPageSize(), filtered.size());
        List<EtudiantResponse> content = filtered.subList(start, end).stream()
            .map(this::toResponse)
            .toList();

        return new PageImpl<>(content, safePageable, filtered.size());
    }

    // ─── MATRICULE AUTO ───────────────────────────────────
    private String generateMatricule() {
        int year = Year.now().getValue();
        long count = etudiantRepository.count() + 1;
        return String.format("ETU-%d-%04d", year, count);
    }

    // ─── MAPPER ───────────────────────────────────────────
    private EtudiantResponse toResponse(Etudiant e) {
        return EtudiantResponse.builder()
            .id(e.getId())
            .matricule(e.getMatricule())
            .nom(e.getNom())
            .prenom(e.getPrenom())
            .email(e.getEmail())
            .telephone(e.getTelephone())
            .dateNaissance(e.getDateNaissance())
            .adresse(e.getAdresse())
                .statut(e.getStatut() != null ? e.getStatut().name() : null)
            .createdAt(e.getCreatedAt())
            .userEmail(e.getUser() != null ? e.getUser().getEmail() : null)
            .photo(e.getUser() != null ? e.getUser().getPhoto() : null)
            .classeId(e.getClasse() != null ? e.getClasse().getId() : null)
            .classeCode(e.getClasse() != null ? e.getClasse().getCode() : null)
                .notesAccessEnabled(e.getNotesAccessEnabled())
            .build();
    }

            private boolean containsIgnoreCase(String value, String query) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(query);
            }

    // ─── EXPORT PDF ───────────────────────────────────────
    public byte[] exportPdf() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, BaseColor.DARK_GRAY);
            Paragraph title = new Paragraph("Liste des Étudiants", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);

            Font headerFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, BaseColor.WHITE);
            String[] headers = {"Matricule", "Nom", "Prénom", "Email", "Statut"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new BaseColor(4, 41, 84));
                cell.setPadding(8);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            Font dataFont = new Font(Font.FontFamily.HELVETICA, 10);
            List<Etudiant> etudiants = etudiantRepository.findAll();
            boolean alternate = false;
            for (Etudiant e : etudiants) {
                BaseColor rowColor = alternate ? new BaseColor(240, 240, 240) : BaseColor.WHITE;
                String[] values = {
                    e.getMatricule(), e.getNom(), e.getPrenom(),
                    e.getEmail(), e.getStatut().name()
                };
                for (String v : values) {
                    PdfPCell cell = new PdfPCell(new Phrase(v != null ? v : "", dataFont));
                    cell.setBackgroundColor(rowColor);
                    cell.setPadding(6);
                    table.addCell(cell);
                }
                alternate = !alternate;
            }

            document.add(table);
            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF", e);
        }
    }
}