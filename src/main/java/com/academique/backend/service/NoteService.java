package com.academique.backend.service;

import com.academique.backend.dto.request.NoteRequest;
import com.academique.backend.dto.response.NoteResponse;
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
import java.util.List;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final EtudiantRepository etudiantRepository;
    private final MatiereRepository matiereRepository;
    private final EnseignantRepository enseignantRepository;

    public NoteResponse create(NoteRequest request) {
        Etudiant etudiant = etudiantRepository.findById(request.getEtudiantId())
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        Matiere matiere = matiereRepository.findById(request.getMatiereId())
            .orElseThrow(() -> new ResourceNotFoundException("Matière non trouvée"));

        Note note = new Note();
        note.setValeur(request.getValeur());
        note.setEtudiant(etudiant);
        note.setMatiere(matiere);
        note.setTypeNote(request.getTypeNote() != null ?
            Note.TypeNote.valueOf(request.getTypeNote()) : Note.TypeNote.EXAMEN);
        note.setSemestre(request.getSemestre() != null ?
            Note.Semestre.valueOf(request.getSemestre()) : Note.Semestre.S1);
        note.setCommentaire(request.getCommentaire());

        if (request.getEnseignantId() != null) {
            Enseignant enseignant = enseignantRepository.findById(request.getEnseignantId())
                .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));
            note.setEnseignant(enseignant);
        }
        return toResponse(noteRepository.save(note));
    }

    public Page<NoteResponse> getAll(Pageable pageable) {
        return noteRepository.findAll(pageable).map(this::toResponse);
    }

    public NoteResponse getById(Long id) {
        return toResponse(noteRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Note non trouvée")));
    }

    public List<NoteResponse> getByEtudiant(Long etudiantId) {
        return noteRepository.findByEtudiantId(etudiantId)
            .stream().map(this::toResponse).toList();
    }

    public List<NoteResponse> getByEtudiantAndSemestre(Long etudiantId, String semestre) {
        return noteRepository.findByEtudiantIdAndSemestre(
            etudiantId, Note.Semestre.valueOf(semestre))
            .stream().map(this::toResponse).toList();
    }

    public NoteResponse update(Long id, NoteRequest request) {
        Note note = noteRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Note non trouvée"));
        note.setValeur(request.getValeur());
        note.setCommentaire(request.getCommentaire());
        if (request.getTypeNote() != null)
            note.setTypeNote(Note.TypeNote.valueOf(request.getTypeNote()));
        if (request.getSemestre() != null)
            note.setSemestre(Note.Semestre.valueOf(request.getSemestre()));
        return toResponse(noteRepository.save(note));
    }
    public List<NoteResponse> getByEtudiantEmail(String email) {
        Etudiant etudiant = etudiantRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        return noteRepository.findByEtudiantId(etudiant.getId()).stream().map(this::toResponse).toList();
        }

    public void delete(Long id) {
        if (!noteRepository.existsById(id))
            throw new ResourceNotFoundException("Note non trouvée");
        noteRepository.deleteById(id);
    }

    public Double calculerMoyenne(Long etudiantId, String semestre) {
        Double moyenne = noteRepository.calculerMoyenne(
            etudiantId, Note.Semestre.valueOf(semestre));
        return moyenne != null ? Math.round(moyenne * 100.0) / 100.0 : 0.0;
    }

    public byte[] exportBulletinPdf(Long etudiantId, String semestre) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        List<Note> notes = noteRepository.findByEtudiantIdAndSemestre(
            etudiantId, Note.Semestre.valueOf(semestre));
        Double moyenne = calculerMoyenne(etudiantId, semestre);

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            // ── En-tête ──
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, new BaseColor(4, 41, 84));
            Font subFont  = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL, BaseColor.DARK_GRAY);
            Font boldFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD);

            Paragraph titre = new Paragraph("BULLETIN DE NOTES", titleFont);
            titre.setAlignment(Element.ALIGN_CENTER);
            titre.setSpacingAfter(4);
            document.add(titre);

            Paragraph sousTitre = new Paragraph("Système de Gestion Académique", subFont);
            sousTitre.setAlignment(Element.ALIGN_CENTER);
            sousTitre.setSpacingAfter(20);
            document.add(sousTitre);

            // ── Infos étudiant ──
            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(20);

            addInfoCell(infoTable, "Matricule :", etudiant.getMatricule());
            addInfoCell(infoTable, "Semestre :", semestre);
            addInfoCell(infoTable, "Nom & Prénom :", etudiant.getPrenom() + " " + etudiant.getNom());
            addInfoCell(infoTable, "Statut :", etudiant.getStatut().name());
            document.add(infoTable);

            // ── Tableau des notes ──
            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{5f, 2f, 2f, 3f});

            Font headerFont = new Font(Font.FontFamily.HELVETICA, 11, Font.BOLD, BaseColor.WHITE);
            String[] headers = {"Matière", "Coefficient", "Note /20", "Type"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new BaseColor(4, 41, 84));
                cell.setPadding(8);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            Font dataFont = new Font(Font.FontFamily.HELVETICA, 10);
            boolean alternate = false;
            for (Note note : notes) {
                BaseColor rowColor = alternate ? new BaseColor(240, 240, 240) : BaseColor.WHITE;
                String[] values = {
                    note.getMatiere().getNom(),
                    String.valueOf(note.getMatiere().getCoefficient()),
                    String.format("%.2f", note.getValeur()),
                    note.getTypeNote().name()
                };
                for (String v : values) {
                    PdfPCell cell = new PdfPCell(new Phrase(v, dataFont));
                    cell.setBackgroundColor(rowColor);
                    cell.setPadding(6);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
                alternate = !alternate;
            }
            document.add(table);

            // ── Moyenne ──
            Font moyenneFont = new Font(Font.FontFamily.HELVETICA, 13, Font.BOLD,
                moyenne >= 10 ? new BaseColor(27, 94, 32) : new BaseColor(183, 28, 28));
            Paragraph moyennePara = new Paragraph(
                String.format("\nMoyenne Générale : %.2f / 20", moyenne), moyenneFont);
            moyennePara.setAlignment(Element.ALIGN_RIGHT);
            moyennePara.setSpacingBefore(16);
            document.add(moyennePara);

            Paragraph mention = new Paragraph(
                "Mention : " + getMention(moyenne),
                new Font(Font.FontFamily.HELVETICA, 11, Font.ITALIC, BaseColor.DARK_GRAY));
            mention.setAlignment(Element.ALIGN_RIGHT);
            document.add(mention);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération bulletin", e);
        }
    }

    private void addInfoCell(PdfPTable table, String label, String value) {
        Font labelFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD);
        Font valueFont = new Font(Font.FontFamily.HELVETICA, 10);
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(4);
        valueCell.setPadding(4);
        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private String getMention(Double moyenne) {
        if (moyenne >= 16) return "Très Bien";
        if (moyenne >= 14) return "Bien";
        if (moyenne >= 12) return "Assez Bien";
        if (moyenne >= 10) return "Passable";
        return "Insuffisant";
    }

    private NoteResponse toResponse(Note n) {
        return NoteResponse.builder()
            .id(n.getId())
            .valeur(n.getValeur())
            .typeNote(n.getTypeNote().name())
            .semestre(n.getSemestre().name())
            .commentaire(n.getCommentaire())
            .createdAt(n.getCreatedAt())
            .etudiantId(n.getEtudiant().getId())
            .etudiantNom(n.getEtudiant().getNom())
            .etudiantPrenom(n.getEtudiant().getPrenom())
            .etudiantMatricule(n.getEtudiant().getMatricule())
            .matiereId(n.getMatiere().getId())
            .matiereNom(n.getMatiere().getNom())
            .matiereCoefficient(n.getMatiere().getCoefficient())
            .enseignantNom(n.getEnseignant() != null ?
                n.getEnseignant().getNom() + " " + n.getEnseignant().getPrenom() : null)
            .build();
    }
}