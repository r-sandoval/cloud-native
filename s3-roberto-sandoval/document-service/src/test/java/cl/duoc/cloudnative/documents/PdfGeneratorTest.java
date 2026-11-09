package cl.duoc.cloudnative.documents;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;

class PdfGeneratorTest {

    @Test
    void generatesPdf() throws Exception {
        byte[] bytes = new PdfGenerator().generate(new CreateDocumentRequest(
                "evento01",
                "usuario01",
                "Concierto Rock",
                "Juan Pérez",
                LocalDate.of(2026, 11, 4),
                "VIP"
        ));
        assertTrue(bytes.length > 200);
        assertEquals("%PDF-", new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
    }
}
