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
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DemandeDocumentService {

    private final DemandeDocumentRepository demandeRepository;
    private final EtudiantRepository etudiantRepository;
    private final EnseignantRepository enseignantRepository;
    private final ValidationEnseignantRepository validationRepository;

    private static final DateTimeFormatter DATE_FORMAT =
        DateTimeFormatter.ofPattern("dd/MM/yyyy");

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
            }
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
        } else if (unRejete) {
            demande.setStatut(DemandeDocument.Statut.REJETEE);
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
        return toResponse(demandeRepository.save(demande));
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
            Document doc = new Document(PageSize.A4, 80, 80, 100, 80);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "ATTESTATION DE PRÉSENCE");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 12);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(30);
            p.setLeading(22);
            p.add(new Chunk("Je soussigné(e), le Directeur de l'établissement, atteste que l'étudiant(e) :\n\n", bodyFont));
            p.add(new Chunk("Nom et Prénom : ", boldFont));
            p.add(new Chunk(e.getNom() + " " + e.getPrenom() + "\n", bodyFont));
            p.add(new Chunk("Matricule : ", boldFont));
            p.add(new Chunk(e.getMatricule() + "\n", bodyFont));
            p.add(new Chunk("Date de naissance : ", boldFont));
            p.add(new Chunk((e.getDateNaissance() != null ? e.getDateNaissance().toString() : "-") + "\n\n", bodyFont));
            p.add(new Chunk("est régulièrement inscrit(e) et présent(e) au sein de notre établissement ", bodyFont));
            p.add(new Chunk("pour l'année académique en cours.\n\n", bodyFont));
            p.add(new Chunk("Cette attestation est délivrée à l'intéressé(e) pour servir et valoir ce que de droit.\n", bodyFont));
            doc.add(p);

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
            Document doc = new Document(PageSize.A4, 80, 80, 100, 80);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "RELEVÉ DE NOTES");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 12);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(30);
            p.setLeading(22);
            p.add(new Chunk("Le présent relevé de notes est établi pour l'étudiant(e) :\n\n", bodyFont));
            p.add(new Chunk("Nom et Prénom : ", boldFont));
            p.add(new Chunk(e.getNom() + " " + e.getPrenom() + "\n", bodyFont));
            p.add(new Chunk("Matricule : ", boldFont));
            p.add(new Chunk(e.getMatricule() + "\n\n", bodyFont));
            p.add(new Chunk("Ce document officiel certifie les résultats académiques ", bodyFont));
            p.add(new Chunk("obtenus au cours de l'année universitaire en cours.\n", bodyFont));
            doc.add(p);

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
            Document doc = new Document(PageSize.A4, 80, 80, 100, 80);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "ATTESTATION DE PAIEMENT");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 12);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(30);
            p.setLeading(22);
            p.add(new Chunk("Nous attestons que l'étudiant(e) :\n\n", bodyFont));
            p.add(new Chunk("Nom et Prénom : ", boldFont));
            p.add(new Chunk(e.getNom() + " " + e.getPrenom() + "\n", bodyFont));
            p.add(new Chunk("Matricule : ", boldFont));
            p.add(new Chunk(e.getMatricule() + "\n\n", bodyFont));
            p.add(new Chunk("a bien effectué le règlement de ses frais de scolarité ", bodyFont));
            p.add(new Chunk("pour l'année universitaire en cours.\n", bodyFont));
            doc.add(p);

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
            Document doc = new Document(PageSize.A4, 80, 80, 100, 80);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "DEMANDE DE STAGE");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 12);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(30);
            p.setLeading(22);
            p.add(new Chunk("L'établissement certifie que l'étudiant(e) :\n\n", bodyFont));
            p.add(new Chunk("Nom et Prénom : ", boldFont));
            p.add(new Chunk(e.getNom() + " " + e.getPrenom() + "\n", bodyFont));
            p.add(new Chunk("Matricule : ", boldFont));
            p.add(new Chunk(e.getMatricule() + "\n\n", bodyFont));
            p.add(new Chunk("est autorisé(e) à effectuer un stage au sein de l'entreprise :\n\n", bodyFont));
            if (d.getNomEntrepriseStage() != null) {
                p.add(new Chunk("Entreprise : ", boldFont));
                p.add(new Chunk(d.getNomEntrepriseStage() + "\n", bodyFont));
            }
            if (d.getAdresseEntreprise() != null) {
                p.add(new Chunk("Adresse : ", boldFont));
                p.add(new Chunk(d.getAdresseEntreprise() + "\n", bodyFont));
            }
            if (d.getNomEncadrant() != null) {
                p.add(new Chunk("Encadrant : ", boldFont));
                p.add(new Chunk(d.getNomEncadrant() + "\n", bodyFont));
            }
            doc.add(p);

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
            Document doc = new Document(PageSize.A4, 80, 80, 100, 80);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "ATTESTATION DE VALIDATION DE STAGE");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 12);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(30);
            p.setLeading(22);
            p.add(new Chunk("Nous soussignés, attestons que l'étudiant(e) :\n\n", bodyFont));
            p.add(new Chunk("Nom et Prénom : ", boldFont));
            p.add(new Chunk(e.getNom() + " " + e.getPrenom() + "\n", bodyFont));
            p.add(new Chunk("Matricule : ", boldFont));
            p.add(new Chunk(e.getMatricule() + "\n\n", bodyFont));
            p.add(new Chunk("a effectué et validé avec succès son stage de fin d'études au sein de :\n\n", bodyFont));
            if (d.getNomEntrepriseStage() != null) {
                p.add(new Chunk("Entreprise : ", boldFont));
                p.add(new Chunk(d.getNomEntrepriseStage() + "\n", bodyFont));
            }
            if (d.getNomEncadrant() != null) {
                p.add(new Chunk("Encadrant entreprise : ", boldFont));
                p.add(new Chunk(d.getNomEncadrant() + "\n", bodyFont));
            }
            doc.add(p);

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
            Document doc = new Document(PageSize.A4, 80, 80, 100, 80);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "ATTESTATION DE RÉUSSITE");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 12);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(30);
            p.setLeading(22);
            p.add(new Chunk("Le Directeur de l'établissement atteste que l'étudiant(e) :\n\n", bodyFont));
            p.add(new Chunk("Nom et Prénom : ", boldFont));
            p.add(new Chunk(e.getNom() + " " + e.getPrenom() + "\n", bodyFont));
            p.add(new Chunk("Matricule : ", boldFont));
            p.add(new Chunk(e.getMatricule() + "\n\n", bodyFont));
            p.add(new Chunk("a satisfait aux épreuves de fin d'année universitaire ", bodyFont));
            p.add(new Chunk("et a obtenu son diplôme avec succès.\n\n", bodyFont));
            p.add(new Chunk("Cette attestation lui est délivrée pour servir et valoir ce que de droit.\n", bodyFont));
            doc.add(p);

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
            Document doc = new Document(PageSize.A4, 80, 80, 100, 80);
            PdfWriter.getInstance(doc, out);
            doc.open();
            addEntete(doc, "ATTESTATION D'AFFECTATION");

            Font bodyFont = new Font(Font.FontFamily.HELVETICA, 12);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);
            Etudiant e = d.getEtudiant();
            String date = d.getCreatedAt().format(DATE_FORMAT);

            Paragraph p = new Paragraph();
            p.setSpacingBefore(30);
            p.setLeading(22);
            p.add(new Chunk("Nous attestons que l'étudiant(e) :\n\n", bodyFont));
            p.add(new Chunk("Nom et Prénom : ", boldFont));
            p.add(new Chunk(e.getNom() + " " + e.getPrenom() + "\n", bodyFont));
            p.add(new Chunk("Matricule : ", boldFont));
            p.add(new Chunk(e.getMatricule() + "\n\n", bodyFont));
            p.add(new Chunk("a été régulièrement affecté(e) au sein de notre établissement ", bodyFont));
            p.add(new Chunk("pour l'année universitaire en cours.\n\n", bodyFont));
            p.add(new Chunk("Cette attestation est délivrée à sa demande pour servir et valoir ce que de droit.\n", bodyFont));
            doc.add(p);

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
        Font etablissementFont = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD, new BaseColor(4, 41, 84));
        Font sousTitreFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.DARK_GRAY);

        Paragraph entete = new Paragraph();
        entete.setAlignment(Element.ALIGN_CENTER);
        entete.add(new Chunk("MINISTÈRE DE L'ENSEIGNEMENT SUPÉRIEUR\n", sousTitreFont));
        entete.add(new Chunk("ÉTABLISSEMENT ACADÉMIQUE\n", etablissementFont));
        entete.add(new Chunk("Tunis, Tunisie\n", sousTitreFont));
        doc.add(entete);

        Font sepFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, new BaseColor(4, 41, 84));
        Paragraph sep1 = new Paragraph("________________________________________________", sepFont);
        sep1.setAlignment(Element.ALIGN_CENTER);
        doc.add(sep1);

        Font titreFont = new Font(Font.FontFamily.HELVETICA, 16, Font.BOLD, new BaseColor(4, 41, 84));
        Paragraph titrePara = new Paragraph(titre, titreFont);
        titrePara.setAlignment(Element.ALIGN_CENTER);
        titrePara.setSpacingBefore(15);
        titrePara.setSpacingAfter(10);
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
        titre.setSpacingBefore(20);
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
        signature.setSpacingBefore(40);
        signature.setAlignment(Element.ALIGN_RIGHT);
        signature.add(new Chunk("Fait à Tunis, le " + date + "\n\n", normalFont));
        signature.add(new Chunk("Le Directeur\n", boldFont));
        signature.add(new Chunk("(Cachet et Signature)\n\n\n", normalFont));
        signature.add(new Chunk("_______________________", normalFont));
        doc.add(signature);
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