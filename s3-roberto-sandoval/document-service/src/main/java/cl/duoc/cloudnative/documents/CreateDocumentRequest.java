package cl.duoc.cloudnative.documents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CreateDocumentRequest(
        @NotBlank String eventoId,
        @NotBlank String usuarioId,
        @NotBlank String nombreEvento,
        @NotBlank String nombreUsuario,
        @NotNull LocalDate fecha,
        @NotBlank String tipoTicket
) {}
