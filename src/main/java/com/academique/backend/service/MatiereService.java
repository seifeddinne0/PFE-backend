package com.academique.backend.service;

import com.academique.backend.dto.request.MatiereRequest;
import com.academique.backend.dto.response.MatiereResponse;
import com.academique.backend.entity.Enseignant;
import com.academique.backend.entity.Matiere;
import com.academique.backend.entity.Niveau;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.EnseignantRepository;
import com.academique.backend.repository.MatiereRepository;
import com.academique.backend.repository.NiveauRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MatiereService {

    private final MatiereRepository matiereRepository;
    private final EnseignantRepository enseignantRepository;
    private final NiveauRepository niveauRepository;

    public MatiereResponse create(MatiereRequest request) {
        if (matiereRepository.existsByNom(request.getNom())) {
            throw new RuntimeException("Matière déjà existante");
        }
        Matiere matiere = Matiere.builder()
            .nom(request.getNom())
            .code(request.getCode())
            .description(request.getDescription())
            .coefficient(request.getCoefficient())
            .semestre(Matiere.Semestre.valueOf(request.getSemestre()))
            .build();

        if (request.getEnseignantId() != null) {
            Enseignant e = enseignantRepository.findById(request.getEnseignantId())
                .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
            matiere.setEnseignant(e);
        }
        
        if (request.getNiveauId() != null) {
            Niveau n = niveauRepository.findById(request.getNiveauId())
                .orElseThrow(() -> new ResourceNotFoundException("Niveau non trouvé"));
            matiere.setNiveau(n);
        }

        return toResponse(matiereRepository.save(matiere));
    }

    public List<MatiereResponse> getAll() {
        return matiereRepository.findAll()
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public Page<MatiereResponse> getAllPaged(Pageable pageable) {
        return matiereRepository.findAll(pageable).map(this::toResponse);
    }

    public MatiereResponse getById(Long id) {
        return toResponse(matiereRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Matière non trouvée")));
    }

    public MatiereResponse update(Long id, MatiereRequest request) {
        Matiere matiere = matiereRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Matière non trouvée"));
        matiere.setNom(request.getNom());
        matiere.setCode(request.getCode());
        matiere.setDescription(request.getDescription());
        matiere.setCoefficient(request.getCoefficient());
        matiere.setSemestre(Matiere.Semestre.valueOf(request.getSemestre()));

        if (request.getEnseignantId() != null) {
            Enseignant e = enseignantRepository.findById(request.getEnseignantId())
                .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
            matiere.setEnseignant(e);
        } else {
            matiere.setEnseignant(null);
        }
        
        if (request.getNiveauId() != null) {
            Niveau n = niveauRepository.findById(request.getNiveauId())
                .orElseThrow(() -> new ResourceNotFoundException("Niveau non trouvé"));
            matiere.setNiveau(n);
        } else {
            matiere.setNiveau(null);
        }

        return toResponse(matiereRepository.save(matiere));
    }

    public List<MatiereResponse> getMatieresByEnseignant(Long enseignantId) {
        Enseignant e = enseignantRepository.findById(enseignantId)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
        return matiereRepository.findByEnseignant(e).stream()
            .map(this::toResponse).collect(Collectors.toList());
    }

    public void updateEnseignantMatieres(Long enseignantId, List<Long> matiereIds) {
        Enseignant enseignant = enseignantRepository.findById(enseignantId)
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));

        // Unset old matieres
        List<Matiere> oldMatieres = matiereRepository.findByEnseignant(enseignant);
        for (Matiere m : oldMatieres) {
            m.setEnseignant(null);
        }
        matiereRepository.saveAll(oldMatieres);

        // Set new matieres
        if (matiereIds != null && !matiereIds.isEmpty()) {
            List<Matiere> newMatieres = matiereRepository.findAllById(matiereIds);
            for (Matiere m : newMatieres) {
                m.setEnseignant(enseignant);
            }
            matiereRepository.saveAll(newMatieres);
        }
    }

    public void delete(Long id) {
        if (!matiereRepository.existsById(id)) {
            throw new ResourceNotFoundException("Matière non trouvée");
        }
        matiereRepository.deleteById(id);
    }

    private MatiereResponse toResponse(Matiere m) {
        return MatiereResponse.builder()
            .id(m.getId())
            .nom(m.getNom())
            .code(m.getCode())
            .description(m.getDescription())
            .coefficient(m.getCoefficient())
            .semestre(m.getSemestre().name())
            .enseignantId(m.getEnseignant() != null ? m.getEnseignant().getId() : null)
            .niveauId(m.getNiveau() != null ? m.getNiveau().getId() : null)
            .niveauCode(m.getNiveau() != null ? m.getNiveau().getCode() : null)
            .filiereNom(m.getNiveau() != null && m.getNiveau().getFiliere() != null ? m.getNiveau().getFiliere().getNom() : null)
            .filiereCode(m.getNiveau() != null && m.getNiveau().getFiliere() != null ? m.getNiveau().getFiliere().getCode() : null)
            .build();
    }
}