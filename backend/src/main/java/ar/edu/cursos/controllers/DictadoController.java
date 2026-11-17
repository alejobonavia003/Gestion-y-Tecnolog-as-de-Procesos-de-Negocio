package ar.edu.cursos.controllers;

import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.cursos.dto.DefinirDocenteRequest;
import ar.edu.cursos.services.DictadoService;
import ar.edu.cursos.services.SesionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Endpoints #17 y #18 de ENDPOINTS_WEB.md (etapa 9). */
@RestController
@RequestMapping("/api/v1/tareas")
public class DictadoController {

    private final DictadoService dictado;
    private final SesionService sesiones;

    public DictadoController(DictadoService dictado, SesionService sesiones) {
        this.dictado = dictado;
        this.sesiones = sesiones;
    }

    @PostMapping("/{taskId}/docente")
    public Map<String, Boolean> definirDocente(@PathVariable String taskId,
                                               @Valid @RequestBody DefinirDocenteRequest cuerpo,
                                               HttpServletRequest request) {
        dictado.definirDocente(taskId, sesiones.requerirPersonal(request), cuerpo.docenteId().trim());
        return Map.of("completada", true);
    }

    /** El cuerpo es {} o vacío: el docente sale de la sesión. */
    @PostMapping("/{taskId}/finalizacion-dictado")
    public Map<String, Boolean> finalizarDictado(@PathVariable String taskId, HttpServletRequest request) {
        dictado.finalizarDictado(taskId, sesiones.requerirPersonal(request));
        return Map.of("completada", true);
    }
}
