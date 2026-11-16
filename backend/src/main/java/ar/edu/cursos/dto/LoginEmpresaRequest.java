package ar.edu.cursos.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginEmpresaRequest(
        @NotBlank(message = "Ingresá el usuario.") String usuario,
        @NotBlank(message = "Ingresá la contraseña.") String clave) {
}
