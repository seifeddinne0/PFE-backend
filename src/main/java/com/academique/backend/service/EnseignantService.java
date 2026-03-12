package com.academique.backend.service;

import com.academique.backend.dto.request.EnseignantRequest;
import com.academique.backend.dto.response.EnseignantResponse;
import com.academique.backend.entity.Enseignant;
import com.academique.backend.entity.Role;
import com.academique.backend.entity.User;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.EnseignantRepository;
import com.academique.backend.repository.RoleRepository;
import com.academique.backend.repository.UserRepository;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.time.Year;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EnseignantService {

    private final EnseignantRepository enseignantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public EnseignantResponse create(EnseignantRequest request) {
        if (enseignantRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email déjà utilisé");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setNom(request.getNom());
        user.setPrenom(request.getPrenom());
        user.setActif(true);

        Role role = roleRepository.findByName(Role.RoleName.ROLE_ENSEIGNANT)
            .orElseThrow(() -> new ResourceNotFoundException("Rôle ENSEIGNANT non trouvé"));
        user.setRoles(Set.of(role));

        User savedUser = userRepository.save(user);

        Enseignant enseignant = new Enseignant();
        enseignant.setNom(request.getNom());
        enseignant.setPrenom(request.getPrenom());
        enseignant.setEmail(request.getEmail());
        enseignant.setTelephone(request.getTelephone());
        enseignant.setDateNaissance(request.getDateNaissance());
        enseignant.setAdresse(request.getAdresse());
        enseignant.setSpecialite(request.getSpecialite());
        enseignant.setGrade(request.getGrade());
        enseignant.setMatricule(generateMatricule());
        enseignant.setUser(savedUser);

        return toResponse(enseignantRepository.save(enseignant));
    }

    public Page<EnseignantResponse> getAll(Pageable pageable) {
        return enseignantRepository.findAll(pageable).map(this::toResponse);
    }

    public EnseignantResponse getById(Long id) {
        return toResponse(enseignantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé")));
    }

    public EnseignantResponse getByEmail(String email) {
        return toResponse(enseignantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé")));
    }

    public EnseignantResponse update(Long id, EnseignantRequest request) {
        Enseignant enseignant = enseignantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));

        enseignant.setNom(request.getNom());
        enseignant.setPrenom(request.getPrenom());
        enseignant.setEmail(request.getEmail());
        enseignant.setTelephone(request.getTelephone());
        enseignant.setDateNaissance(request.getDateNaissance());
        enseignant.setAdresse(request.getAdresse());
        enseignant.setSpecialite(request.getSpecialite());
        enseignant.setGrade(request.getGrade());

        if (enseignant.getUser() != null) {
            User user = enseignant.getUser();
            user.setNom(request.getNom());
            user.setPrenom(request.getPrenom());
            user.setEmail(request.getEmail());
            if (request.getPassword() != null && !request.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
            }
            userRepository.save(user);
        }

        return toResponse(enseignantRepository.save(enseignant));
    }

    public void delete(Long id) {
        if (!enseignantRepository.existsById(id)) {
            throw new ResourceNotFoundException("Enseignant non trouvé");
        }
        enseignantRepository.deleteById(id);
    }

    public Page<EnseignantResponse> search(String query, Pageable pageable) {
        return enseignantRepository.search(query, pageable).map(this::toResponse);
    }

    private String generateMatricule() {
        int year = Year.now().getValue();
        long count = enseignantRepository.count() + 1;
        return String.format("ENS-%d-%04d", year, count);
    }

    public byte[] exportPdf() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, BaseColor.DARK_GRAY);
            Paragraph title = new Paragraph("Liste des Enseignants", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);

            Font headerFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, BaseColor.WHITE);
            String[] headers = {"Matricule", "Nom", "Prénom", "Email", "Spécialité", "Statut"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new BaseColor(4, 41, 84));
                cell.setPadding(8);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            Font dataFont = new Font(Font.FontFamily.HELVETICA, 10);
            List<Enseignant> enseignants = enseignantRepository.findAll();
            boolean alternate = false;
            for (Enseignant e : enseignants) {
                BaseColor rowColor = alternate ? new BaseColor(240, 240, 240) : BaseColor.WHITE;
                String[] values = {
                    e.getMatricule(), e.getNom(), e.getPrenom(),
                    e.getEmail(),
                    e.getSpecialite() != null ? e.getSpecialite() : "",
                    e.getStatut().name()
                };
                for (String v : values) {
                    PdfPCell cell = new PdfPCell(new Phrase(v, dataFont));
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

    private EnseignantResponse toResponse(Enseignant e) {
        return EnseignantResponse.builder()
            .id(e.getId())
            .matricule(e.getMatricule())
            .nom(e.getNom())
            .prenom(e.getPrenom())
            .email(e.getEmail())
            .telephone(e.getTelephone())
            .dateNaissance(e.getDateNaissance())
            .adresse(e.getAdresse())
            .specialite(e.getSpecialite())
            .grade(e.getGrade())
            .statut(e.getStatut().name())
            .createdAt(e.getCreatedAt())
            .userEmail(e.getUser() != null ? e.getUser().getEmail() : null)
            .build();
    }
}