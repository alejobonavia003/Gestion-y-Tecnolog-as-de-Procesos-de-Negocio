package ar.edu.cursos.dto;

import java.util.List;

public record ActividadesCursoResponse(
        String estado,
        List<ActividadCursoResponse> items
) {}