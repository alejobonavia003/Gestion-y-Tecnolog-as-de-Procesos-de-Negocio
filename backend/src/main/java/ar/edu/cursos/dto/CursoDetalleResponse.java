package ar.edu.cursos.dto;

import java.time.OffsetDateTime;

public record CursoDetalleResponse(
        String cursoId,
        String nombre,
        String estado,
        String estadoEtiqueta,
        OffsetDateTime fechaCurso,
        String descripcion,
        int cantidadParticipantes
) {}