package com.academique.backend.service;

import com.academique.backend.dto.request.EtudiantRequest;
import com.academique.backend.dto.response.EtudiantResponse;
import com.academique.backend.entity.Etudiant;
import com.academique.backend.entity.User;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.EtudiantRepository;
import com.academique.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import java.io.ByteArrayOutputStream;
import java.util.List;

import java.time.Year;

@Service
@RequiredArgsConstructor
public class EtudiantService {

    private final EtudiantRepository etudiantRepository;
    private final UserRepository userRepository;

    // ─── CREATE ───────────────────────────────────────────
    public EtudiantResponse create(EtudiantRequest request) {
        if (etudiantRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email déjà utilisé");
        }

        Etudiant etudiant = new Etudiant();
        etudiant.setNom(request.getNom());
        etudiant.setPrenom(request.getPrenom());
        etudiant.setEmail(request.getEmail());
        etudiant.setTelephone(request.getTelephone());
        etudiant.setDateNaissance(request.getDateNaissance());
        etudiant.setAdresse(request.getAdresse());
        etudiant.setMatricule(generateMatricule());

        if (request.getUserId() != null) {
            User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User non trouvé"));
            etudiant.setUser(user);
        }

        return toResponse(etudiantRepository.save(etudiant));
    }
    public EtudiantResponse getByEmail(String email) {
            Etudiant etudiant = etudiantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        return toResponse(etudiant);
    }

    // ─── READ ALL ──────────────────────────────────────────
    public Page<EtudiantResponse> getAll(Pageable pageable) {
        return etudiantRepository.findAll(pageable).map(this::toResponse);
    }

    // ─── READ ONE ──────────────────────────────────────────
    public EtudiantResponse getById(Long id) {
        Etudiant etudiant = etudiantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        return toResponse(etudiant);
    }

    // ─── UPDATE ───────────────────────────────────────────
    public EtudiantResponse update(Long id, EtudiantRequest request) {
        Etudiant etudiant = etudiantRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        etudiant.setNom(request.getNom());
        etudiant.setPrenom(request.getPrenom());
        etudiant.setEmail(request.getEmail());
        etudiant.setTelephone(request.getTelephone());
        etudiant.setDateNaissance(request.getDateNaissance());
        etudiant.setAdresse(request.getAdresse());

        return toResponse(etudiantRepository.save(etudiant));
    }

    // ─── DELETE ───────────────────────────────────────────
    public void delete(Long id) {
        if (!etudiantRepository.existsById(id)) {
            throw new ResourceNotFoundException("Étudiant non trouvé");
        }
        etudiantRepository.deleteById(id);
    }

    // ─── SEARCH ───────────────────────────────────────────
    public Page<EtudiantResponse> search(String query, Pageable pageable) {
        return etudiantRepository.search(query, pageable).map(this::toResponse);
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
            .statut(e.getStatut().name())
            .createdAt(e.getCreatedAt())
            .userEmail(e.getUser() != null ? e.getUser().getEmail() : null)
            .build();
    }
    public byte[] exportPdf() {
    try {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();

        // Titre
        Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, BaseColor.DARK_GRAY);
        Paragraph title = new Paragraph("Liste des Étudiants", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(20);
        document.add(title);

        // Tableau
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);

        // Headers
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, BaseColor.WHITE);
        String[] headers = {"Matricule", "Nom", "Prénom", "Email", "Statut"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new BaseColor(4, 41, 84));
            cell.setPadding(8);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        // Données
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