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
    private final NotificationService notificationService;

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
        
        Facture.TypePaiement typePaiement = parseTypePaiement(request.getTypePaiement());
        facture.setTypePaiement(typePaiement);

        if (typePaiement == Facture.TypePaiement.DEUX_TRANCHES) {
            double half = request.getMontant() / 2.0;
            facture.setMontant(half);
            facture.setDescription(request.getDescription() + " (Tranche 1)");
            
            Facture saved1 = factureRepository.save(facture);
            
            // Check if we already passed Feb 15th this year
            LocalDate today = LocalDate.now();
            LocalDate feb15 = LocalDate.of(today.getYear(), 2, 15);
            
            if (today.isAfter(feb15)) {
                // Generate 2nd tranche immediately since date passed
                Facture tranche2 = new Facture();
                tranche2.setNumero(generateNumero());
                tranche2.setMontant(half);
                tranche2.setDescription(request.getDescription() + " (Tranche 2)");
                tranche2.setEtudiant(etudiant);
                tranche2.setStatut(Facture.Statut.NON_PAYEE);
                tranche2.setTypeFacture(facture.getTypeFacture());
                tranche2.setTypePaiement(Facture.TypePaiement.DEUX_TRANCHES);
                tranche2.setDateEcheance(feb15);
                factureRepository.save(tranche2);
            }
            
            return toResponse(saved1);
        }

        return toResponse(factureRepository.save(facture));
    }

    public Map<String, Object> createBatch(BatchFactureRequest request) {
        List<Long> classeIds = request.getClasseIds();
        List<Etudiant> etudiants = etudiantRepository.findByClasseIdIn(classeIds);

        if (etudiants.isEmpty()) {
            throw new ResourceNotFoundException("Aucun étudiant trouvé pour les classes sélectionnées");
        }

        Facture.TypeFacture type = parseTypeFacture(request.getTypeFacture());
        Facture.TypePaiement typePaiement = parseTypePaiement(request.getTypePaiement());
        int createdCount = 0;
        long currentCount = factureRepository.count();

        for (Etudiant etudiant : etudiants) {
            if (typePaiement == Facture.TypePaiement.DEUX_TRANCHES) {
                double half = request.getMontant() / 2.0;
                
                // Tranche 1
                Facture f1 = new Facture();
                f1.setNumero(generateNumero(++currentCount));
                f1.setMontant(half);
                f1.setDescription(request.getDescription() + " (Tranche 1)");
                f1.setDateEcheance(request.getDateEcheance());
                f1.setEtudiant(etudiant);
                f1.setStatut(Facture.Statut.NON_PAYEE);
                f1.setTypeFacture(type);
                f1.setTypePaiement(typePaiement);
                factureRepository.save(f1);
                createdCount++;
                
                // If past Feb 15, generate Tranche 2 now
                LocalDate today = LocalDate.now();
                LocalDate feb15 = LocalDate.of(today.getYear(), 2, 15);
                if (today.isAfter(feb15)) {
                    Facture f2 = new Facture();
                    f2.setNumero(generateNumero(++currentCount));
                    f2.setMontant(half);
                    f2.setDescription(request.getDescription() + " (Tranche 2)");
                    f2.setDateEcheance(feb15);
                    f2.setEtudiant(etudiant);
                    f2.setStatut(Facture.Statut.NON_PAYEE);
                    f2.setTypeFacture(type);
                    f2.setTypePaiement(typePaiement);
                    factureRepository.save(f2);
                    createdCount++;
                }
            } else {
                Facture facture = new Facture();
                facture.setNumero(generateNumero(++currentCount));
                facture.setMontant(request.getMontant());
                facture.setDescription(request.getDescription());
                facture.setDateEcheance(request.getDateEcheance());
                facture.setEtudiant(etudiant);
                facture.setStatut(Facture.Statut.NON_PAYEE);
                facture.setTypeFacture(type);
                facture.setTypePaiement(typePaiement);
                factureRepository.save(facture);
                createdCount++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("classesCount", classeIds.size());
        result.put("studentsCount", etudiants.size());
        result.put("createdCount", createdCount);
        result.put("montant", request.getMontant());
        result.put("typeFacture", type.name());
        result.put("typePaiement", typePaiement.name());
        result.put("message", "Factures créées avec succès pour le groupe sélectionné");
        return result;
    }

    // ─── READ ALL ──────────────────────────────────────────
    public Page<FactureResponse> getAll(Pageable pageable) {
        return factureRepository.findAll(pageable).map(this::toResponse);
    }

    public FactureResponse getById(Long id) {
        return toResponse(findFactureById(id));
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
        Facture facture = findFactureById(id);

        facture.setMontant(request.getMontant());
        facture.setDescription(request.getDescription());
        facture.setDateEcheance(request.getDateEcheance());

        if (request.getTypeFacture() != null)
            facture.setTypeFacture(Facture.TypeFacture.valueOf(request.getTypeFacture()));

        facture.setTypePaiement(parseTypePaiement(request.getTypePaiement()));

        if (request.getStatut() != null)
            facture.setStatut(Facture.Statut.valueOf(request.getStatut()));

        return toResponse(factureRepository.save(facture));
    }

    // ─── MARQUER PAYEE ────────────────────────────────────
    public FactureResponse marquerPayee(Long id) {
        Facture facture = findFactureById(id);
        facture.setStatut(Facture.Statut.PAYEE);
        facture.setDatePaiement(LocalDate.now());
        Facture saved = factureRepository.save(facture);

        // Notify Student
        notificationService.createNotification(
            saved.getEtudiant().getUser().getId(),
            "ETUDIANT",
            "Paiement validé",
            "Votre facture " + saved.getNumero() + " a été marquée comme payée.",
            "FACTURE",
            saved.getEtudiant().getEmail()
        );

        return toResponse(saved);
    }

    public FactureResponse confirmerPaiementEtudiant(
        Long factureId,
        String etudiantEmail,
        LocalDate datePaiement,
        MultipartFile image
    ) {
        Etudiant etudiant = etudiantRepository.findByEmail(etudiantEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        Facture facture = findFactureById(factureId);

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
        Facture saved = factureRepository.save(facture);

        // Notify Admin
        notificationService.createNotification(
            0L,
            "ADMIN",
            "Nouveau paiement à vérifier",
            "L'étudiant " + etudiant.getNom() + " " + etudiant.getPrenom() + " a envoyé un reçu pour la facture " + saved.getNumero(),
            "FACTURE_VERIFICATION",
            null
        );

        return toResponse(saved);
    }

    // ─── ANNULER ──────────────────────────────────────────
    public FactureResponse annuler(Long id) {
        Facture facture = findFactureById(id);
        facture.setStatut(Facture.Statut.ANNULEE);
        return toResponse(factureRepository.save(facture));
    }

    public FactureResponse cancel(Long id) {
        Facture facture = findFactureById(id);
        facture.setStatut(Facture.Statut.ANNULEE);
        return toResponse(factureRepository.save(facture));
    }

    public FactureResponse reject(Long id) {
        Facture facture = findFactureById(id);
        facture.setStatut(Facture.Statut.REJETEE);
        Facture saved = factureRepository.save(facture);

        // Notify Student
        notificationService.createNotification(
            saved.getEtudiant().getUser().getId(),
            "ETUDIANT",
            "Paiement rejeté",
            "La preuve de paiement pour la facture " + saved.getNumero() + " a été rejetée par l'administration. Veuillez vérifier votre reçu.",
            "FACTURE",
            saved.getEtudiant().getEmail()
        );

        return toResponse(saved);
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
        // Use count + random to avoid collisions in high-speed generation
        long count = factureRepository.count() + 1;
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return String.format("FAC-%d-%04d-%s", year, count, random);
    }

    private String generateNumero(long count) {
        int year = Year.now().getValue();
        String random = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        return String.format("FAC-%d-%04d-%s", year, count, random);
    }

    private Facture.TypeFacture parseTypeFacture(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return Facture.TypeFacture.SCOLARITE;
        }
        return Facture.TypeFacture.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
    }

    private Facture.TypePaiement parseTypePaiement(String rawTypePaiement) {
        if (rawTypePaiement == null || rawTypePaiement.isBlank()) {
            return Facture.TypePaiement.UNE_TRANCHE;
        }
        return Facture.TypePaiement.valueOf(rawTypePaiement.trim().toUpperCase(Locale.ROOT));
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

    public void generateSecondTranchesForEligibleInvoices(LocalDate targetDueDate) {
        // Find all "Tranche 1" invoices of type DEUX_TRANCHES
        // We look for those that don't have a Tranche 2 yet.
        List<Facture> tranche1s = factureRepository.findAll().stream()
            .filter(f -> f.getTypePaiement() == Facture.TypePaiement.DEUX_TRANCHES)
            .filter(f -> f.getDescription() != null && f.getDescription().contains("(Tranche 1)"))
            .toList();

        for (Facture f1 : tranche1s) {
            // Check if Tranche 2 already exists for this student and this description base
            String baseDesc = f1.getDescription().replace("(Tranche 1)", "").trim();
            boolean exists = factureRepository.findByEtudiantId(f1.getEtudiant().getId()).stream()
                .anyMatch(f -> f.getDescription() != null && f.getDescription().contains(baseDesc) && f.getDescription().contains("(Tranche 2)"));
            
            if (!exists) {
                Facture f2 = new Facture();
                f2.setNumero(generateNumero());
                f2.setMontant(f1.getMontant()); // 50/50
                f2.setDescription(baseDesc + " (Tranche 2)");
                f2.setDateEcheance(targetDueDate);
                f2.setEtudiant(f1.getEtudiant());
                f2.setStatut(Facture.Statut.NON_PAYEE);
                f2.setTypeFacture(f1.getTypeFacture());
                f2.setTypePaiement(Facture.TypePaiement.DEUX_TRANCHES);
                factureRepository.save(f2);
                System.out.println("✅ Généré Tranche 2 pour : " + f1.getEtudiant().getNom() + " (" + f2.getNumero() + ")");
                
                // Notify Student
                notificationService.createNotification(
                    f1.getEtudiant().getUser().getId(),
                    "ETUDIANT",
                    "Nouvelle facture générée",
                    "La 2ème tranche de votre facture (" + baseDesc + ") a été générée automatiquement.",
                    "FACTURE",
                    f1.getEtudiant().getEmail()
                );
            }
        }
    }

    private Facture findFactureById(Long id) {
        return factureRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée"));
    }

    // ─── MAPPER ───────────────────────────────────────────
    private FactureResponse toResponse(Facture f) {
        return FactureResponse.builder()
            .id(f.getId())
            .numero(f.getNumero())
            .montant(f.getMontant())
            .statut(f.getStatut().name())
            .typeFacture(f.getTypeFacture() != null ? f.getTypeFacture().name() : null)
            .typePaiement(f.getTypePaiement() != null ? f.getTypePaiement().name() : null)
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