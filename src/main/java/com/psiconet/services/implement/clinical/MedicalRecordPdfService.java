package com.psiconet.services.implement.clinical;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import com.psiconet.model.dtos.clinical.record.PatientRecordViewDTO;
import com.psiconet.model.dtos.clinical.record.SessionNoteAddendumDTO;
import com.psiconet.model.dtos.clinical.record.SessionNoteDTO;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Gera o PDF do prontuário LIBERADO ao paciente (somente o registro documental). Cada registro sai
 * com autor, CRP, data/hora do servidor e hash SHA-256; o hash do arquivo final é calculado por quem
 * chama e registrado na auditoria. O conteúdo vem do JSON do editor já validado — nunca de HTML.
 */
@Service
public class MedicalRecordPdfService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Color PRIMARY = new Color(37, 99, 235);
    private static final Color MUTED = new Color(100, 116, 139);

    private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, PRIMARY);
    private static final Font H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
    private static final Font LABEL = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.DARK_GRAY);
    private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, MUTED);

    /** Para quem o PDF é gerado: muda título, aviso e a identificação das anotações pessoais. */
    public enum Audience { PATIENT, PSYCHOLOGIST }

    public byte[] render(PatientRecordViewDTO view, UUID exportId) {
        return render(view, exportId, Audience.PATIENT, false);
    }

    public byte[] render(PatientRecordViewDTO view, UUID exportId, Audience audience, boolean includesPrivateNotes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 48, 48, 48, 56);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new com.lowagie.text.pdf.PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter w, Document d) {
                    com.lowagie.text.pdf.ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_CENTER,
                            new Phrase("Documento sigiloso — Psiconet · Exportação " + exportId + " · página " + w.getPageNumber(), SMALL),
                            (d.right() + d.left()) / 2, d.bottom() - 24, 0);
                }
            });
            document.addTitle("Prontuário psicológico");
            document.addCreator("Psiconet");
            document.open();

            boolean forPsychologist = audience == Audience.PSYCHOLOGIST;
            document.add(new Paragraph(forPsychologist
                    ? "Prontuário psicológico — cópia do(a) psicólogo(a) responsável"
                    : "Prontuário psicológico — cópia do(a) titular", TITLE));
            document.add(spacer(6));
            document.add(meta("Paciente", view.patientName()));
            document.add(meta("Psicólogo(a) responsável", view.psychologistName()
                    + (view.psychologistCrp() == null ? "" : " — CRP " + view.psychologistCrp())));
            document.add(meta("Abertura do prontuário", view.openingDate() == null ? "—" : view.openingDate().format(DATE)));
            document.add(meta("Exportado em", LocalDateTime.now().format(DATE_TIME) + " (horário do servidor)"));
            document.add(meta("Identificador da exportação", exportId.toString()));
            document.add(spacer(8));
            String noticeText = !forPsychologist
                    ? "Cópia somente leitura do registro documental, liberada pelo(a) psicólogo(a) a pedido do(a) titular. "
                            + "Anotações pessoais de trabalho do profissional não integram esta cópia. "
                            + "Cada registro traz seu hash SHA-256 de integridade."
                    : includesPrivateNotes
                    ? "Cópia de guarda do(a) psicólogo(a), com registro documental e ANOTAÇÕES PESSOAIS de trabalho. "
                            + "Documento sigiloso: as anotações pessoais não integram a cópia entregue ao paciente. "
                            + "Cada registro traz seu hash SHA-256 de integridade."
                    : "Cópia de guarda do(a) psicólogo(a) com o registro documental (sem anotações pessoais). "
                            + "Cada registro traz seu hash SHA-256 de integridade.";
            Paragraph notice = new Paragraph(noticeText, SMALL);
            document.add(notice);

            section(document, "Avaliação de demanda", view.demand());
            section(document, "Objetivos do trabalho", view.goals());
            section(document, "Histórico", view.history());
            section(document, "Procedimentos técnico-científicos adotados", view.procedures());
            section(document, "Encaminhamento / encerramento", view.closing());

            document.add(spacer(10));
            document.add(new Paragraph("Registros de evolução", H2));
            if (view.notes().isEmpty()) {
                document.add(new Paragraph("Nenhum registro de evolução finalizado.", BODY));
            }
            for (SessionNoteDTO note : view.notes()) {
                document.add(spacer(8));
                String when = note.appointmentStart() != null ? "Sessão de " + note.appointmentStart().format(DATE) : "Registro";
                String kind = note.type() == com.psiconet.model.enums.clinical.SessionNoteTypeEnum.PRIVATE
                        ? "[Anotação pessoal] " : "";
                document.add(new Paragraph(kind + when + (note.title() != null ? " — " + note.title() : ""), LABEL));
                document.add(new Paragraph("Registrado por " + note.authorName()
                        + (note.authorCrp() == null ? "" : " (CRP " + note.authorCrp() + ")")
                        + " em " + (note.finalizedAt() == null ? "—" : note.finalizedAt().format(DATE_TIME)), SMALL));
                renderDocument(document, note.content());
                document.add(new Paragraph("SHA-256: " + note.integrityHash(), SMALL));

                for (SessionNoteAddendumDTO addendum : note.addenda()) {
                    document.add(spacer(4));
                    document.add(new Paragraph("Adendo nº " + addendum.sequence() + " — " + addendum.createdAt().format(DATE_TIME)
                            + " — " + addendum.authorName()
                            + (addendum.authorCrp() == null ? "" : " (CRP " + addendum.authorCrp() + ")"), LABEL));
                    document.add(new Paragraph("Motivo: " + addendum.reason(), BODY));
                    renderDocument(document, addendum.content());
                    document.add(new Paragraph("SHA-256: " + addendum.integrityHash(), SMALL));
                }
            }
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar o PDF do prontuário.");
        }
        return out.toByteArray();
    }

    private static Paragraph meta(String label, String value) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + ": ", LABEL));
        p.add(new Chunk(value == null ? "—" : value, BODY));
        return p;
    }

    private static void section(Document document, String title, String text) throws DocumentException {
        document.add(spacer(10));
        document.add(new Paragraph(title, H2));
        document.add(new Paragraph(text == null || text.isBlank() ? "—" : text, BODY));
    }

    private static Paragraph spacer(float height) {
        Paragraph p = new Paragraph(" ", BODY);
        p.setLeading(height);
        return p;
    }

    // ---- JSON do editor → elementos PDF (somente os nós da whitelist) ----

    private void renderDocument(Document document, JsonNode doc) throws DocumentException {
        if (doc == null) return;
        for (JsonNode block : doc.path("content")) {
            Element element = renderBlock(block);
            if (element != null) document.add(element);
        }
    }

    private Element renderBlock(JsonNode node) {
        String type = node.path("type").asText();
        return switch (type) {
            case "heading" -> {
                int level = node.path("attrs").path("level").asInt(1);
                Paragraph p = inline(node, FontFactory.getFont(FontFactory.HELVETICA_BOLD, level == 1 ? 13 : 11));
                p.setSpacingBefore(6);
                p.setAlignment(alignment(node));
                yield p;
            }
            case "paragraph" -> {
                Paragraph p = inline(node, null);
                p.setAlignment(alignment(node));
                yield p;
            }
            case "bulletList", "orderedList" -> list(node, "orderedList".equals(type));
            default -> null;
        };
    }

    private List list(JsonNode node, boolean ordered) {
        List list = new List(ordered, 14);
        if (!ordered) list.setListSymbol(new Chunk("•  ", BODY));
        for (JsonNode item : node.path("content")) {
            ListItem li = new ListItem();
            li.setFont(BODY);
            for (JsonNode child : item.path("content")) {
                Element element = renderBlock(child);
                if (element != null) li.add(element);
            }
            list.add(li);
        }
        return list;
    }

    private static int alignment(JsonNode node) {
        return switch (node.path("attrs").path("textAlign").asText("")) {
            case "center" -> Element.ALIGN_CENTER;
            case "right" -> Element.ALIGN_RIGHT;
            default -> Element.ALIGN_LEFT;
        };
    }

    /** Tamanho do editor (px, base 16px) → pontos no PDF (base 10pt). Só valores da whitelist. */
    private static Float pdfSize(String px) {
        if (!com.psiconet.infra.richtext.RichTextSanitizer.FONT_SIZES.contains(px)) return null;
        return Float.parseFloat(px.replace("px", "")) * 10f / 16f;
    }

    private Paragraph inline(JsonNode node, Font baseOverride) {
        Paragraph p = new Paragraph();
        p.setLeading(14);
        for (JsonNode child : node.path("content")) {
            if ("hardBreak".equals(child.path("type").asText())) {
                p.add(Chunk.NEWLINE);
                continue;
            }
            int style = Font.NORMAL;
            Float sizeFromMark = null;
            for (JsonNode mark : child.path("marks")) {
                switch (mark.path("type").asText()) {
                    case "bold" -> style |= Font.BOLD;
                    case "italic" -> style |= Font.ITALIC;
                    case "underline" -> style |= Font.UNDERLINE;
                    case "textStyle" -> sizeFromMark = pdfSize(mark.path("attrs").path("fontSize").asText(""));
                    default -> { }
                }
            }
            float size = sizeFromMark != null ? sizeFromMark : baseOverride != null ? baseOverride.getSize() : 10f;
            Font font = baseOverride != null
                    ? FontFactory.getFont(FontFactory.HELVETICA, size, baseOverride.getStyle() | style)
                    : FontFactory.getFont(FontFactory.HELVETICA, size, style);
            p.add(new Chunk(child.path("text").asText(), font));
        }
        return p;
    }
}
