package com.academique.backend.service;

import com.academique.backend.dto.request.SeanceRequest;
import com.academique.backend.dto.response.AgendaAbsenceResponse;
import com.academique.backend.dto.response.SeanceResponse;
import com.academique.backend.entity.*;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SeanceService {

    private static final String STAGE_PFE = "STAGE_PFE";

    private final SeanceRepository seanceRepository;
    private final MatiereRepository matiereRepository;
    private final ClasseRepository classeRepository;
    private final EnseignantRepository enseignantRepository;

    public SeanceResponse create(SeanceRequest request) {
        Matiere matiere = matiereRepository.findById(request.getMatiereId())
            .orElseThrow(() -> new ResourceNotFoundException("Matière non trouvée"));
        Classe classe = classeRepository.findById(request.getClasseId())
            .orElseThrow(() -> new ResourceNotFoundException("Classe non trouvée"));
        Enseignant enseignant = enseignantRepository.findById(request.getEnseignantId())
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));

        Seance seance = Seance.builder()
            .jourSemaine(Seance.JourSemaine.valueOf(request.getJourSemaine()))
            .heureDebut(request.getHeureDebut())
            .heureFin(request.getHeureFin())
            .matiere(matiere)
            .classe(classe)
            .enseignant(enseignant)
            .salle(request.getSalle())
            .build();

        return toResponse(seanceRepository.save(seance));
    }

    public List<SeanceResponse> getAll() {
        return seanceRepository.findAll().stream()
            .map(this::toResponse)
            .toList();
    }

    public List<SeanceResponse> getByEnseignant(Long enseignantId) {
        return seanceRepository.findByEnseignantId(enseignantId).stream()
            .map(this::toResponse)
            .toList();
    }

    public List<SeanceResponse> getByEnseignantEmail(String email) {
        Enseignant enseignant = enseignantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
        return getByEnseignant(enseignant.getId());
    }

    public AgendaAbsenceResponse getAgendaAbsencesForTeacher(
        String enseignantEmail,
        String filiereCode,
        String niveauCode,
        LocalDate referenceDate
    ) {
        Enseignant enseignant = enseignantRepository.findByEmail(enseignantEmail)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));

        String normalizedFiliere = normalizeCode(filiereCode);
        String normalizedNiveau = normalizeCode(niveauCode);
        LocalDate effectiveDate = referenceDate != null ? referenceDate : LocalDate.now();

        if (normalizedNiveau == null) {
            return AgendaAbsenceResponse.builder()
                .filiereCode(normalizedFiliere)
                .niveauCode(null)
                .blocked(false)
                .message("Veuillez sélectionner un niveau")
                .seances(List.of())
                .build();
        }

        String semestreLabel = isStagePfePeriod(normalizedNiveau, effectiveDate)
            ? STAGE_PFE
            : getCurrentSemester(normalizedNiveau, effectiveDate).name();

        if (isVacationPeriod(effectiveDate)) {
            return AgendaAbsenceResponse.builder()
                .filiereCode(normalizedFiliere)
                .niveauCode(normalizedNiveau)
                .semestreActif(semestreLabel)
                .blocked(false)
                .message("Periode de vacances: 01/01-15/01, 15/03-31/03, 30/05-12/09")
                .seances(List.of())
                .build();
        }

        if (STAGE_PFE.equals(semestreLabel)) {
            return AgendaAbsenceResponse.builder()
                .filiereCode(normalizedFiliere)
                .niveauCode(normalizedNiveau)
                .semestreActif(STAGE_PFE)
                .blocked(false)
                .message("LCS3 apres le 15 janvier: periode stage PFE")
                .seances(List.of())
                .build();
        }

            Matiere.Semestre semestreActif = getCurrentSemester(normalizedNiveau, effectiveDate);

        List<SeanceResponse> seances = seanceRepository
            .findAgendaByEnseignantAndSemestre(
                enseignant.getId(),
                semestreActif,
                normalizedFiliere,
                normalizedNiveau
            )
            .stream()
            .map(this::toResponse)
            .toList();

        return AgendaAbsenceResponse.builder()
            .filiereCode(normalizedFiliere)
            .niveauCode(normalizedNiveau)
            .semestreActif(semestreActif.name())
            .blocked(false)
            .message(null)
            .seances(seances)
            .build();
    }

    // Le semestre actif est déterminé par la date système et le niveau choisi.
    public Matiere.Semestre getCurrentSemester(String niveauCode) {
        return getCurrentSemester(niveauCode, LocalDate.now());
    }

    // Le semestre actif est déterminé par la date de référence (semaine affichée) et le niveau.
    public Matiere.Semestre getCurrentSemester(String niveauCode, LocalDate referenceDate) {
        String normalizedNiveau = normalizeCode(niveauCode);
        if (normalizedNiveau == null) {
            throw new IllegalArgumentException("Le niveau est obligatoire");
        }

        LocalDate safeDate = referenceDate != null ? referenceDate : LocalDate.now();
        boolean afterCutOff = isAfterJanuaryCutoff(safeDate);

        return switch (normalizedNiveau) {
            case "LCS1" -> afterCutOff ? Matiere.Semestre.S2 : Matiere.Semestre.S1;
            case "LCS2" -> afterCutOff ? Matiere.Semestre.S4 : Matiere.Semestre.S3;
            case "LCS3" -> Matiere.Semestre.S5;
            default -> throw new IllegalArgumentException("Niveau non supporté: " + normalizedNiveau);
        };
    }

    private boolean isStagePfePeriod(String normalizedNiveau) {
        return isStagePfePeriod(normalizedNiveau, LocalDate.now());
    }

    private boolean isStagePfePeriod(String normalizedNiveau, LocalDate referenceDate) {
        if (!"LCS3".equals(normalizedNiveau)) {
            return false;
        }

        LocalDate safeDate = referenceDate != null ? referenceDate : LocalDate.now();
        return isAfterJanuaryCutoff(safeDate);
    }

    private boolean isVacationPeriod(LocalDate referenceDate) {
        LocalDate safeDate = referenceDate != null ? referenceDate : LocalDate.now();
        int year = safeDate.getYear();

        LocalDate janStart = LocalDate.of(year, 1, 1);
        LocalDate janEnd = LocalDate.of(year, 1, 15);

        LocalDate marStart = LocalDate.of(year, 3, 15);
        LocalDate marEnd = LocalDate.of(year, 3, 31);

        LocalDate mayStart = LocalDate.of(year, 5, 30);
        LocalDate sepEnd = LocalDate.of(year, 9, 12);

        return isBetweenInclusive(safeDate, janStart, janEnd)
            || isBetweenInclusive(safeDate, marStart, marEnd)
            || isBetweenInclusive(safeDate, mayStart, sepEnd);
    }

    private boolean isBetweenInclusive(LocalDate date, LocalDate start, LocalDate end) {
        return !date.isBefore(start) && !date.isAfter(end);
    }

    // Règle académique: S2/S4 (et Stage PFE) uniquement du 16 janvier au 29 mai.
    // De septembre à décembre, on reste en S1/S3/S5.
    private boolean isAfterJanuaryCutoff(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        if (month == 1) {
            return day > 15;
        }

        return month >= 2 && month <= 5;
    }

    public SeanceResponse getById(Long id) {
        return toResponse(seanceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Séance non trouvée")));
    }

    public void delete(Long id) {
        if (!seanceRepository.existsById(id))
            throw new ResourceNotFoundException("Séance non trouvée");
        seanceRepository.deleteById(id);
    }

    private SeanceResponse toResponse(Seance s) {
        return SeanceResponse.builder()
            .id(s.getId())
            .jourSemaine(s.getJourSemaine().name())
            .heureDebut(s.getHeureDebut())
            .heureFin(s.getHeureFin())
            .salle(s.getSalle())
            .matiereId(s.getMatiere().getId())
            .matiereNom(s.getMatiere().getNom())
            .matiereCode(s.getMatiere().getCode())
            .classeId(s.getClasse().getId())
            .classeCode(s.getClasse().getCode())
            .classeNom(s.getClasse().getNom())
            .enseignantId(s.getEnseignant().getId())
            .enseignantNom(s.getEnseignant().getNom() + " " + s.getEnseignant().getPrenom())
            .build();
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
