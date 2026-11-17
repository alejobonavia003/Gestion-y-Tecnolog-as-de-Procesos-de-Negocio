package ar.edu.cursos.controllers;

import ar.edu.cursos.dto.ComunicacionRequest;
import ar.edu.cursos.dto.ComunicacionResponse;
import ar.edu.cursos.dto.SolicitudSimulacionResponse;
import ar.edu.cursos.services.SimulacionService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/simulacion/solicitudes")
public class SimulacionController {

    private final SimulacionService simulacionService;

    public SimulacionController(SimulacionService simulacionService) {
        this.simulacionService = simulacionService;
    }

    @GetMapping
    public Map<String, Object> listar() {
        return simulacionService.listarSolicitudes();
    }

    @GetMapping("/{cursoId}/comunicaciones")
    public SolicitudSimulacionResponse obtenerComunicaciones(@PathVariable UUID cursoId) {
        return simulacionService.obtenerComunicacionesHabilitadas(cursoId);
    }

    @GetMapping("/{cursoId}/propuesta-pdf")
    public ResponseEntity<byte[]> descargarPdfPropuesta(@PathVariable UUID cursoId) {
        byte[] pdf = simulacionService.obtenerPdfPropuestaVigente(cursoId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename("propuesta-empresa.pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @PostMapping("/{cursoId}/comunicaciones")
    public ComunicacionResponse enviarComunicacion(
            @PathVariable UUID cursoId,
            @Valid @RequestBody ComunicacionRequest body
    ) {
        return simulacionService.aplicarComunicacion(cursoId, body);
    }
}