package ar.edu.cursos.simulador;

import java.util.concurrent.ExecutionException;

public final class ErrorAplicacion extends RuntimeException {

    public ErrorAplicacion(String mensaje) {
        super(mensaje);
    }

    public static String mensaje(Throwable error) {
        Throwable actual = error instanceof ExecutionException && error.getCause() != null
                ? error.getCause()
                : error;
        if (actual instanceof ErrorAplicacion) {
            return actual.getMessage();
        }
        return "Ocurrió un error al comunicarse con el backend: " + actual.getMessage();
    }
}
