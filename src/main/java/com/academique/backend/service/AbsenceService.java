package com.academique.backend.service;

import com.academique.backend.dto.request.AbsenceRequest;
import com.academique.backend.dto.request.BatchAbsenceRequest;
import com.academique.backend.dto.response.AbsenceResponse;
import com.academique.backend.entity.*;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AbsenceService {

    private final AbsenceRepository absenceRepository;
    private final EtudiantRepository etudiantRepository;
    private final MatiereRepository matiereRepository;
    private final EnseignantRepository enseignantRepository;
    private final SeanceRepository seanceRepository;
    private final NotificationService notificationService;

    private static final int SEUIL_ALERTE = 3;

    // ─── CREATE ───────────────────────────────────────────
    public AbsenceResponse create(AbsenceRequest request) {
        Etudiant etudiant = etudiantRepository.findById(request.getEtudiantId())
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        Absence absence = new Absence();
        absence.setDateAbsence(request.getDateAbsence());
        absence.setEtudiant(etudiant);
        absence.setMotif(request.getMotif());
        absence.setJustification(request.getJustification());
        absence.setStatut(parseStatut(request.getStatut()));

        if (request.getMatiereId() != null) {
            Matiere matiere = matiereRepository.findById(request.getMatiereId())
                .orElseThrow(() -> new ResourceNotFoundException("Matière non trouvée"));
            absence.setMatiere(matiere);
        }

        if (request.getEnseignantId() != null) {
            Enseignant enseignant = enseignantRepository.findById(request.getEnseignantId())
                .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
            absence.setEnseignant(enseignant);
        }

        if (request.getSeanceId() != null) {
            Seance seance = seanceRepository.findById(request.getSeanceId())
                .orElseThrow(() -> new ResourceNotFoundException("Séance non trouvée"));
            absence.setSeance(seance);
            // Auto-fill matiere and enseignant from seance
            if (absence.getMatiere() == null) absence.setMatiere(seance.getMatiere());
            if (absence.getEnseignant() == null) absence.setEnseignant(seance.getEnseignant());
        }

        Absence saved = absenceRepository.save(absence);
        updateAlerteStatusForStudentAndMatiere(etudiant.getId(), saved.getMatiere().getId());

        // Notify Student
        notificationService.createNotification(
            etudiant.getUser().getId(),
            "ETUDIANT",
            "Nouvelle absence enregistrée",
            "Une absence a été marquée le " + saved.getDateAbsence() + (saved.getMatiere() != null ? " en " + saved.getMatiere().getNom() : ""),
            "ABSENCE",
            etudiant.getEmail()
        );

        return toResponse(absenceRepository.findById(saved.getId()).orElse(saved));
    }

    // ─── BATCH CREATE (from séance) ──────────────────────
    public List<AbsenceResponse> createBatch(BatchAbsenceRequest request) {
        Seance seance = seanceRepository.findById(request.getSeanceId())
            .orElseThrow(() -> new ResourceNotFoundException("Séance non trouvée"));

        List<Absence> existing = absenceRepository.findBySeanceIdAndDateAbsence(
            request.getSeanceId(), request.getDateAbsence());
        Set<Long> alreadyMarkedStudentIds = new HashSet<>();
        for (Absence absence : existing) {
            alreadyMarkedStudentIds.add(absence.getEtudiant().getId());
        }

        List<AbsenceResponse> results = new ArrayList<>();
        Absence.Statut statut = parseStatut(request.getStatut());

        for (Long etudiantId : request.getEtudiantIds()) {
            Etudiant etudiant = etudiantRepository.findById(etudiantId)
                .orElseThrow(() -> new ResourceNotFoundException("Étudiant ID " + etudiantId + " non trouvé"));

            if (!alreadyMarkedStudentIds.contains(etudiantId)) {
                Absence absence = new Absence();
                absence.setDateAbsence(request.getDateAbsence());
                absence.setEtudiant(etudiant);
                absence.setSeance(seance);
                absence.setMatiere(seance.getMatiere());
                absence.setEnseignant(seance.getEnseignant());
                absence.setMotif(request.getMotif());
                absence.setJustification(request.getJustification());
                absence.setStatut(statut);

                Absence saved = absenceRepository.save(absence);
                updateAlerteStatusForStudentAndMatiere(etudiantId, seance.getMatiere().getId());
                results.add(toResponse(absenceRepository.findById(saved.getId()).orElse(saved)));

                // Notify Student
                notificationService.createNotification(
                    etudiant.getUser().getId(),
                    "ETUDIANT",
                    "Nouvelle absence enregistrée",
                    "Une absence a été marquée le " + saved.getDateAbsence() + " en " + seance.getMatiere().getNom(),
                    "ABSENCE",
                    etudiant.getEmail()
                );

                alreadyMarkedStudentIds.add(etudiantId);
            }
        }

        return results;
    }

    // ─── GET ABSENCES BY SEANCE AND DATE ─────────────────
    public List<AbsenceResponse> getBySeanceAndDate(Long seanceId, LocalDate date) {
        return absenceRepository.findBySeanceIdAndDateAbsence(seanceId, date)
            .stream().map(this::toResponse).toList();
    }

    // ─── DELETE ABSENCES BY SEANCE AND DATE (for re-recording) ─
    public void deleteBySeanceAndDate(Long seanceId, LocalDate date) {
        List<Absence> absences = absenceRepository.findBySeanceIdAndDateAbsence(seanceId, date);
        absenceRepository.deleteAll(absences);
    }

    // ─── READ ALL ──────────────────────────────────────────
    public Page<AbsenceResponse> getAll(Pageable pageable) {
        return absenceRepository.findAll(pageable).map(this::toResponse);
    }

    // ─── READ BY ID ────────────────────────────────────────
    public AbsenceResponse getById(Long id) {
        return toResponse(absenceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Absence non trouvée")));
    }

    // ─── READ BY ETUDIANT ──────────────────────────────────
    public List<AbsenceResponse> getByEtudiant(Long etudiantId) {
        return absenceRepository.findByEtudiantIdOrderByDate(etudiantId)
            .stream().map(this::toResponse).toList();
    }

    // ─── READ BY EMAIL (Étudiant connecté) ────────────────
    public List<AbsenceResponse> getByEtudiantEmail(String email) {
        Etudiant etudiant = etudiantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        return absenceRepository.findByEtudiantIdOrderByDate(etudiant.getId())
            .stream().map(this::toResponse).toList();
    }

    // ─── READ BY STATUT ────────────────────────────────────
    public Page<AbsenceResponse> getByStatut(String statut, Pageable pageable) {
        return absenceRepository.findByStatut(
            Absence.Statut.valueOf(statut), pageable).map(this::toResponse);
    }

    // ─── UPDATE ───────────────────────────────────────────
    public AbsenceResponse update(Long id, AbsenceRequest request) {
        Absence absence = absenceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Absence non trouvée"));
        absence.setDateAbsence(request.getDateAbsence());
        absence.setMotif(request.getMotif());
        absence.setJustification(request.getJustification());
        if (request.getStatut() != null) {
            absence.setStatut(parseStatut(request.getStatut()));
        }
        Absence saved = absenceRepository.save(absence);
        if (saved.getMatiere() != null) {
            updateAlerteStatusForStudentAndMatiere(saved.getEtudiant().getId(), saved.getMatiere().getId());
        }
        return toResponse(absenceRepository.findById(saved.getId()).orElse(saved));
    }

    // ─── JUSTIFIER ────────────────────────────────────────
    public AbsenceResponse justifier(Long id, String justification) {
        Absence absence = absenceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Absence non trouvée"));
        absence.setJustification(justification);
        absence.setStatut(Absence.Statut.JUSTIFIEE);
        Absence saved = absenceRepository.save(absence);
        if (saved.getMatiere() != null) {
            updateAlerteStatusForStudentAndMatiere(saved.getEtudiant().getId(), saved.getMatiere().getId());
        }
        return toResponse(absenceRepository.findById(saved.getId()).orElse(saved));
    }

    public AbsenceResponse demanderJustificationEtudiant(
        Long absenceId,
        String etudiantEmail,
        String commentaire,
        MultipartFile file
    ) {
        Etudiant etudiant = etudiantRepository.findByEmail(etudiantEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        Absence absence = findAbsenceById(absenceId);

        if (!absence.getEtudiant().getId().equals(etudiant.getId())) {
            throw new IllegalArgumentException("Vous ne pouvez justifier que vos propres absences");
        }

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier de justification est obligatoire");
        }

        String contentType = file.getContentType();
        boolean isImage = contentType != null && contentType.startsWith("image/");
        boolean isPdf = contentType != null && contentType.equals("application/pdf");

        if (!isImage && !isPdf) {
            throw new IllegalArgumentException("Le fichier doit être une image ou un PDF");
        }

        String extension = extractExtension(file.getOriginalFilename());
        String fileName = "justif_" + absenceId + "_" + UUID.randomUUID() + extension;
        Path uploadDir = Path.of("uploads", "justifications");
        Path targetPath = uploadDir.resolve(fileName);

        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'upload du fichier", e);
        }

        absence.setPreuveJustification("/uploads/justifications/" + fileName);
        absence.setJustification(commentaire != null ? commentaire.trim() : "");
        absence.setStatut(Absence.Statut.EN_ATTENTE);
        Absence saved = absenceRepository.save(absence);

        // Notify Admin (In-app only)
        notificationService.createNotification(
            0L, // Placeholder for all admins
            "ADMIN",
            "Nouvelle justification à vérifier",
            "L'étudiant " + etudiant.getNom() + " " + etudiant.getPrenom() + " a soumis une justification pour son absence du " + saved.getDateAbsence(),
            "ABSENCE_JUSTIFICATION",
            null
        );

        return toResponse(saved);
    }

    public AbsenceResponse approveJustification(Long id) {
        Absence absence = findAbsenceById(id);
        absence.setStatut(Absence.Statut.JUSTIFIEE);
        Absence saved = absenceRepository.save(absence);
        if (saved.getMatiere() != null) {
            updateAlerteStatusForStudentAndMatiere(saved.getEtudiant().getId(), saved.getMatiere().getId());
        }

        // Notify Student
        notificationService.createNotification(
            saved.getEtudiant().getUser().getId(),
            "ETUDIANT",
            "Justification acceptée",
            "Votre justification pour l'absence du " + saved.getDateAbsence() + " a été acceptée.",
            "ABSENCE",
            saved.getEtudiant().getEmail()
        );

        return toResponse(absenceRepository.findById(saved.getId()).orElse(saved));
    }

    public AbsenceResponse rejectJustification(Long id) {
        Absence absence = findAbsenceById(id);
        absence.setStatut(Absence.Statut.NON_JUSTIFIEE);
        Absence saved = absenceRepository.save(absence);
        if (saved.getMatiere() != null) {
            updateAlerteStatusForStudentAndMatiere(saved.getEtudiant().getId(), saved.getMatiere().getId());
        }

        // Notify Student
        notificationService.createNotification(
            saved.getEtudiant().getUser().getId(),
            "ETUDIANT",
            "Justification refusée",
            "Votre justification pour l'absence du " + saved.getDateAbsence() + " a été refusée par l'administration.",
            "ABSENCE",
            saved.getEtudiant().getEmail()
        );

        return toResponse(absenceRepository.findById(saved.getId()).orElse(saved));
    }

    // ─── DELETE ───────────────────────────────────────────
    public void delete(Long id) {
        Absence absence = absenceRepository.findById(id).orElse(null);
        if (absence == null) return;
        
        Long etudiantId = absence.getEtudiant().getId();
        Long matiereId = absence.getMatiere() != null ? absence.getMatiere().getId() : null;
        
        absenceRepository.deleteById(id);
        
        if (matiereId != null) {
            updateAlerteStatusForStudentAndMatiere(etudiantId, matiereId);
        }
    }

    private void updateAlerteStatusForStudentAndMatiere(Long etudiantId, Long matiereId) {
        if (matiereId == null) return;
        
        long count = absenceRepository.countByEtudiantIdAndMatiereIdAndStatutNot(
            etudiantId, matiereId, Absence.Statut.JUSTIFIEE);
        
        boolean isAlert = count > SEUIL_ALERTE;
        
        List<Absence> absences = absenceRepository.findByEtudiantIdAndMatiereId(etudiantId, matiereId);
        for (Absence a : absences) {
            a.setAlerte(isAlert);
        }
        absenceRepository.saveAll(absences);
    }

    // ─── TOTAL ABSENCES ───────────────────────────────────
    public long countAbsences(Long etudiantId) {
        return absenceRepository.countByEtudiantId(etudiantId);
    }

    // ─── MAPPER ───────────────────────────────────────────
    private AbsenceResponse toResponse(Absence a) {
        return AbsenceResponse.builder()
            .id(a.getId())
            .dateAbsence(a.getDateAbsence())
            .statut(a.getStatut().name())
            .motif(a.getMotif())
            .justification(a.getJustification())
            .preuveJustification(a.getPreuveJustification())
            .alerte(a.getAlerte())
            .createdAt(a.getCreatedAt())
            .etudiantId(a.getEtudiant().getId())
            .etudiantNom(a.getEtudiant().getNom())
            .etudiantPrenom(a.getEtudiant().getPrenom())
            .etudiantMatricule(a.getEtudiant().getMatricule())
            .matiereNom(a.getMatiere() != null ? a.getMatiere().getNom() : null)
            .enseignantNom(a.getEnseignant() != null ?
                a.getEnseignant().getNom() + " " + a.getEnseignant().getPrenom() : null)
            .totalAbsences(absenceRepository.countByEtudiantId(a.getEtudiant().getId()))
            .seanceId(a.getSeance() != null ? a.getSeance().getId() : null)
            .classeCode(a.getEtudiant().getClasse() != null ? a.getEtudiant().getClasse().getCode() : null)
            .niveauCode(a.getEtudiant().getClasse() != null && a.getEtudiant().getClasse().getNiveau() != null ? a.getEtudiant().getClasse().getNiveau().getCode() : null)
            .filiereNom(a.getEtudiant().getClasse() != null && a.getEtudiant().getClasse().getNiveau() != null && a.getEtudiant().getClasse().getNiveau().getFiliere() != null ? a.getEtudiant().getClasse().getNiveau().getFiliere().getNom() : null)
            .build();
    }

    private Absence.Statut parseStatut(String statut) {
        if (statut == null || statut.isBlank()) {
            return Absence.Statut.NON_JUSTIFIEE;
        }
        return Absence.Statut.valueOf(statut.trim().toUpperCase(Locale.ROOT));
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return ".jpg";
        }

        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return ".jpg";
        }

        String ext = fileName.substring(index).toLowerCase(Locale.ROOT);
        if (ext.matches("\\.(jpg|jpeg|png|webp|gif|pdf)")) {
            return ext;
        }
        return ".jpg";
    }

    private Absence findAbsenceById(Long id) {
        return absenceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Absence non trouvée"));
    }
}