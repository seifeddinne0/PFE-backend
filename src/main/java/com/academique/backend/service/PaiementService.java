package com.academique.backend.service;

import com.academique.backend.dto.response.PaiementResponse;
import com.academique.backend.entity.*;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PaiementService {

    @PersistenceContext
    private EntityManager entityManager;

    private final PaiementRepository paiementRepository;
    private final FactureRepository factureRepository;
    private final EtudiantRepository etudiantRepository;
    private final CodesPaiementUtilisesRepository codesPaiementRepository;
    private final ConfigPaiementRepository configPaiementRepository;
    private final ReceiptAnalysisService receiptAnalysisService;

    @Value("${file.upload.path:uploads/receipts}")
    private String uploadPath;

    @Value("${file.max.size:5242880}")
    private long maxFileSize;

    @Transactional
    public PaiementResponse soumettrePaiement(Long factureId, Long etudiantId, MultipartFile receipt, String etudiantEmail) {
        Facture facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée"));

        Etudiant etudiant = etudiantRepository.findByEmail(etudiantEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        if (!etudiant.getId().equals(etudiantId)) {
            throw new IllegalArgumentException("Accès interdit à cette facture");
        }

        if (!facture.getEtudiant().getId().equals(etudiantId)) {
            throw new IllegalArgumentException("Cette facture ne vous appartient pas");
        }

        if (facture.getStatut() == Facture.Statut.PAYEE) {
            throw new IllegalArgumentException("Cette facture est déjà payée.");
        }

        if (receipt == null || receipt.isEmpty()) {
            throw new IllegalArgumentException("Veuillez joindre un reçu valide.");
        }

        if (receipt.getSize() > maxFileSize) {
            throw new IllegalArgumentException("Le fichier dépasse la taille maximale autorisée (5MB).");
        }

        String contentType = receipt.getContentType();
        if (contentType == null || !(contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("application/pdf"))) {
            throw new IllegalArgumentException("Format de fichier invalide. Utilisez JPG, PNG ou PDF.");
        }

        String extension = extractExtension(receipt.getOriginalFilename(), contentType);
        String fileName = "receipt_" + factureId + "_" + System.currentTimeMillis() + extension;
        Path uploadDir = Path.of(uploadPath);
        Path targetPath = uploadDir.resolve(fileName);

        try {
            Files.createDirectories(uploadDir);
            Files.copy(receipt.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new RuntimeException("Erreur lors de l'upload du reçu");
        }

        facture.setPreuvePaiement("/uploads/receipts/" + fileName);

        Paiement paiement = Paiement.builder()
            .facture(facture)
            .etudiant(etudiant)
            .statut(Paiement.Statut.EN_ATTENTE)
            .preuvePaiement("/uploads/receipts/" + fileName)
            .build();

        paiement = paiementRepository.save(paiement);

        ReceiptData extracted = receiptAnalysisService.analyze(receipt);

        ConfigPaiement config = configPaiementRepository.findByActifTrue()
            .orElseThrow(() -> new IllegalArgumentException("Configuration de paiement introuvable."));

        String officialRib = config.getRib().replaceAll("\\s", "");
        String extractedRib = extracted.getRibDestinataire() != null ? extracted.getRibDestinataire().replaceAll("\\s", "") : "";
        String officialRibTrimmed = officialRib.replaceFirst("^0+(?!$)", "");
        String extractedRibTrimmed = extractedRib.replaceFirst("^0+(?!$)", "");
        boolean ribValide = !extractedRib.isEmpty() && 
            (extractedRib.equals(officialRib) || officialRibTrimmed.equals(extractedRibTrimmed));

        boolean codeUnique = extracted.getCodePaiement() != null
            && !codesPaiementRepository.existsByCodePaiement(extracted.getCodePaiement());

        boolean montantValide = extracted.getMontant() != null
            && Math.abs(extracted.getMontant() - facture.getMontant()) < 0.01;

        List<String> reasons = new ArrayList<>();
        if (!ribValide) reasons.add("RIB destinataire incorrect");
        if (!codeUnique) {
            if (extracted.getCodePaiement() == null || extracted.getCodePaiement().isBlank()) {
                reasons.add("Code de paiement manquant");
            } else {
                reasons.add("Code de paiement déjà utilisé");
            }
        }
        if (!montantValide) {
            String received = extracted.getMontant() != null ? String.format(Locale.FRANCE, "%.2f", extracted.getMontant()) : "-";
            reasons.add("Montant incorrect: reçu " + received + "DT, attendu " + String.format(Locale.FRANCE, "%.2f", facture.getMontant()) + "DT");
        }

        paiement.setRibDestinataire(extracted.getRibDestinataire());
        paiement.setCodePaiement(extracted.getCodePaiement());
        paiement.setMontant(extracted.getMontant());
        paiement.setDatePaiement(extracted.getDatePaiement());
        paiement.setBanqueEmettrice(extracted.getBanqueEmettrice());

        if (reasons.isEmpty()) {
            paiement.setStatut(Paiement.Statut.VALIDE);
            facture.setStatut(Facture.Statut.PAYEE);
            facture.setDatePaiement(LocalDate.now());

            if (extracted.getCodePaiement() != null) {
                CodesPaiementUtilises usedCode = CodesPaiementUtilises.builder()
                    .codePaiement(extracted.getCodePaiement())
                    .paiementId(paiement.getId())
                    .build();
                codesPaiementRepository.save(usedCode);
            }
        } else {
            paiement.setStatut(Paiement.Statut.REJETE);
            paiement.setMotifRejet(String.join(" + ", reasons));
            facture.setStatut(Facture.Statut.REJETEE);
        }

        factureRepository.save(facture);
        Paiement saved = paiementRepository.save(paiement);
        return toResponse(saved);
    }

    public List<PaiementResponse> getByFacture(Long factureId, String etudiantEmail) {
        Etudiant etudiant = etudiantRepository.findByEmail(etudiantEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        Facture facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée"));

        if (!facture.getEtudiant().getId().equals(etudiant.getId())) {
            throw new IllegalArgumentException("Cette facture ne vous appartient pas");
        }

        return paiementRepository.findByFactureId(factureId).stream().map(this::toResponse).toList();
    }

    public List<PaiementResponse> getByEtudiant(Long etudiantId, String etudiantEmail) {
        Etudiant etudiant = etudiantRepository.findByEmail(etudiantEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        if (!etudiant.getId().equals(etudiantId)) {
            throw new IllegalArgumentException("Accès interdit");
        }

        return paiementRepository.findByEtudiantIdOrderByCreatedAtDesc(etudiantId).stream().map(this::toResponse).toList();
    }

    public List<PaiementResponse> getAll() {
        return paiementRepository.findAll().stream().map(this::toResponse).toList();
    }

    private PaiementResponse toResponse(Paiement p) {
        return PaiementResponse.builder()
            .id(p.getId())
            .factureId(p.getFacture().getId())
            .factureNumero(p.getFacture().getNumero())
            .etudiantId(p.getEtudiant().getId())
            .etudiantNom(p.getEtudiant().getNom())
            .etudiantPrenom(p.getEtudiant().getPrenom())
            .etudiantMatricule(p.getEtudiant().getMatricule())
            .statut(p.getStatut().name())
            .ribDestinataire(p.getRibDestinataire())
            .codePaiement(p.getCodePaiement())
            .montant(p.getMontant())
            .datePaiement(p.getDatePaiement())
            .banqueEmettrice(p.getBanqueEmettrice())
            .preuvePaiement(p.getPreuvePaiement())
            .motifRejet(p.getMotifRejet())
            .createdAt(p.getCreatedAt())
            .build();
    }

    private String extractExtension(String originalName, String contentType) {
        if (originalName != null && originalName.contains(".")) {
            return originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        }
        if ("application/pdf".equals(contentType)) return ".pdf";
        if ("image/png".equals(contentType)) return ".png";
        return ".jpg";
    }
}
