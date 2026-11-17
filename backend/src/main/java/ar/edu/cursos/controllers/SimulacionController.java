package ar.edu.cursos.controllers;

import ar.edu.cursos.dto.ComunicacionRequest;
import ar.edu.cursos.dto.ComunicacionResponse;
import ar.edu.cursos.dto.SolicitudSimulacionResponse;
import ar.edu.cursos.services.SesionService;
import ar.edu.cursos.services.SimulacionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
    private final SesionService sesionService;

    public SimulacionController(SimulacionService simulacionService, SesionService sesionService) {
        this.simulacionService = simulacionService;
        this.sesionService = sesionService;
    }

    @GetMapping
    public Map<String, Object> listar(HttpServletRequest request) {
        sesionService.requerirPersonal(request);
        return simulacionService.listarSolicitudes();
    }

    @GetMapping("/{cursoId}/comunicaciones")
    public SolicitudSimulacionResponse obtenerComunicaciones(
            @PathVariable UUID cursoId,
            HttpServletRequest request
    ) {
        sesionService.requerirPersonal(request);
        return simulacionService.obtenerComunicacionesHabilitadas(cursoId);
    }

    @PostMapping("/{cursoId}/comunicaciones")
    public ComunicacionResponse enviarComunicacion(
            @PathVariable UUID cursoId,
            @Valid @RequestBody ComunicacionRequest body,
            HttpServletRequest request
    ) {
        sesionService.requerirPersonal(request);
        return simulacionService.aplicarComunicacion(cursoId, body);
    }
}