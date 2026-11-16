package ar.edu.cursos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public record NuevaPropuestaRequest(
        @NotBlank(message = "El programa es obligatorio")
        @Size(max = 10000, message = "El programa supera el máximo permitido")
        String programa,

        @NotNull(message = "El presupuesto es obligatorio")
        @Valid
        PresupuestoDto presupuesto,

        @NotEmpty(message = "Debe indicar al menos una fecha posible")
        List<@NotNull OffsetDateTime> fechasPosibles,

        Integer versionBase
) {
    public record PresupuestoDto(
            @NotBlank(message = "El importe es obligatorio")
            @Pattern(regexp = "^\\d+(\\.\\d{1,2})?$", message = "El importe debe ser un número positivo con hasta 2 decimales")
            String importe,

            @NotBlank(message = "La moneda es obligatoria")
            @Pattern(regexp = "^(ARS|USD|EUR)$", message = "La moneda debe ser ARS, USD o EUR")
            String moneda
    ) {}
}