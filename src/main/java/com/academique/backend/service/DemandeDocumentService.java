package com.academique.backend.service;

import com.academique.backend.dto.request.DemandeDocumentRequest;
import com.academique.backend.dto.response.DemandeDocumentResponse;
import com.academique.backend.entity.*;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.*;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandeDocumentService {

    private final DemandeDocumentRepository demandeRepository;
    private final EtudiantRepository etudiantRepository;
    private final NoteRepository noteRepository;
    private final EnseignantRepository enseignantRepository;
    private final ValidationEnseignantRepository validationRepository;
    private final NotificationService notificationService;

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final float DOC_MARGIN_LEFT = 50f;
    private static final float DOC_MARGIN_RIGHT = 50f;
    private static final float DOC_MARGIN_TOP = 70f;
    private static final float DOC_MARGIN_BOTTOM = 50f;
    private static final float BODY_FONT_SIZE = 11f;

    // ─── ÉTUDIANT CRÉE UNE DEMANDE ────────────────────────
    public DemandeDocumentResponse create(DemandeDocumentRequest request, String etudiantEmail) {
        Etudiant etudiant = etudiantRepository.findByEmail(etudiantEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        DemandeDocument demande = new DemandeDocument();
        demande.setTypeDocument(DemandeDocument.TypeDocument.valueOf(request.getTypeDocument()));
        demande.setMotif(request.getMotif());
        demande.setNomEntrepriseStage(request.getNomEntrepriseStage());
        demande.setAdresseEntreprise(request.getAdresseEntreprise());
        demande.setNomEncadrant(request.getNomEncadrant());
        demande.setEtudiant(etudiant);
        demande.setStatut(DemandeDocument.Statut.EN_ATTENTE);

        if (request.getTypeDocument().equals("ATTESTATION_PRESENCE") &&
            request.getValidateursIds() != null &&
            request.getValidateursIds().size() == 2) {

            List<Enseignant> validateurs = new ArrayList<>();
            for (Long id : request.getValidateursIds()) {
                Enseignant ens = enseignantRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
                validateurs.add(ens);
            }
            demande.setValidateurs(validateurs);
            demande.setStatut(DemandeDocument.Statut.EN_COURS_VALIDATION);
        }

        DemandeDocument saved = demandeRepository.save(demande);

        if (!demande.getValidateurs().isEmpty()) {
            for (Enseignant ens : demande.getValidateurs()) {
                ValidationEnseignant validation = new ValidationEnseignant();
                validation.setDemande(saved);
                validation.setEnseignant(ens);
                validation.setStatut(ValidationEnseignant.Statut.EN_ATTENTE);
                validationRepository.save(validation);

                // Notify Teacher
                notificationService.createNotification(
                    ens.getUser().getId(),
                    "ENSEIGNANT",
                    "Nouveau document à valider",
                    "L'étudiant " + etudiant.getNom() + " " + etudiant.getPrenom() + " a sollicité votre signature pour une attestation.",
                    "DOCUMENT",
                    ens.getEmail()
                );
            }
        } else {
            // Notify Admin (In-app only)
            notificationService.createNotification(
                0L,
                "ADMIN",
                "Nouveau document à valider",
                "Une nouvelle demande de document (" + saved.getTypeDocument() + ") a été soumise par " + etudiant.getNom() + " " + etudiant.getPrenom(),
                "DOCUMENT",
                null
            );
        }

        return toResponse(saved);
    }

    // ─── READ ALL ──────────────────────────────────────────
    public Page<DemandeDocumentResponse> getAll(Pageable pageable) {
        return demandeRepository.findAll(pageable).map(this::toResponse);
    }

    // ─── READ BY ID ────────────────────────────────────────
    public DemandeDocumentResponse getById(Long id) {
        return toResponse(demandeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée")));
    }

    // ─── READ BY ETUDIANT EMAIL ───────────────────────────
    public List<DemandeDocumentResponse> getByEtudiantEmail(String email) {
        Etudiant etudiant = etudiantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        return demandeRepository.findByEtudiantId(etudiant.getId())
            .stream().map(this::toResponse).toList();
    }

    // ─── READ BY ENSEIGNANT EMAIL ─────────────────────────
    public List<DemandeDocumentResponse> getByEnseignantEmail(String email) {
        Enseignant enseignant = enseignantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
        return demandeRepository.findByValidateursId(enseignant.getId())
            .stream().map(d -> toResponse(d, enseignant.getId())).toList();
    }

    // ─── ENSEIGNANT VALIDE ────────────────────────────────
    public DemandeDocumentResponse validerParEnseignant(
            Long demandeId, String enseignantEmail,
            boolean valide, String commentaire) {

        Enseignant enseignant = enseignantRepository.findByEmail(enseignantEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));

        ValidationEnseignant validation = validationRepository
            .findByDemandeIdAndEnseignantId(demandeId, enseignant.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Validation non trouvée"));

        validation.setStatut(valide ?
            ValidationEnseignant.Statut.VALIDE : ValidationEnseignant.Statut.REJETE);
        validation.setCommentaire(commentaire);
        validation.setDateValidation(LocalDateTime.now());
        validationRepository.save(validation);

        DemandeDocument demande = demandeRepository.findById(demandeId)
            .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée"));

        List<ValidationEnseignant> validations = validationRepository.findByDemandeId(demandeId);
        boolean tousValides = validations.stream()
            .allMatch(v -> v.getStatut() == ValidationEnseignant.Statut.VALIDE);
        boolean unRejete = validations.stream()
            .anyMatch(v -> v.getStatut() == ValidationEnseignant.Statut.REJETE);

        if (tousValides) {
            demande.setStatut(DemandeDocument.Statut.VALIDEE);
            // Notify student
            notificationService.createNotification(
                demande.getEtudiant().getUser().getId(),
                "ETUDIANT",
                "Document validé par les enseignants",
                "Votre demande de document " + demande.getTypeDocument() + " a été validée par tous les enseignants requis.",
                "DOCUMENT",
                demande.getEtudiant().getEmail()
            );
        } else if (unRejete) {
            demande.setStatut(DemandeDocument.Statut.REJETEE);
            // Notify student
            notificationService.createNotification(
                demande.getEtudiant().getUser().getId(),
                "ETUDIANT",
                "Document rejeté par un enseignant",
                "Votre demande de document " + demande.getTypeDocument() + " a été rejetée par l'un des enseignants.",
                "DOCUMENT",
                demande.getEtudiant().getEmail()
            );
        }

        return toResponse(demandeRepository.save(demande));
    }

    // ─── ADMIN VALIDE ─────────────────────────────────────
    public DemandeDocumentResponse validerParAdmin(Long id, boolean valide, String commentaire) {
        DemandeDocument demande = demandeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée"));
        demande.setStatut(valide ?
            DemandeDocument.Statut.VALIDEE : DemandeDocument.Statut.REJETEE);
        demande.setCommentaireAdmin(commentaire);
        DemandeDocument saved = demandeRepository.save(demande);

        // Notify Student
        notificationService.createNotification(
            saved.getEtudiant().getUser().getId(),
            "ETUDIANT",
            valide ? "Document validé par l'admin" : "Document rejeté par l'admin",
            "Votre demande de document " + saved.getTypeDocument() + " a été " + (valide ? "validée" : "rejetée") + " par l'administration.",
            "DOCUMENT",
            saved.getEtudiant().getEmail()
        );

        return toResponse(saved);
    }

    // ─── ADMIN MARQUE ENVOYÉE ─────────────────────────────
    public DemandeDocumentResponse marquerEnvoyee(Long id) {
        DemandeDocument demande = demandeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée"));
        demande.setStatut(DemandeDocument.Statut.ENVOYEE);
        return toResponse(demandeRepository.save(demande));
    }

    // ─── DELETE ───────────────────────────────────────────
    public void delete(Long id) {
        if (!demandeRepository.existsById(id))
            throw new ResourceNotFoundException("Demande non trouvée");
        demandeRepository.deleteById(id);
    }

    // ─── STATS ────────────────────────────────────────────
    public Map<String, Object> getStats() {
        return Map.of(
            "enAttente", demandeRepository.countByStatut(DemandeDocument.Statut.EN_ATTENTE),
            "enCoursValidation", demandeRepository.countByStatut(DemandeDocument.Statut.EN_COURS_VALIDATION),
            "validees", demandeRepository.countByStatut(DemandeDocument.Statut.VALIDEE),
            "rejetees", demandeRepository.countByStatut(DemandeDocument.Statut.REJETEE),
            "envoyees", demandeRepository.countByStatut(DemandeDocument.Statut.ENVOYEE)
        );
    }

    // ─── GÉNÉRATION PDF ───────────────────────────────────
    @Transactional(readOnly = true)
    public byte[] genererPdf(Long id) {
        DemandeDocument demande = demandeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Demande non trouvée"));

        if (demande.getStatut() != DemandeDocument.Statut.VALIDEE &&
            demande.getStatut() != DemandeDocument.Statut.ENVOYEE) {
            throw new RuntimeException("Document non encore validé");
        }

        return switch (demande.getTypeDocument()) {
            case ATTESTATION_PRESENCE -> genererAttestationPresence(demande);
            case RELEVE_NOTES -> genererReleveNotes(demande);
            case FACTURE_PAIEMENT -> genererFacturePaiement(demande);
            case DEMANDE_STAGE -> genererDemandeStage(demande);
            case VALIDATION_STAGE -> genererValidationStage(demande);
            case ATTESTATION_REUSSITE -> genererAttestationReussite(demande);
            case ATTESTATION_AFFECTATION -> genererAttestationAffectation(demande);
        };
    }

    // ════════════════════════════════════════════════════════
    // PDF GENERATORS
    // ════════════════════════════════════════════════════════

    private byte[] genererAttestationPresence(DemandeDocument d) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, DOC_MARGIN_LEFT, DOC_MARGIN_RIGHT, DOC_MARGIN_TOP, DOC_MARGIN_BOTTOM);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "ATTESTATION DE PRÉSENCE");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, BODY_FONT_SIZE);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(18);
            p.setLeading(18);
            p.add(new Chunk("Je soussigné(e), le Directeur de l'établissement, atteste que l'étudiant(e) :\n\n", bodyFont));
            doc.add(p);

            doc.add(buildEtudiantInfoTable(e));

            Paragraph p2 = new Paragraph();
            p2.setSpacingBefore(2);
            p2.setLeading(18);
            p2.add(new Chunk("est régulièrement inscrit(e) et présent(e) au sein de notre établissement ", bodyFont));
            p2.add(new Chunk("pour l'année académique en cours.\n\n", bodyFont));
            p2.add(new Chunk("Cette attestation est délivrée à l'intéressé(e) pour servir et valoir ce que de droit.\n", bodyFont));
            doc.add(p2);

            addValidateursSignatures(doc, d);
            addSignatureAdmin(doc, date);
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Erreur génération PDF", ex);
        }
    }

    private byte[] genererReleveNotes(DemandeDocument d) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, DOC_MARGIN_LEFT, DOC_MARGIN_RIGHT, DOC_MARGIN_TOP, DOC_MARGIN_BOTTOM);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "RELEVÉ DE NOTES");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, BODY_FONT_SIZE);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(18);
            p.setLeading(18);
            p.add(new Chunk("Le présent relevé de notes est établi pour l'étudiant(e) :\n\n", bodyFont));
            doc.add(p);

            doc.add(buildEtudiantInfoTable(e));

            List<Note> notes = noteRepository.findByEtudiantId(e.getId());
            List<MatiereBulletinRow> rows = buildMatiereRows(notes);
            Map<Note.Semestre, List<MatiereBulletinRow>> rowsBySemestre = groupRowsBySemestre(rows);

            if (rowsBySemestre.isEmpty()) {
                Paragraph empty = new Paragraph("Aucune note disponible pour le moment.", bodyFont);
                empty.setSpacingBefore(10);
                doc.add(empty);
            } else {
                Font sectionFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, new BaseColor(4, 41, 84));
                Font dataFont = new Font(Font.FontFamily.HELVETICA, 9);

                List<Double> semestreMoyennes = new ArrayList<>();

                for (Map.Entry<Note.Semestre, List<MatiereBulletinRow>> entry : rowsBySemestre.entrySet()) {
                    String semestreLabel = entry.getKey() != null ? entry.getKey().name() : "-";
                    Paragraph semestreTitle = new Paragraph("Semestre " + semestreLabel, sectionFont);
                    semestreTitle.setSpacingBefore(8);
                    semestreTitle.setSpacingAfter(6);
                    doc.add(semestreTitle);

                    PdfPTable table = new PdfPTable(6);
                    table.setWidthPercentage(100);
                    table.setWidths(new float[]{4.2f, 1.2f, 1.2f, 1.2f, 1.4f, 1.8f});

                    Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
                    String[] headers = {"Matière", "Coeff", "DS", "TP", "EXAMEN", "Moyenne"};
                    for (String h : headers) {
                        PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                        cell.setBackgroundColor(new BaseColor(4, 41, 84));
                        cell.setPadding(7);
                        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        table.addCell(cell);
                    }

                    boolean alternate = false;
                    for (MatiereBulletinRow row : entry.getValue()) {
                        BaseColor rowColor = alternate ? new BaseColor(245, 245, 245) : BaseColor.WHITE;
                        String[] values = {
                            row.matiereNom,
                            String.format("%.2f", row.coefficient),
                            row.ds == null ? "-" : String.format("%.2f", row.ds),
                            row.tp == null ? "-" : String.format("%.2f", row.tp),
                            row.examen == null ? "-" : String.format("%.2f", row.examen),
                            String.format("%.2f", row.moyenne)
                        };

                        for (String value : values) {
                            PdfPCell cell = new PdfPCell(new Phrase(value, dataFont));
                            cell.setBackgroundColor(rowColor);
                            cell.setPadding(6);
                            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                            table.addCell(cell);
                        }
                        alternate = !alternate;
                    }

                    double semestreMoyenne = computeSimpleAverage(entry.getValue());
                    PdfPCell moyenneLabel = new PdfPCell(new Phrase("Moyenne Semestre", new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD)));
                    moyenneLabel.setColspan(5);
                    moyenneLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
                    moyenneLabel.setPadding(6);
                    table.addCell(moyenneLabel);

                    PdfPCell moyenneValue = new PdfPCell(new Phrase(String.format("%.2f", semestreMoyenne), new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD)));
                    moyenneValue.setHorizontalAlignment(Element.ALIGN_CENTER);
                    moyenneValue.setPadding(6);
                    table.addCell(moyenneValue);

                    doc.add(table);
                    semestreMoyennes.add(semestreMoyenne);
                }

                double moyenneSemestres = semestreMoyennes.isEmpty()
                    ? 0.0
                    : semestreMoyennes.stream().mapToDouble(Double::doubleValue).sum() / semestreMoyennes.size();

                Font moyenneFont = new Font(
                    Font.FontFamily.HELVETICA,
                    13,
                    Font.BOLD,
                    moyenneSemestres >= 10 ? new BaseColor(27, 94, 32) : new BaseColor(183, 28, 28)
                );
                Paragraph moyennePara = new Paragraph(
                    String.format("\nMoyenne des semestres : %.2f / 20", moyenneSemestres),
                    moyenneFont
                );
                moyennePara.setAlignment(Element.ALIGN_RIGHT);
                moyennePara.setSpacingBefore(12);
                doc.add(moyennePara);
            }

            addSignatureAdmin(doc, date);
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Erreur génération PDF", ex);
        }
    }

    private byte[] genererFacturePaiement(DemandeDocument d) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, DOC_MARGIN_LEFT, DOC_MARGIN_RIGHT, DOC_MARGIN_TOP, DOC_MARGIN_BOTTOM);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "ATTESTATION DE PAIEMENT");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, BODY_FONT_SIZE);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(18);
            p.setLeading(18);
            p.add(new Chunk("Nous attestons que l'étudiant(e) :\n\n", bodyFont));
            doc.add(p);

            doc.add(buildEtudiantInfoTable(e));

            Paragraph p2 = new Paragraph();
            p2.setSpacingBefore(2);
            p2.setLeading(18);
            p2.add(new Chunk("a bien effectué le règlement de ses frais de scolarité ", bodyFont));
            p2.add(new Chunk("pour l'année universitaire en cours.\n", bodyFont));
            doc.add(p2);

            addSignatureAdmin(doc, date);
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Erreur génération PDF", ex);
        }
    }

    private byte[] genererDemandeStage(DemandeDocument d) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, DOC_MARGIN_LEFT, DOC_MARGIN_RIGHT, DOC_MARGIN_TOP, DOC_MARGIN_BOTTOM);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "DEMANDE DE STAGE");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, BODY_FONT_SIZE);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, BODY_FONT_SIZE, Font.BOLD);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(18);
            p.setLeading(18);
            p.add(new Chunk("L'établissement certifie que l'étudiant(e) :\n\n", bodyFont));
            doc.add(p);

            doc.add(buildEtudiantInfoTable(e));

            Paragraph p2 = new Paragraph();
            p2.setSpacingBefore(2);
            p2.setLeading(18);
            p2.add(new Chunk("est autorisé(e) à effectuer un stage au sein de l'entreprise :\n\n", bodyFont));
            if (d.getNomEntrepriseStage() != null) {
                p2.add(new Chunk("Entreprise : ", boldFont));
                p2.add(new Chunk(d.getNomEntrepriseStage() + "\n", bodyFont));
            }
            if (d.getAdresseEntreprise() != null) {
                p2.add(new Chunk("Adresse : ", boldFont));
                p2.add(new Chunk(d.getAdresseEntreprise() + "\n", bodyFont));
            }
            if (d.getNomEncadrant() != null) {
                p2.add(new Chunk("Encadrant : ", boldFont));
                p2.add(new Chunk(d.getNomEncadrant() + "\n", bodyFont));
            }
            doc.add(p2);

            addSignatureAdmin(doc, date);
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Erreur génération PDF", ex);
        }
    }

    private byte[] genererValidationStage(DemandeDocument d) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, DOC_MARGIN_LEFT, DOC_MARGIN_RIGHT, DOC_MARGIN_TOP, DOC_MARGIN_BOTTOM);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "ATTESTATION DE VALIDATION DE STAGE");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, BODY_FONT_SIZE);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, BODY_FONT_SIZE, Font.BOLD);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(18);
            p.setLeading(18);
            p.add(new Chunk("Nous soussignés, attestons que l'étudiant(e) :\n\n", bodyFont));
            doc.add(p);

            doc.add(buildEtudiantInfoTable(e));

            Paragraph p2 = new Paragraph();
            p2.setSpacingBefore(2);
            p2.setLeading(18);
            p2.add(new Chunk("a effectué et validé avec succès son stage de fin d'études au sein de :\n\n", bodyFont));
            if (d.getNomEntrepriseStage() != null) {
                p2.add(new Chunk("Entreprise : ", boldFont));
                p2.add(new Chunk(d.getNomEntrepriseStage() + "\n", bodyFont));
            }
            if (d.getNomEncadrant() != null) {
                p2.add(new Chunk("Encadrant entreprise : ", boldFont));
                p2.add(new Chunk(d.getNomEncadrant() + "\n", bodyFont));
            }
            doc.add(p2);

            addSignatureAdmin(doc, date);
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Erreur génération PDF", ex);
        }
    }

    private byte[] genererAttestationReussite(DemandeDocument d) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, DOC_MARGIN_LEFT, DOC_MARGIN_RIGHT, DOC_MARGIN_TOP, DOC_MARGIN_BOTTOM);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "ATTESTATION DE RÉUSSITE");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, BODY_FONT_SIZE);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(18);
            p.setLeading(18);
            p.add(new Chunk("Le Directeur de l'établissement atteste que l'étudiant(e) :\n\n", bodyFont));
            doc.add(p);

            doc.add(buildEtudiantInfoTable(e));

            Paragraph p2 = new Paragraph();
            p2.setSpacingBefore(2);
            p2.setLeading(18);
            p2.add(new Chunk("a satisfait aux épreuves de fin d'année universitaire ", bodyFont));
            p2.add(new Chunk("et a obtenu son diplôme avec succès.\n\n", bodyFont));
            p2.add(new Chunk("Cette attestation lui est délivrée pour servir et valoir ce que de droit.\n", bodyFont));
            doc.add(p2);

            addSignatureAdmin(doc, date);
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Erreur génération PDF", ex);
        }
    }

    private byte[] genererAttestationAffectation(DemandeDocument d) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, DOC_MARGIN_LEFT, DOC_MARGIN_RIGHT, DOC_MARGIN_TOP, DOC_MARGIN_BOTTOM);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "ATTESTATION D'AFFECTATION");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, BODY_FONT_SIZE);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(18);
            p.setLeading(18);
            p.add(new Chunk("Nous attestons que l'étudiant(e) :\n\n", bodyFont));
            doc.add(p);

            doc.add(buildEtudiantInfoTable(e));

            Paragraph p2 = new Paragraph();
            p2.setSpacingBefore(2);
            p2.setLeading(18);
            p2.add(new Chunk("a été régulièrement affecté(e) au sein de notre établissement ", bodyFont));
            p2.add(new Chunk("pour l'année universitaire en cours.\n\n", bodyFont));
            p2.add(new Chunk("Cette attestation est délivrée à sa demande pour servir et valoir ce que de droit.\n", bodyFont));
            doc.add(p2);

            addSignatureAdmin(doc, date);
            doc.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new RuntimeException("Erreur génération PDF", ex);
        }
    }

    // ════════════════════════════════════════════════════════
    // HELPERS PDF
    // ════════════════════════════════════════════════════════

    private void addEntete(Document doc, String titre) throws DocumentException {
        Font etablissementFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD, new BaseColor(4, 41, 84));
        Font sousTitreFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, BaseColor.DARK_GRAY);

        Paragraph entete = new Paragraph();
        entete.setAlignment(Element.ALIGN_CENTER);
        entete.add(new Chunk("MINISTÈRE DE L'ENSEIGNEMENT SUPÉRIEUR\n", sousTitreFont));
        entete.add(new Chunk("ÉTABLISSEMENT ACADÉMIQUE\n", etablissementFont));
        entete.add(new Chunk("Tunis, Tunisie\n", sousTitreFont));
        doc.add(entete);

        Font sepFont = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, new BaseColor(4, 41, 84));
        Paragraph sep1 = new Paragraph("________________________________________________", sepFont);
        sep1.setAlignment(Element.ALIGN_CENTER);
        doc.add(sep1);

        Font titreFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD, new BaseColor(4, 41, 84));
        Paragraph titrePara = new Paragraph(titre, titreFont);
        titrePara.setAlignment(Element.ALIGN_CENTER);
        titrePara.setSpacingBefore(10);
        titrePara.setSpacingAfter(6);
        doc.add(titrePara);

        Paragraph sep2 = new Paragraph("________________________________________________", sepFont);
        sep2.setAlignment(Element.ALIGN_CENTER);
        doc.add(sep2);
    }

    private void addValidateursSignatures(Document doc, DemandeDocument d) throws DocumentException {
        if (d.getValidateurs() == null || d.getValidateurs().isEmpty()) return;

        Font boldFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD);
        Font normalFont = new Font(Font.FontFamily.HELVETICA, 11);

        Paragraph titre = new Paragraph("\nValidations des enseignants :\n", boldFont);
        titre.setSpacingBefore(10);
        doc.add(titre);

        List<ValidationEnseignant> validations = validationRepository.findByDemandeId(d.getId());

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3f, 2f, 3f});

        String[] headers = {"Enseignant", "Statut", "Commentaire"};
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
            cell.setBackgroundColor(new BaseColor(4, 41, 84));
            cell.setPadding(6);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }

        for (ValidationEnseignant v : validations) {
            table.addCell(new PdfPCell(new Phrase(
                v.getEnseignant().getNom() + " " + v.getEnseignant().getPrenom(), normalFont)));
            table.addCell(new PdfPCell(new Phrase(v.getStatut().name(), normalFont)));
            table.addCell(new PdfPCell(new Phrase(
                v.getCommentaire() != null ? v.getCommentaire() : "-", normalFont)));
        }
        doc.add(table);
    }

    private void addSignatureAdmin(Document doc, String date) throws DocumentException {
        Font normalFont = new Font(Font.FontFamily.HELVETICA, 11);
        Font boldFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD);

        Paragraph signature = new Paragraph();
        signature.setSpacingBefore(24);
        signature.setAlignment(Element.ALIGN_RIGHT);
        signature.add(new Chunk("Fait à Tunis, le " + date + "\n\n", normalFont));
        signature.add(new Chunk("Le Directeur\n", boldFont));
        signature.add(new Chunk("(Cachet et Signature)\n\n\n", normalFont));
        signature.add(new Chunk("_______________________", normalFont));
        doc.add(signature);
    }

    private PdfPTable buildEtudiantInfoTable(Etudiant etudiant) {
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setSpacingAfter(6);

        Classe classe = etudiant.getClasse();
        Niveau niveau = classe != null ? classe.getNiveau() : null;
        Filiere filiere = niveau != null ? niveau.getFiliere() : null;

        addInfoCell(infoTable, "Nom et Prénom :", formatNomPrenom(etudiant.getPrenom(), etudiant.getNom()));
        addInfoCell(infoTable, "Matricule :", safeValue(etudiant.getMatricule()));
        addInfoCell(infoTable, "Email :", safeValue(etudiant.getEmail()));
        addInfoCell(infoTable, "Téléphone :", safeValue(etudiant.getTelephone()));
        addInfoCell(infoTable, "Date de naissance :", formatDate(etudiant.getDateNaissance()));
        addInfoCell(infoTable, "Adresse :", safeValue(etudiant.getAdresse()));
        addInfoCell(infoTable, "Statut :", etudiant.getStatut() != null ? etudiant.getStatut().name() : "-");
        addInfoCell(infoTable, "Classe :", formatCodeNom(classe != null ? classe.getCode() : null, classe != null ? classe.getNom() : null));
        addInfoCell(infoTable, "Niveau :", formatCodeNom(niveau != null ? niveau.getCode() : null, niveau != null ? niveau.getNom() : null));
        addInfoCell(infoTable, "Filière :", formatCodeNom(filiere != null ? filiere.getCode() : null, filiere != null ? filiere.getNom() : null));
        addInfoCell(infoTable, "Date d'inscription :", formatDateTime(etudiant.getCreatedAt()));

        return infoTable;
    }

    private void addInfoCell(PdfPTable table, String label, String value) {
        Font labelFont = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD);
        Font valueFont = new Font(Font.FontFamily.HELVETICA, 9);
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(4);
        valueCell.setPadding(4);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String formatNomPrenom(String prenom, String nom) {
        String p = prenom != null ? prenom.trim() : "";
        String n = nom != null ? nom.trim() : "";
        String full = (p + " " + n).trim();
        return full.isEmpty() ? "-" : full;
    }

    private String formatCodeNom(String code, String nom) {
        String c = code != null ? code.trim() : "";
        String n = nom != null ? nom.trim() : "";
        String full = c.isEmpty() ? n : n.isEmpty() ? c : c + " - " + n;
        return full.isEmpty() ? "-" : full;
    }

    private String safeValue(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMAT) : "-";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATE_TIME_FORMAT) : "-";
    }

    private List<MatiereBulletinRow> buildMatiereRows(List<Note> notes) {
        Map<String, MatiereBulletinAccumulator> map = new LinkedHashMap<>();

        for (Note note : notes) {
            if (note.getMatiere() == null) continue;

            String semestre = note.getSemestre() != null ? note.getSemestre().name() : "";
            String key = semestre + "::" + note.getMatiere().getId();
            MatiereBulletinAccumulator acc = map.computeIfAbsent(
                key,
                k -> new MatiereBulletinAccumulator(
                    note.getMatiere().getNom(),
                    note.getMatiere().getCoefficient() != null ? note.getMatiere().getCoefficient() : 1.0,
                    note.getSemestre()
                )
            );

            if (note.getValeur() == null || note.getTypeNote() == null) {
                continue;
            }

            switch (note.getTypeNote()) {
                case CONTROLE -> {
                    acc.dsSum += note.getValeur();
                    acc.dsCount++;
                }
                case TP -> {
                    acc.tpSum += note.getValeur();
                    acc.tpCount++;
                }
                case EXAMEN -> {
                    acc.examSum += note.getValeur();
                    acc.examCount++;
                }
                default -> {
                    // PROJET non utilisé dans la formule
                }
            }
        }

        return map.values().stream()
            .map(acc -> {
                Double ds = acc.dsCount > 0 ? acc.dsSum / acc.dsCount : null;
                Double tp = acc.tpCount > 0 ? acc.tpSum / acc.tpCount : null;
                Double exam = acc.examCount > 0 ? acc.examSum / acc.examCount : null;

                double moyenne = (exam == null ? 0.0 : exam * 0.7)
                    + (ds == null ? 0.0 : ds * 0.3);

                double moyenneRounded = Math.round(moyenne * 100.0) / 100.0;
                return new MatiereBulletinRow(acc.matiereNom, acc.coefficient, ds, tp, exam, moyenneRounded, acc.semestre);
            })
            .sorted(Comparator
                .comparingInt((MatiereBulletinRow r) -> semestreOrder(r.semestre))
                .thenComparing(r -> r.matiereNom))
            .toList();
    }

    private Map<Note.Semestre, List<MatiereBulletinRow>> groupRowsBySemestre(List<MatiereBulletinRow> rows) {
        Map<Note.Semestre, List<MatiereBulletinRow>> grouped = new LinkedHashMap<>();
        rows.stream()
            .sorted(Comparator
                .comparingInt((MatiereBulletinRow r) -> semestreOrder(r.semestre))
                .thenComparing(r -> r.matiereNom))
            .forEach(row -> grouped
                .computeIfAbsent(row.semestre, k -> new ArrayList<>())
                .add(row));
        return grouped;
    }

    private double computeSimpleAverage(List<MatiereBulletinRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0.0;
        }
        double total = 0.0;
        for (MatiereBulletinRow row : rows) {
            total += row.moyenne;
        }
        return total / rows.size();
    }

    private int semestreOrder(Note.Semestre semestre) {
        if (semestre == null) return 99;
        return switch (semestre) {
            case S1 -> 1;
            case S2 -> 2;
            case S3 -> 3;
            case S4 -> 4;
            case S5 -> 5;
        };
    }

    private static class MatiereBulletinAccumulator {
        private final String matiereNom;
        private final double coefficient;
        private final Note.Semestre semestre;
        private double dsSum = 0.0;
        private int dsCount = 0;
        private double tpSum = 0.0;
        private int tpCount = 0;
        private double examSum = 0.0;
        private int examCount = 0;

        private MatiereBulletinAccumulator(String matiereNom, double coefficient, Note.Semestre semestre) {
            this.matiereNom = matiereNom;
            this.coefficient = coefficient;
            this.semestre = semestre;
        }
    }

    private static class MatiereBulletinRow {
        private final String matiereNom;
        private final double coefficient;
        private final Double ds;
        private final Double tp;
        private final Double examen;
        private final double moyenne;
        private final Note.Semestre semestre;

        private MatiereBulletinRow(String matiereNom, double coefficient, Double ds, Double tp, Double examen, double moyenne, Note.Semestre semestre) {
            this.matiereNom = matiereNom;
            this.coefficient = coefficient;
            this.ds = ds;
            this.tp = tp;
            this.examen = examen;
            this.moyenne = moyenne;
            this.semestre = semestre;
        }
    }

    // ─── MAPPER ───────────────────────────────────────────
    private DemandeDocumentResponse toResponse(DemandeDocument d) {
        return toResponse(d, null);
    }

    private DemandeDocumentResponse toResponse(DemandeDocument d, Long enseignantId) {
        List<ValidationEnseignant> validations = validationRepository.findByDemandeId(d.getId());

        List<DemandeDocumentResponse.ValidateurInfo> validateursInfo = validations.stream()
            .map(v -> DemandeDocumentResponse.ValidateurInfo.builder()
                .enseignantId(v.getEnseignant().getId())
                .nom(v.getEnseignant().getNom())
                .prenom(v.getEnseignant().getPrenom())
                .statut(v.getStatut().name())
                .commentaire(v.getCommentaire())
                .build())
            .collect(Collectors.toList());

        long nombreValidations = validations.stream()
            .filter(v -> v.getStatut() == ValidationEnseignant.Statut.VALIDE)
            .count();

        boolean pretPourEnvoi = d.getStatut() == DemandeDocument.Statut.VALIDEE;

        String monStatut = null;
        if (enseignantId != null) {
            monStatut = validations.stream()
                .filter(v -> v.getEnseignant() != null && enseignantId.equals(v.getEnseignant().getId()))
                .map(v -> switch (v.getStatut()) {
                    case VALIDE -> "VALIDEE";
                    case REJETE -> "REJETEE";
                    case EN_ATTENTE -> "EN_ATTENTE";
                })
                .findFirst()
                .orElse("EN_ATTENTE");
        }

        return DemandeDocumentResponse.builder()
            .id(d.getId())
            .typeDocument(d.getTypeDocument().name())
            .statut(d.getStatut().name())
            .monStatut(monStatut)
            .motif(d.getMotif())
            .commentaireAdmin(d.getCommentaireAdmin())
            .nomEntrepriseStage(d.getNomEntrepriseStage())
            .adresseEntreprise(d.getAdresseEntreprise())
            .nomEncadrant(d.getNomEncadrant())
            .createdAt(d.getCreatedAt())
            .updatedAt(d.getUpdatedAt())
            .etudiantId(d.getEtudiant().getId())
            .etudiantNom(d.getEtudiant().getNom())
            .etudiantPrenom(d.getEtudiant().getPrenom())
            .etudiantMatricule(d.getEtudiant().getMatricule())
            .etudiantEmail(d.getEtudiant().getEmail())
            .validateurs(validateursInfo)
            .nombreValidations((int) nombreValidations)
            .pretPourEnvoi(pretPourEnvoi)
            .build();
    }
}