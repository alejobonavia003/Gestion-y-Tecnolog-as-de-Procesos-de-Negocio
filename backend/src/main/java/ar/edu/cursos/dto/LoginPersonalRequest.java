package ar.edu.cursos.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginPersonalRequest(
        @NotBlank(message = "Ingresá tu usuario.") String usuarioId,
        @NotBlank(message = "Ingresá tu contraseña.") String clave) {
}
