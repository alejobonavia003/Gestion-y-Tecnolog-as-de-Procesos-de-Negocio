package ar.edu.cursos.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.cursos.dto.NuevaSolicitudRequest;
import ar.edu.cursos.dto.SolicitudCreada;
import ar.edu.cursos.services.SolicitudService;
import jakarta.validation.Valid;

/** Endpoint #1 de docs/BACKEND.md (contrato de endpoints). Público: no requiere sesión. */
@RestController
@RequestMapping("/api/v1/solicitudes")
public class SolicitudController {

    private final SolicitudService solicitudes;

    public SolicitudController(SolicitudService solicitudes) {
        this.solicitudes = solicitudes;
    }

    @PostMapping
    public ResponseEntity<SolicitudCreada> crear(@RequestHeader("Idempotency-Key") String clave,
                                                 @Valid @RequestBody NuevaSolicitudRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitudes.registrar(clave.trim(), datos));
    }
}