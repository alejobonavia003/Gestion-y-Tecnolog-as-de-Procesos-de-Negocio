package ar.edu.cursos.dto;

import jakarta.validation.constraints.NotBlank;

/** Cuerpo de POST /tareas/{taskId}/docente. */
public record DefinirDocenteRequest(
        @NotBlank(message = "Elegí un docente")
        String docenteId) {
}
