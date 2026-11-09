package cl.duoc.cloudnative.documents;

import java.io.ByteArrayOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

@Component
public class PdfGenerator {

    public byte[] generate(CreateDocumentRequest request) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText("Ticket de Evento");
                contentStream.endText();

                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 650);
                contentStream.showText("Evento: " + request.nombreEvento());
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("ID Evento: " + request.eventoId());
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Usuario: " + request.nombreUsuario());
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("ID Usuario: " + request.usuarioId());
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Fecha: " + request.fecha().toString());
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("Tipo de Ticket: " + request.tipoTicket());
                contentStream.endText();
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }
}
