package ar.edu.cursos.controllers;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import ar.edu.cursos.dto.*;
import ar.edu.cursos.services.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/tareas")
public class TareaController {
    private final TareaService tareas;
    private final SesionService sesiones;
    public TareaController(TareaService tareas, SesionService sesiones) { this.tareas = tareas; this.sesiones = sesiones; }

    @GetMapping
    public Map<String, Object> listar(@RequestParam(defaultValue = "asignadas") String bandeja,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, HttpServletRequest request) {
        return tareas.listar(sesiones.requerirPersonal(request), bandeja, page, size);
    }
    @GetMapping("/{id}")
    public TareaDetalle detalle(@PathVariable String id, HttpServletRequest request) {
        return tareas.detalle(id, sesiones.requerirPersonal(request));
    }
    @PostMapping("/{id}/reclamo")
    public TareaResumen reclamar(@PathVariable String id, HttpServletRequest request) {
        return tareas.reclamar(id, sesiones.requerirPersonal(request));
    }
    @PostMapping("/{id}/completado")
    public Map<String, Object> completar(@PathVariable String id, @RequestBody JsonNode cuerpo, HttpServletRequest request) {
        return tareas.completar(id, sesiones.requerirPersonal(request), cuerpo);
    }
}
