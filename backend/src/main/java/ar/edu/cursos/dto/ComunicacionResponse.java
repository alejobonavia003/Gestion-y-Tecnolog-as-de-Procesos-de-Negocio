package ar.edu.cursos.dto;

public record ComunicacionResponse(
        String comunicacionId,
        String tipo,
        boolean aplicada,
        boolean repetida,
        String cuentaEmpresaUsuario
) {}