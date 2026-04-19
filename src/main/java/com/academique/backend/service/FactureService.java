package com.academique.backend.service;

import com.academique.backend.dto.request.FactureRequest;
import com.academique.backend.dto.request.BatchFactureRequest;
import com.academique.backend.dto.response.FactureResponse;
import com.academique.backend.entity.Etudiant;
import com.academique.backend.entity.Facture;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.EtudiantRepository;
import com.academique.backend.repository.FactureRepository;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FactureService {

    private final FactureRepository factureRepository;
    private final EtudiantRepository etudiantRepository;

    // ─── CREATE ───────────────────────────────────────────
    public FactureResponse create(FactureRequest request) {
        Etudiant etudiant = etudiantRepository.findById(request.getEtudiantId())
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        Facture facture = new Facture();
        facture.setNumero(generateNumero());
        facture.setMontant(request.getMontant());
        facture.setDescription(request.getDescription());
        facture.setDateEcheance(request.getDateEcheance());
        facture.setEtudiant(etudiant);
        facture.setStatut(Facture.Statut.NON_PAYEE);

        if (request.getTypeFacture() != null)
            facture.setTypeFacture(Facture.TypeFacture.valueOf(request.getTypeFacture()));

        return toResponse(factureRepository.save(facture));
    }

    public Map<String, Object> createBatch(BatchFactureRequest request) {
        List<Long> classeIds = request.getClasseIds();
        List<Etudiant> etudiants = etudiantRepository.findByClasseIdIn(classeIds);

        if (etudiants.isEmpty()) {
            throw new ResourceNotFoundException("Aucun étudiant trouvé pour les classes sélectionnées");
        }

        Facture.TypeFacture type = parseTypeFacture(request.getTypeFacture());
        int createdCount = 0;

        for (Etudiant etudiant : etudiants) {
            Facture facture = new Facture();
            facture.setNumero(generateNumero());
            facture.setMontant(request.getMontant());
            facture.setDescription(request.getDescription());
            facture.setDateEcheance(request.getDateEcheance());
            facture.setEtudiant(etudiant);
            facture.setStatut(Facture.Statut.NON_PAYEE);
            facture.setTypeFacture(type);
            factureRepository.save(facture);
            createdCount++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("classesCount", classeIds.size());
        result.put("studentsCount", etudiants.size());
        result.put("createdCount", createdCount);
        result.put("montant", request.getMontant());
        result.put("typeFacture", type.name());
        result.put("message", "Factures créées avec succès pour le groupe sélectionné");
        return result;
    }

    // ─── READ ALL ──────────────────────────────────────────
    public Page<FactureResponse> getAll(Pageable pageable) {
        return factureRepository.findAll(pageable).map(this::toResponse);
    }

    // ─── READ BY ID ────────────────────────────────────────
    public FactureResponse getById(Long id) {
        return toResponse(factureRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée")));
    }

    // ─── READ BY ETUDIANT ──────────────────────────────────
    public List<FactureResponse> getByEtudiant(Long etudiantId) {
        return factureRepository.findByEtudiantId(etudiantId)
            .stream().map(this::toResponse).toList();
    }

    // ─── READ BY ETUDIANT EMAIL ───────────────────────────
    public List<FactureResponse> getByEtudiantEmail(String email) {
        Etudiant etudiant = etudiantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        return factureRepository.findByEtudiantId(etudiant.getId())
            .stream().map(this::toResponse).toList();
    }

    // ─── READ BY STATUT ────────────────────────────────────
    public Page<FactureResponse> getByStatut(String statut, Pageable pageable) {
        return factureRepository.findByStatut(
            Facture.Statut.valueOf(statut), pageable).map(this::toResponse);
    }

    // ─── UPDATE ───────────────────────────────────────────
    public FactureResponse update(Long id, FactureRequest request) {
        Facture facture = factureRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée"));

        facture.setMontant(request.getMontant());
        facture.setDescription(request.getDescription());
        facture.setDateEcheance(request.getDateEcheance());

        if (request.getTypeFacture() != null)
            facture.setTypeFacture(Facture.TypeFacture.valueOf(request.getTypeFacture()));

        if (request.getStatut() != null)
            facture.setStatut(Facture.Statut.valueOf(request.getStatut()));

        return toResponse(factureRepository.save(facture));
    }

    // ─── MARQUER PAYEE ────────────────────────────────────
    public FactureResponse marquerPayee(Long id) {
        Facture facture = factureRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée"));
        facture.setStatut(Facture.Statut.PAYEE);
        facture.setDatePaiement(LocalDate.now());
        return toResponse(factureRepository.save(facture));
    }

    public FactureResponse confirmerPaiementEtudiant(
        Long factureId,
        String etudiantEmail,
        LocalDate datePaiement,
        MultipartFile image
    ) {
        Etudiant etudiant = etudiantRepository.findByEmail(etudiantEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        Facture facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée"));

        if (!facture.getEtudiant().getId().equals(etudiant.getId())) {
            throw new IllegalArgumentException("Vous ne pouvez confirmer que vos propres factures");
        }

        if (facture.getStatut() == Facture.Statut.ANNULEE) {
            throw new IllegalArgumentException("Cette facture est annulée");
        }

        if (facture.getStatut() == Facture.Statut.PAYEE) {
            throw new IllegalArgumentException("Cette facture est déjà marquée comme payée");
        }

        if (datePaiement == null) {
            throw new IllegalArgumentException("La date de paiement est obligatoire");
        }

        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("La photo du reçu est obligatoire");
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Le fichier doit être une image");
        }

        String extension = extractExtension(image.getOriginalFilename());
        String fileName = "receipt_" + factureId + "_" + UUID.randomUUID() + extension;
        Path uploadDir = Path.of("uploads", "receipts");
        Path targetPath = uploadDir.resolve(fileName);

        try {
            Files.createDirectories(uploadDir);
            Files.copy(image.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'upload du reçu", e);
        }

        facture.setPreuvePaiement("/uploads/receipts/" + fileName);
        facture.setDatePaiement(datePaiement);
        facture.setStatut(Facture.Statut.EN_ATTENTE);

        return toResponse(factureRepository.save(facture));
    }

    // ─── ANNULER ──────────────────────────────────────────
    public FactureResponse annuler(Long id) {
        Facture facture = factureRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée"));
        facture.setStatut(Facture.Statut.ANNULEE);
        return toResponse(factureRepository.save(facture));
    }

    // ─── DELETE ───────────────────────────────────────────
    public void delete(Long id) {
        if (!factureRepository.existsById(id))
            throw new ResourceNotFoundException("Facture non trouvée");
        factureRepository.deleteById(id);
    }

    // ─── STATS ────────────────────────────────────────────
    public Map<String, Object> getStats() {
        return Map.of(
            "totalPayee", factureRepository.totalPaye() != null ? factureRepository.totalPaye() : 0.0,
            "totalImpaye", factureRepository.totalImpaye() != null ? factureRepository.totalImpaye() : 0.0,
            "countPayee", factureRepository.countByStatut(Facture.Statut.PAYEE),
            "countNonPayee", factureRepository.countByStatut(Facture.Statut.NON_PAYEE)
        );
    }

    // ─── EXPORT PDF ───────────────────────────────────────
    public byte[] exportPdf(Long etudiantId) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, BaseColor.DARK_GRAY);
            Paragraph title = new Paragraph("Relevé des Factures", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            List<Facture> factures;
            if (etudiantId != null) {
                Etudiant etudiant = etudiantRepository.findById(etudiantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
                Font infoFont = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL);
                Paragraph info = new Paragraph(
                    "Étudiant : " + etudiant.getNom() + " " + etudiant.getPrenom() +
                    " | Matricule : " + etudiant.getMatricule(), infoFont);
                info.setSpacingAfter(15);
                document.add(info);
                factures = factureRepository.findByEtudiantId(etudiantId);
            } else {
                factures = factureRepository.findAll();
            }

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setWidths(new float[]{2f, 2f, 2f, 2f, 2f, 2f});

            Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
            String[] headers = {"Numéro", "Type", "Montant", "Paiement", "Confirmation", "Statut"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new BaseColor(4, 41, 84));
                cell.setPadding(8);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            Font dataFont = new Font(Font.FontFamily.HELVETICA, 9);
            boolean alternate = false;
            double totalPaye = 0;
            double totalImpaye = 0;

            for (Facture f : factures) {
                BaseColor rowColor = alternate ? new BaseColor(240, 240, 240) : BaseColor.WHITE;
                if (f.getStatut() == Facture.Statut.PAYEE) {
                    rowColor = new BaseColor(220, 255, 220);
                    totalPaye += f.getMontant();
                } else if (f.getStatut() == Facture.Statut.NON_PAYEE) {
                    rowColor = new BaseColor(255, 220, 220);
                    totalImpaye += f.getMontant();
                }

                String[] values = {
                    f.getNumero(),
                    f.getTypeFacture() != null ? f.getTypeFacture().name() : "-",
                    String.format("%.2f DT", f.getMontant()),
                    f.getDatePaiement() != null ? f.getDatePaiement().toString() : "-",
                    f.getPreuvePaiement() != null && !f.getPreuvePaiement().isBlank() ? "OUI" : "NON",
                    f.getStatut().name()
                };

                for (String v : values) {
                    PdfPCell cell = new PdfPCell(new Phrase(v, dataFont));
                    cell.setBackgroundColor(rowColor);
                    cell.setPadding(6);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
                alternate = !alternate;
            }

            document.add(table);

            Font totalFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD);
            Paragraph totaux = new Paragraph(
                "\nTotal Payé : " + String.format("%.2f DT", totalPaye) +
                "     |     Total Impayé : " + String.format("%.2f DT", totalImpaye),
                totalFont);
            totaux.setSpacingBefore(15);
            totaux.setAlignment(Element.ALIGN_RIGHT);
            document.add(totaux);

            document.close();
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF factures", e);
        }
    }

    // ─── NUMERO AUTO ──────────────────────────────────────
    private String generateNumero() {
        int year = Year.now().getValue();
        long count = factureRepository.count() + 1;
        return String.format("FAC-%d-%04d", year, count);
    }

    private Facture.TypeFacture parseTypeFacture(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return Facture.TypeFacture.SCOLARITE;
        }
        return Facture.TypeFacture.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return ".jpg";
        }

        int index = fileName.lastIndexOf('.');
        if (index == -1 || index == fileName.length() - 1) {
            return ".jpg";
        }

        String extension = fileName.substring(index).toLowerCase(Locale.ROOT);
        if (extension.equals(".png") || extension.equals(".jpg") || extension.equals(".jpeg") || extension.equals(".webp")) {
            return extension;
        }
        return ".jpg";
    }

    // ─── MAPPER ───────────────────────────────────────────
    private FactureResponse toResponse(Facture f) {
        return FactureResponse.builder()
            .id(f.getId())
            .numero(f.getNumero())
            .montant(f.getMontant())
            .statut(f.getStatut().name())
            .typeFacture(f.getTypeFacture() != null ? f.getTypeFacture().name() : null)
            .description(f.getDescription())
            .dateEcheance(f.getDateEcheance())
            .datePaiement(f.getDatePaiement())
            .preuvePaiement(f.getPreuvePaiement())
            .createdAt(f.getCreatedAt())
            .etudiantId(f.getEtudiant().getId())
            .etudiantNom(f.getEtudiant().getNom())
            .etudiantPrenom(f.getEtudiant().getPrenom())
            .etudiantMatricule(f.getEtudiant().getMatricule())
            .build();
    }
}