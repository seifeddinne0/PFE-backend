package com.academique.backend.service;

import com.academique.backend.dto.request.AbsenceRequest;
import com.academique.backend.dto.response.AbsenceResponse;
import com.academique.backend.entity.*;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AbsenceService {

    private final AbsenceRepository absenceRepository;
    private final EtudiantRepository etudiantRepository;
    private final MatiereRepository matiereRepository;
    private final EnseignantRepository enseignantRepository;

    private static final int SEUIL_ALERTE = 3;

    // ─── CREATE ───────────────────────────────────────────
    public AbsenceResponse create(AbsenceRequest request) {
        Etudiant etudiant = etudiantRepository.findById(request.getEtudiantId())
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));

        Absence absence = new Absence();
        absence.setDateAbsence(request.getDateAbsence());
        absence.setEtudiant(etudiant);
        absence.setMotif(request.getMotif());
        absence.setStatut(request.getStatut() != null ?
            Absence.Statut.valueOf(request.getStatut()) : Absence.Statut.NON_JUSTIFIEE);

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

        // ✅ Alerte automatique si > seuil
        long totalAbsences = absenceRepository.countByEtudiantId(request.getEtudiantId()) + 1;
        absence.setAlerte(totalAbsences >= SEUIL_ALERTE);

        return toResponse(absenceRepository.save(absence));
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
        if (request.getStatut() != null)
            absence.setStatut(Absence.Statut.valueOf(request.getStatut()));
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
            .build();
    }
}