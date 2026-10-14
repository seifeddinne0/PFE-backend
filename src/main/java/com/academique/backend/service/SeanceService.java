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
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SeanceService {

    private static final String STAGE_PFE = "STAGE_PFE";

    private final SeanceRepository seanceRepository;
    private final MatiereRepository matiereRepository;
    private final ClasseRepository classeRepository;
    private final EnseignantRepository enseignantRepository;
    private final NiveauRepository niveauRepository;
    private final CreneauRepository creneauRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public SeanceResponse create(SeanceRequest request) {
        Matiere matiere = matiereRepository.findById(request.getMatiereId())
            .orElseThrow(() -> new ResourceNotFoundException("Matière non trouvée"));
        Classe classe = null;
        if (request.getClasseId() != null) {
            classe = classeRepository.findById(request.getClasseId())
                .orElseThrow(() -> new ResourceNotFoundException("Classe non trouvée"));
        }
        Enseignant enseignant = null;
        if (request.getEnseignantId() != null) {
            enseignant = enseignantRepository.findById(request.getEnseignantId())
                .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
        }
        Niveau niveau = niveauRepository.findById(request.getNiveauId())
            .orElseThrow(() -> new ResourceNotFoundException("Niveau non trouvé"));
        Creneau creneau = creneauRepository.findById(request.getCreneauId().intValue())
            .orElseThrow(() -> new ResourceNotFoundException("Créneau non trouvé"));

        Seance.TypeSeance typeSeance = Seance.TypeSeance.valueOf(request.getTypeSeance());
        Matiere.Semestre semestre = Matiere.Semestre.valueOf(request.getSemestre());

        if (typeSeance == Seance.TypeSeance.COURS && classe != null) {
            throw new IllegalArgumentException("Une séance COURS ne doit pas avoir de classe");
        }

        if (typeSeance == Seance.TypeSeance.TD && classe == null) {
            throw new IllegalArgumentException("Une séance TD doit avoir une classe");
        }

        Seance seance = Seance.builder()
            .jourSemaine(Seance.JourSemaine.valueOf(request.getJourSemaine()))
            .heureDebut(request.getHeureDebut())
            .heureFin(request.getHeureFin())
            .matiere(matiere)
            .classe(classe)
            .enseignant(enseignant)
            .niveau(niveau)
            .typeSeance(typeSeance)
            .semestre(semestre)
            .creneau(creneau)
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
        return getByEnseignant(enseignantId, LocalDate.now());
    }

    public List<SeanceResponse> getByEnseignant(Long enseignantId, LocalDate referenceDate) {
        return filterSeancesByDate(seanceRepository.findByEnseignantId(enseignantId), referenceDate)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    public List<SeanceResponse> getByEnseignantEmail(String email) {
        return getByEnseignantEmail(email, LocalDate.now());
    }

    public List<SeanceResponse> getByEnseignantEmail(String email, LocalDate referenceDate) {
        Enseignant enseignant = enseignantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
        return getByEnseignant(enseignant.getId(), referenceDate);
    }

    public List<SeanceResponse> getByClasseId(Long classeId) {
        return getByClasseId(classeId, LocalDate.now());
    }

    public List<SeanceResponse> getByClasseId(Long classeId, LocalDate referenceDate) {
        Classe classe = classeRepository.findById(classeId)
            .orElseThrow(() -> new ResourceNotFoundException("Classe non trouvée"));
        LocalDate safeDate = referenceDate != null ? referenceDate : LocalDate.now();
        if (isVacationPeriod(safeDate)) {
            return List.of();
        }

        String niveauCode = classe.getNiveau() != null ? classe.getNiveau().getCode() : null;
        if (isStagePfePeriod(niveauCode, safeDate)) {
            return List.of();
        }

        Long niveauId = classe.getNiveau().getId();
        Matiere.Semestre semestreActif = getCurrentSemester(niveauCode, safeDate);

        return seanceRepository.findForClasseAndNiveauAndSemestre(classeId, niveauId, semestreActif).stream()
            .map(this::toResponse)
            .toList();
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
        if (normalizedNiveau == null || !normalizedNiveau.matches("[A-Z]{3}3")) {
            return false;
        }

        LocalDate safeDate = referenceDate != null ? referenceDate : LocalDate.now();
        return isAfterJanuaryCutoff(safeDate);
    }

    private boolean isStagePfeNiveauCode(String niveauCode, LocalDate referenceDate) {
        String normalized = normalizeCode(niveauCode);
        if (normalized == null || !normalized.matches("[A-Z]{3}3")) {
            return false;
        }
        LocalDate safeDate = referenceDate != null ? referenceDate : LocalDate.now();
        return isAfterJanuaryCutoff(safeDate);
    }

    private List<Seance> filterStagePfeSeances(List<Seance> seances, LocalDate referenceDate) {
        if (seances == null || seances.isEmpty()) {
            return List.of();
        }
        return seances.stream()
            .filter(s -> {
                Niveau niveau = s.getNiveau();
                if (niveau == null && s.getClasse() != null) {
                    niveau = s.getClasse().getNiveau();
                }
                if (niveau == null) {
                    return true;
                }
                return !isStagePfeNiveauCode(niveau.getCode(), referenceDate);
            })
            .toList();
    }

    private List<Seance> filterSeancesByDate(List<Seance> seances, LocalDate referenceDate) {
        if (seances == null || seances.isEmpty()) {
            return List.of();
        }

        LocalDate safeDate = referenceDate != null ? referenceDate : LocalDate.now();
        if (isVacationPeriod(safeDate)) {
            return List.of();
        }

        List<Matiere.Semestre> allowedSemestres = getAllowedSemestresByDate(safeDate);

        return filterStagePfeSeances(seances, safeDate).stream()
            .filter(s -> {
                Matiere.Semestre semestre = s.getSemestre();
                if (semestre == null && s.getMatiere() != null) {
                    semestre = s.getMatiere().getSemestre();
                }
                return semestre != null && allowedSemestres.contains(semestre);
            })
            .toList();
    }

    private List<Matiere.Semestre> getAllowedSemestresByDate(LocalDate referenceDate) {
        LocalDate safeDate = referenceDate != null ? referenceDate : LocalDate.now();
        if (isAfterJanuaryCutoff(safeDate)) {
            return List.of(Matiere.Semestre.S2, Matiere.Semestre.S4);
        }
        return List.of(Matiere.Semestre.S1, Matiere.Semestre.S3, Matiere.Semestre.S5);
    }

    private boolean isVacationPeriod(LocalDate referenceDate) {
        LocalDate safeDate = referenceDate != null ? referenceDate : LocalDate.now();
        int year = safeDate.getYear();

        LocalDate mayStart = LocalDate.of(year, 5, 30);
        LocalDate sepEnd = LocalDate.of(year, 9, 11);

        return isBetweenInclusive(safeDate, mayStart, sepEnd);
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

    @org.springframework.transaction.annotation.Transactional
    public Map<String, Integer> assignEnseignant(Long matiereId, String semestre, Long enseignantId) {
        Matiere.Semestre sem = Matiere.Semestre.valueOf(semestre);
        Enseignant enseignant = enseignantRepository.findById(enseignantId)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
        int updated = seanceRepository.assignEnseignant(matiereId, sem, enseignant);
        return java.util.Map.of("updated", updated);
    }

    public List<Map<String, Object>> getConflits() {
        return jdbcTemplate.queryForList("SELECT * FROM vue_conflits");
    }

    public List<Map<String, Object>> getChargeEnseignants() {
        return jdbcTemplate.queryForList("SELECT * FROM vue_charge_enseignant");
    }

    private SeanceResponse toResponse(Seance s) {
        Classe classe = s.getClasse();
        Niveau niveau = s.getNiveau();
        Creneau creneau = s.getCreneau();
        return SeanceResponse.builder()
            .id(s.getId())
            .jourSemaine(s.getJourSemaine().name())
            .heureDebut(s.getHeureDebut())
            .heureFin(s.getHeureFin())
            .salle(s.getSalle())
            .typeSeance(s.getTypeSeance() != null ? s.getTypeSeance().name() : null)
            .semestre(s.getSemestre() != null ? s.getSemestre().name() : null)
            .niveauId(niveau != null ? niveau.getId() : null)
            .niveauCode(niveau != null ? niveau.getCode() : null)
            .creneauId(creneau != null && creneau.getId() != null ? creneau.getId().longValue() : null)
            .creneauLabel(creneau != null ? creneau.getLabel() : null)
            .matiereId(s.getMatiere().getId())
            .matiereNom(s.getMatiere().getNom())
            .matiereCode(s.getMatiere().getCode())
            .classeId(classe != null ? classe.getId() : null)
            .classeCode(classe != null ? classe.getCode() : null)
            .classeNom(classe != null ? classe.getNom() : null)
            .enseignantId(s.getEnseignant() != null ? s.getEnseignant().getId() : null)
            .enseignantNom(s.getEnseignant() != null ? s.getEnseignant().getNom() + " " + s.getEnseignant().getPrenom() : null)
            .build();
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
