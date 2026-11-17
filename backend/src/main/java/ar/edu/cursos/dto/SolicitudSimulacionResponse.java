package ar.edu.cursos.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SolicitudSimulacionResponse(
        String cursoId,
        String tematica,
        String empresaNombre,
        String estado,
        Integer propuestaVersionVigente,
        List<OffsetDateTime> fechasPosibles,
        List<String> accionesHabilitadas
) {}