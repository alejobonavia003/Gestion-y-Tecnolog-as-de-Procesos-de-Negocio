package ar.edu.cursos.exceptions;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String codigo;

    public ApiException(HttpStatus status, String codigo, String mensaje) {
        super(mensaje);
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

   

    public static ApiException validacion(String mensaje) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDACION", mensaje);
    }

    public static ApiException noAutenticado() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Iniciá sesión para continuar.");
    }

    public static ApiException sinPermiso(String mensaje) {
        return new ApiException(HttpStatus.FORBIDDEN, "SIN_PERMISO", mensaje);
    }

    public static ApiException noEncontrado(String mensaje) {
        return new ApiException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", mensaje);
    }

    public static ApiException conflicto(String codigo, String mensaje) {
        return new ApiException(HttpStatus.CONFLICT, codigo, mensaje);
    }

    public static ApiException motorNoDisponible() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MOTOR_NO_DISPONIBLE",
                "El sistema de procesos no está disponible. Probá de nuevo en unos minutos.");
    }
}