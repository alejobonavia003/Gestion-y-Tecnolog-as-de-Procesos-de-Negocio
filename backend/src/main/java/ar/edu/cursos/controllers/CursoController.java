package ar.edu.cursos.controllers;

import ar.edu.cursos.dto.ActividadesCursoResponse;
import ar.edu.cursos.dto.CursoDetalleResponse;
import ar.edu.cursos.services.CursoService;
import ar.edu.cursos.services.SesionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cursos")
public class CursoController {

    private final CursoService cursoService;
    private final SesionService sesionService;

    public CursoController(CursoService cursoService, SesionService sesionService) {
        this.cursoService = cursoService;
        this.sesionService = sesionService;
    }

    @GetMapping
    public Map<String, Object> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request
    ) {
        return cursoService.listarDeEmpresa(sesionService.requerirEmpresa(request), page, size);
    }

    @GetMapping("/{cursoId}")
    public CursoDetalleResponse detalle(
            @PathVariable UUID cursoId,
            HttpServletRequest request
    ) {
        return cursoService.detalle(cursoId, sesionService.requerir(request));
    }

    @GetMapping("/{cursoId}/actividades")
    public ActividadesCursoResponse actividades(
            @PathVariable UUID cursoId,
            HttpServletRequest request
    ) {
        return cursoService.actividades(cursoId, sesionService.requerir(request));
    }
}