package com.academique.backend.service;

import com.academique.backend.dto.request.NoteRequest;
import com.academique.backend.dto.response.NoteResponse;
import com.academique.backend.entity.*;
import com.academique.backend.exception.ResourceNotFoundException;
import com.academique.backend.repository.*;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;
    private final EtudiantRepository etudiantRepository;
    private final MatiereRepository matiereRepository;
    private final EnseignantRepository enseignantRepository;
    private final Optional<JavaMailSender> mailSender;

    public NoteResponse create(NoteRequest request) {
        assertCurrentUserCanManageNotes();

        Etudiant etudiant = etudiantRepository.findById(request.getEtudiantId())
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        Matiere matiere = matiereRepository.findById(request.getMatiereId())
            .orElseThrow(() -> new ResourceNotFoundException("Matière non trouvée"));

        Note note = new Note();
        note.setValeur(request.getValeur());
        note.setEtudiant(etudiant);
        note.setMatiere(matiere);
        note.setTypeNote(parseTypeNote(request.getTypeNote()));
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
        assertCurrentUserCanManageNotes();

        Note note = noteRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Note non trouvée"));
        note.setValeur(request.getValeur());
        note.setCommentaire(request.getCommentaire());
        if (request.getTypeNote() != null)
            note.setTypeNote(parseTypeNote(request.getTypeNote()));
        if (request.getSemestre() != null)
            note.setSemestre(Note.Semestre.valueOf(request.getSemestre()));
        return toResponse(noteRepository.save(note));
    }
    public List<NoteResponse> getByEtudiantEmail(String email) {
        Etudiant etudiant = etudiantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        return noteRepository.findByEtudiantId(etudiant.getId()).stream().map(this::toResponse).toList();
    }

    public void delete(Long id) {
        assertCurrentUserCanManageNotes();

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
        return buildBulletinPdf(etudiant, notes, "BULLETIN DE NOTES - " + semestre);
    }

    public byte[] exportBulletinPdfComplet(Long etudiantId) {
        Etudiant etudiant = etudiantRepository.findById(etudiantId)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        List<Note> notes = noteRepository.findByEtudiantId(etudiantId).stream()
            .sorted(Comparator
                .comparingInt((Note n) -> semestreOrder(n.getSemestre()))
                .thenComparing(n -> n.getMatiere() != null && n.getMatiere().getNom() != null ? n.getMatiere().getNom() : ""))
            .toList();

        return buildBulletinPdf(etudiant, notes, "RELEVE DE NOTES COMPLET");
    }

    public Map<String, Object> envoyerBulletinsParNiveau(String niveauCode) {
        String normalizedNiveau = normalizeNiveauCode(niveauCode);
        List<Etudiant> etudiants = etudiantRepository.findByNiveauCode(normalizedNiveau);

        if (etudiants.isEmpty()) {
            throw new IllegalArgumentException("Aucun étudiant trouvé pour le niveau " + normalizedNiveau);
        }

        if (mailSender.isEmpty()) {
            Map<String, Object> noMailResult = new HashMap<>();
            noMailResult.put("niveauCode", normalizedNiveau);
            noMailResult.put("total", etudiants.size());
            noMailResult.put("sent", 0);
            noMailResult.put("failed", 0);
            noMailResult.put("skipped", etudiants.size());
            noMailResult.put("message", "Envoi email indisponible: configurez spring.mail.*");
            return noMailResult;
        }

        JavaMailSender sender = mailSender.get();

        int sent = 0;
        int failed = 0;
        int skipped = 0;

        for (Etudiant etudiant : etudiants) {
            if (etudiant.getEmail() == null || etudiant.getEmail().isBlank()) {
                skipped++;
                continue;
            }

            try {
                byte[] pdf = exportBulletinPdfComplet(etudiant.getId());

                MimeMessage message = sender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setTo(etudiant.getEmail());
                helper.setSubject("Votre bulletin - " + normalizedNiveau);
                helper.setText(
                    "Bonjour " + etudiant.getPrenom() + ",\n\n"
                        + "Veuillez trouver ci-joint votre bulletin de notes au format PDF.\n\n"
                        + "Cordialement,\nAdministration",
                    false
                );
                helper.addAttachment(
                    "bulletin_" + etudiant.getMatricule() + ".pdf",
                    new ByteArrayResource(pdf)
                );

                sender.send(message);
                sent++;
            } catch (Exception e) {
                failed++;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("niveauCode", normalizedNiveau);
        result.put("total", etudiants.size());
        result.put("sent", sent);
        result.put("failed", failed);
        result.put("skipped", skipped);
        result.put("message", "Envoi des bulletins terminé");
        return result;
    }

    public byte[] exportBulletinPdfCompletByEmail(String email) {
        Etudiant etudiant = etudiantRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException("Étudiant non trouvé"));
        return exportBulletinPdfComplet(etudiant.getId());
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

    private String normalizeNiveauCode(String niveauCode) {
        if (niveauCode == null || niveauCode.isBlank()) {
            throw new IllegalArgumentException("Le niveau est obligatoire");
        }
        return niveauCode.trim().toUpperCase(Locale.ROOT);
    }

    private byte[] buildBulletinPdf(Etudiant etudiant, List<Note> notes, String title) {
        List<MatiereBulletinRow> rows = buildMatiereRows(notes);

        double totalPondere = 0.0;
        double totalCoeff = 0.0;
        for (MatiereBulletinRow row : rows) {
            totalPondere += row.moyenne * row.coefficient;
            totalCoeff += row.coefficient;
        }
        double moyenneGenerale = totalCoeff > 0
            ? Math.round((totalPondere / totalCoeff) * 100.0) / 100.0
            : 0.0;

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD, new BaseColor(4, 41, 84));
            Font subFont = new Font(Font.FontFamily.HELVETICA, 11, Font.NORMAL, BaseColor.DARK_GRAY);

            Paragraph titre = new Paragraph(title, titleFont);
            titre.setAlignment(Element.ALIGN_CENTER);
            titre.setSpacingAfter(4);
            document.add(titre);

            Paragraph sousTitre = new Paragraph("Système de Gestion Académique", subFont);
            sousTitre.setAlignment(Element.ALIGN_CENTER);
            sousTitre.setSpacingAfter(18);
            document.add(sousTitre);

            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setSpacingAfter(16);
            addInfoCell(infoTable, "Matricule :", etudiant.getMatricule());
            addInfoCell(infoTable, "Nom & Prénom :", etudiant.getPrenom() + " " + etudiant.getNom());
            addInfoCell(infoTable, "Statut :", etudiant.getStatut().name());
            addInfoCell(infoTable, "Total matières :", String.valueOf(rows.size()));
            document.add(infoTable);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{4.2f, 1.2f, 1.2f, 1.2f, 1.4f, 1.8f});

            Font headerFont = new Font(Font.FontFamily.HELVETICA, 10, Font.BOLD, BaseColor.WHITE);
            String[] headers = {"Matière", "Coeff", "DS", "TP", "EXAMEN", "Moyenne"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                cell.setBackgroundColor(new BaseColor(4, 41, 84));
                cell.setPadding(7);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            Font dataFont = new Font(Font.FontFamily.HELVETICA, 9);
            boolean alternate = false;
            for (MatiereBulletinRow row : rows) {
                BaseColor rowColor = alternate ? new BaseColor(245, 245, 245) : BaseColor.WHITE;
                String[] values = {
                    row.matiereNom,
                    String.format("%.2f", row.coefficient),
                    row.ds == null ? "-" : String.format("%.2f", row.ds),
                    row.tp == null ? "-" : String.format("%.2f", row.tp),
                    row.examen == null ? "-" : String.format("%.2f", row.examen),
                    String.format("%.2f", row.moyenne)
                };

                for (String value : values) {
                    PdfPCell cell = new PdfPCell(new Phrase(value, dataFont));
                    cell.setBackgroundColor(rowColor);
                    cell.setPadding(6);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    table.addCell(cell);
                }
                alternate = !alternate;
            }

            document.add(table);

            Font moyenneFont = new Font(
                Font.FontFamily.HELVETICA,
                13,
                Font.BOLD,
                moyenneGenerale >= 10 ? new BaseColor(27, 94, 32) : new BaseColor(183, 28, 28)
            );
            Paragraph moyennePara = new Paragraph(
                String.format("\nMoyenne Générale : %.2f / 20", moyenneGenerale),
                moyenneFont
            );
            moyennePara.setAlignment(Element.ALIGN_RIGHT);
            moyennePara.setSpacingBefore(12);
            document.add(moyennePara);

            Paragraph mention = new Paragraph(
                "Mention : " + getMention(moyenneGenerale),
                new Font(Font.FontFamily.HELVETICA, 11, Font.ITALIC, BaseColor.DARK_GRAY)
            );
            mention.setAlignment(Element.ALIGN_RIGHT);
            document.add(mention);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération bulletin", e);
        }
    }

    private List<MatiereBulletinRow> buildMatiereRows(List<Note> notes) {
        Map<String, MatiereBulletinAccumulator> map = new HashMap<>();

        for (Note note : notes) {
            if (note.getMatiere() == null) continue;

            String semestre = note.getSemestre() != null ? note.getSemestre().name() : "";
            String key = semestre + "::" + note.getMatiere().getId();
            MatiereBulletinAccumulator acc = map.computeIfAbsent(
                key,
                k -> new MatiereBulletinAccumulator(
                    note.getMatiere().getNom(),
                    note.getMatiere().getCoefficient() != null ? note.getMatiere().getCoefficient() : 1.0,
                    note.getSemestre()
                )
            );

            if (note.getValeur() == null || note.getTypeNote() == null) {
                continue;
            }

            switch (note.getTypeNote()) {
                case CONTROLE -> {
                    acc.dsSum += note.getValeur();
                    acc.dsCount++;
                }
                case TP -> {
                    acc.tpSum += note.getValeur();
                    acc.tpCount++;
                }
                case EXAMEN -> {
                    acc.examSum += note.getValeur();
                    acc.examCount++;
                }
                default -> {
                    // PROJET non utilisé dans la formule demandée
                }
            }
        }

        return map.values().stream()
            .map(acc -> {
                Double ds = acc.dsCount > 0 ? acc.dsSum / acc.dsCount : null;
                Double tp = acc.tpCount > 0 ? acc.tpSum / acc.tpCount : null;
                Double exam = acc.examCount > 0 ? acc.examSum / acc.examCount : null;

                double moyenne = (exam == null ? 0.0 : exam * 0.7)
                    + (ds == null ? 0.0 : ds * 0.15)
                    + (tp == null ? 0.0 : tp * 0.15);

                double moyenneRounded = Math.round(moyenne * 100.0) / 100.0;
                return new MatiereBulletinRow(acc.matiereNom, acc.coefficient, ds, tp, exam, moyenneRounded, acc.semestre);
            })
            .sorted(Comparator
                .comparingInt((MatiereBulletinRow r) -> semestreOrder(r.semestre))
                .thenComparing(r -> r.matiereNom))
            .toList();
    }

    private Note.TypeNote parseTypeNote(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return Note.TypeNote.EXAMEN;
        }

        String normalized = rawType.trim().toUpperCase(Locale.ROOT);
        if ("DS".equals(normalized)) {
            return Note.TypeNote.CONTROLE;
        }

        return Note.TypeNote.valueOf(normalized);
    }

    private int semestreOrder(Note.Semestre semestre) {
        if (semestre == null) return 99;
        return switch (semestre) {
            case S1 -> 1;
            case S2 -> 2;
            case S3 -> 3;
            case S4 -> 4;
            case S5 -> 5;
        };
    }

    private String resolveEnseignantNom(Note n) {
        if (n.getEnseignant() != null) {
            return n.getEnseignant().getNom() + " " + n.getEnseignant().getPrenom();
        }
        if (n.getMatiere() != null && n.getMatiere().getEnseignant() != null) {
            return n.getMatiere().getEnseignant().getNom() + " " + n.getMatiere().getEnseignant().getPrenom();
        }
        return null;
    }

    private void assertCurrentUserCanManageNotes() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new AccessDeniedException("Accès refusé");
        }

        boolean isAdmin = auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) {
            return;
        }

        boolean isEnseignant = auth.getAuthorities().stream()
            .anyMatch(a -> "ROLE_ENSEIGNANT".equals(a.getAuthority()));
        if (!isEnseignant) {
            throw new AccessDeniedException("Accès refusé");
        }

        Enseignant enseignant = enseignantRepository.findByEmail(auth.getName())
            .orElseThrow(() -> new ResourceNotFoundException("Enseignant non trouvé"));

        if (!enseignant.isCanManageNotes()) {
            throw new AccessDeniedException("L'administration n'a pas encore autorisé la gestion des notes pour votre compte");
        }
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
            .enseignantNom(resolveEnseignantNom(n))
            .build();
    }

    private static class MatiereBulletinAccumulator {
        private final String matiereNom;
        private final double coefficient;
        private final Note.Semestre semestre;
        private double dsSum = 0.0;
        private int dsCount = 0;
        private double tpSum = 0.0;
        private int tpCount = 0;
        private double examSum = 0.0;
        private int examCount = 0;

        private MatiereBulletinAccumulator(String matiereNom, double coefficient, Note.Semestre semestre) {
            this.matiereNom = matiereNom;
            this.coefficient = coefficient;
            this.semestre = semestre;
        }
    }

    private static class MatiereBulletinRow {
        private final String matiereNom;
        private final double coefficient;
        private final Double ds;
        private final Double tp;
        private final Double examen;
        private final double moyenne;
        private final Note.Semestre semestre;

        private MatiereBulletinRow(String matiereNom, double coefficient, Double ds, Double tp, Double examen, double moyenne, Note.Semestre semestre) {
            this.matiereNom = matiereNom;
            this.coefficient = coefficient;
            this.ds = ds;
            this.tp = tp;
            this.examen = examen;
            this.moyenne = moyenne;
            this.semestre = semestre;
        }
    }
}