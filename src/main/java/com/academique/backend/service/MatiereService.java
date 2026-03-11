package com.academique.backend.service;

import com.academique.backend.dto.request.MatiereRequest;
import com.academique.backend.dto.response.MatiereResponse;
import com.academique.backend.entity.Matiere;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.MatiereRepository;
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
        return toResponse(matiereRepository.save(matiere));
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
            .build();
    }
}