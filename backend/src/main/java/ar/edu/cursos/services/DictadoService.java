package ar.edu.cursos.services;

import static ar.edu.cursos.services.TareasProceso.*;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.SesionUsuario;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.EstadoCurso;
import ar.edu.cursos.repositories.CursoRepository;

/**
 * Etapa 9: elección del docente (#17) y fin del dictado (#18).
 * El usuario sale siempre de la sesión; la web solo manda el docenteId.
 */
@Service
public class DictadoService {

    private final FlowableClient motor;
    private final CursoRepository cursos;
    private final TareaService tareas;

    public DictadoService(FlowableClient motor, CursoRepository cursos, TareaService tareas) {
        this.motor = motor;
        this.cursos = cursos;
        this.tareas = tareas;
    }

    // --- #17 POST /tareas/{taskId}/docente -----------------------------------

    @Transactional
    public void definirDocente(String taskId, SesionUsuario sesion, String docenteId) {
        JsonNode tarea = tareaDelTipo(taskId, DEFINIR_DOCENTE);
        exigirAsignado(tarea, sesion);
        Curso curso = curso(tarea);

        boolean esDocente = tareas.listarDocentes().stream()
                .anyMatch(d -> docenteId.equals(d.get("usuarioId")));
        if (!esDocente) {
            throw ApiException.validacion("'" + docenteId + "' no es integrante del equipo docente.");
        }

        // desarrollar_curso tiene assignee="${docenteId}": le llega directo a ese docente.
        motor.completar(taskId, Map.of("docenteId", docenteId));

        // Con el docente elegido, el curso queda esperando la fecha (timer "Fecha programa").
        curso.setEstado(EstadoCurso.CONFIRMADO_PENDIENTE_INICIO);
        cursos.save(curso);
    }

    // --- Utilidades compartidas con el paso 2 --------------------------------
    // --- #18 POST /tareas/{taskId}/finalizacion-dictado ----------------------

    @Transactional
    public void finalizarDictado(String taskId, SesionUsuario sesion) {
        JsonNode tarea;
        try {
            tarea = motor.tarea(taskId);
        } catch (ApiException e) {
            // La tarea ya se completó o el timer "Fecha programa" todavía no venció.
            if ("NO_ENCONTRADO".equals(e.getCodigo())) {
                throw ApiException.conflicto("ESTADO_INCOMPATIBLE", "El curso no está en desarrollo.");
            }
            throw e;
        }
        if (!DESARROLLAR_CURSO.equals(tarea.path("taskDefinitionKey").asText())) {
            throw ApiException.conflicto("ESTADO_INCOMPATIBLE", "Esta operación no corresponde a esta tarea.");
        }
        exigirAsignado(tarea, sesion);
        Curso curso = curso(tarea);

        // Sin variables: el proceso sigue a "Elaborar informe".
        motor.completar(taskId, Map.of());

        curso.setEstado(EstadoCurso.EN_ELABORACION_INFORME);
        cursos.save(curso);
    }

    // --- Utilidades -----------------------------------------------------------

    /** Trae la tarea y verifica que sea del tipo esperado (si no, 409). */
    private JsonNode tareaDelTipo(String taskId, String tipo) {
        JsonNode tarea = motor.tarea(taskId);
        if (!tipo.equals(tarea.path("taskDefinitionKey").asText())) {
            throw ApiException.conflicto("ESTADO_INCOMPATIBLE", "Esta operación no corresponde a esta tarea.");
        }
        return tarea;
    }

    private static void exigirAsignado(JsonNode tarea, SesionUsuario sesion) {
        String asignado = tarea.path("assignee").isTextual() ? tarea.get("assignee").asText() : null;
        if (!sesion.usuarioId().equals(asignado)) {
            throw ApiException.sinPermiso("Solo quien tiene asignada la tarea puede completarla.");
        }
    }

    private Curso curso(JsonNode tarea) {
        return cursos.findByProcessInstanceId(tarea.path("processInstanceId").asText())
                .orElseThrow(() -> ApiException.noEncontrado("La tarea no pertenece a un curso de la institución."));
    }
}
