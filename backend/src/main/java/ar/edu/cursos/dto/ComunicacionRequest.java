package ar.edu.cursos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;

public record ComunicacionRequest(
        @NotBlank(message = "El comunicacionId es obligatorio")
        String comunicacionId,

        @NotBlank(message = "El tipo de comunicación es obligatorio")
        String tipo,

        Integer propuestaVersion,
        OffsetDateTime fechaCurso,
        String observacionesEmpresa,

        @Valid
        List<ParticipanteDto> participantes
) {
    public record ParticipanteDto(
            @NotBlank(message = "Cada participante debe tener nombre")
            String nombre
    ) {}
}