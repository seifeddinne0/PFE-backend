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

        // ✅ Alerte automatique si > seuil
        long totalAbsences = absenceRepository.countByEtudiantId(request.getEtudiantId()) + 1;
        absence.setAlerte(totalAbsences >= SEUIL_ALERTE);

        return toResponse(absenceRepository.save(absence));
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

                long totalAbsences = absenceRepository.countByEtudiantId(etudiantId) + 1;
                absence.setAlerte(totalAbsences >= SEUIL_ALERTE);

                results.add(toResponse(absenceRepository.save(absence)));
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
        return toResponse(absenceRepository.save(absence));
    }

    // ─── JUSTIFIER ────────────────────────────────────────
    public AbsenceResponse justifier(Long id, String justification) {
        Absence absence = absenceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Absence non trouvée"));
        absence.setJustification(justification);
        absence.setStatut(Absence.Statut.JUSTIFIEE);
        absence.setAlerte(false);
        return toResponse(absenceRepository.save(absence));
    }

    public AbsenceResponse demanderJustificationEtudiant(
        Long absenceId,
        String etudiantEmail,
        String commentaire,
        MultipartFile image
    ) {
        Etudiant etudiant = etudiantRepository.findByEmail(etudiantEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        Absence absence = absenceRepository.findById(absenceId)
            .orElseThrow(() -> new ResourceNotFoundException("Absence non trouvée"));

        if (!absence.getEtudiant().getId().equals(etudiant.getId())) {
            throw new IllegalArgumentException("Vous ne pouvez justifier que vos propres absences");
        }

        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("Une image de justification est obligatoire");
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Le fichier doit être une image");
        }

        String extension = extractExtension(image.getOriginalFilename());
        String fileName = "justif_" + absenceId + "_" + UUID.randomUUID() + extension;
        Path uploadDir = Path.of("uploads", "justifications");
        Path targetPath = uploadDir.resolve(fileName);

        try {
            Files.createDirectories(uploadDir);
            Files.copy(image.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Erreur lors de l'upload de l'image", e);
        }

        String imagePath = "/uploads/justifications/" + fileName;
        String comment = commentaire == null ? "" : commentaire.trim();
        String justificationText = comment.isBlank() ? imagePath : comment + " | " + imagePath;

        absence.setJustification(justificationText);
        absence.setStatut(Absence.Statut.EN_ATTENTE);

        return toResponse(absenceRepository.save(absence));
    }

    // ─── DELETE ───────────────────────────────────────────
    public void delete(Long id) {
        if (!absenceRepository.existsById(id))
            throw new ResourceNotFoundException("Absence non trouvée");
        absenceRepository.deleteById(id);
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
        if (ext.matches("\\.(jpg|jpeg|png|webp|gif)")) {
            return ext;
        }
        return ".jpg";
    }
}