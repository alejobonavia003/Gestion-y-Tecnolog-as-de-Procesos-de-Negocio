package ar.edu.cursos.exceptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorApi> api(ApiException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorApi(e.getCodigo(), e.getMessage()));
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorApi> validacion(MethodArgumentNotValidException e) {
        FieldError campo = e.getBindingResult().getFieldError();
        String mensaje = campo != null
                ? campo.getField() + ": " + campo.getDefaultMessage()
                : "Los datos enviados no son válidos.";
        return ResponseEntity.badRequest().body(new ErrorApi("VALIDACION", mensaje));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorApi> cuerpoInvalido(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorApi("VALIDACION", "El cuerpo de la solicitud no es válido."));
    }

    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApi> otro(Exception e) {
        if (e instanceof ErrorResponse respuestaSpring) {
            int status = respuestaSpring.getStatusCode().value();
            String codigo = switch (status) {
                case 400 -> "VALIDACION";
                case 404 -> "NO_ENCONTRADO";
                case 405 -> "METODO_NO_PERMITIDO";
                default -> "ERROR";
            };
            return ResponseEntity.status(status).body(new ErrorApi(codigo, e.getMessage()));
        }
        log.error("Error no controlado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorApi("ERROR_INTERNO", "Ocurrió un error inesperado."));
    }
}