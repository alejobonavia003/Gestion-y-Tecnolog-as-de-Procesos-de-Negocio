package ar.edu.cursos.dto;

import java.time.OffsetDateTime;

public record CursoResumenResponse(
        String cursoId,
        String nombre,
        String estado,
        String estadoEtiqueta,
        OffsetDateTime fechaCurso
) {}    