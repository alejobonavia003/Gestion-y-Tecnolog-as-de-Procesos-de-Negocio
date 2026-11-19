package ar.edu.cursos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Cuerpo de POST /solicitudes (formulario público). Ver docs/BACKEND.md (contrato de endpoints) #1. */
public record NuevaSolicitudRequest(
        @NotNull(message = "Faltan los datos de la empresa.") @Valid Empresa empresa,
        @NotNull(message = "Faltan los datos de contacto.") @Valid Contacto contacto,
        @NotBlank(message = "Indicá la temática.") @Size(max = 200) String tematica,
        @NotBlank(message = "Contanos qué necesitan.")
        @Size(min = 10, max = 4000, message = "La descripción debe tener entre 10 y 4000 caracteres.") String descripcion,
        @NotNull(message = "Indicá la cantidad de participantes.")
        @Min(value = 1, message = "Tiene que haber al menos 1 participante.")
        @Max(value = 1000, message = "La cantidad de participantes es demasiado alta.") Integer cantidadEstimadaParticipantes,
        @NotBlank(message = "Indicá las fechas preferidas.") @Size(max = 500) String fechasPreferidas) {

    public record Empresa(
            @NotBlank(message = "Indicá el nombre de la empresa.") @Size(max = 200) String nombre,
            @Size(max = 50) String identificador) {
    }

    public record Contacto(
            @NotBlank(message = "Indicá el nombre del contacto.") @Size(max = 200) String nombre,
            @NotBlank(message = "Indicá un email.") @Email(message = "El email no es válido.") String email,
            @Size(max = 50) String telefono) {
    }
}