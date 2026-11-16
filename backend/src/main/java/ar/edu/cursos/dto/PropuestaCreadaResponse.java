package ar.edu.cursos.dto;

import java.util.UUID;

public record PropuestaCreadaResponse(
        UUID propuestaId,
        Integer propuestaVersion,
        boolean pdfDisponible
) {}