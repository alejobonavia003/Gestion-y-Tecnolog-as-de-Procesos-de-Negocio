package ar.edu.cursos.services;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.ActividadCursoResponse;
import ar.edu.cursos.dto.ActividadesCursoResponse;
import ar.edu.cursos.dto.CursoDetalleResponse;
import ar.edu.cursos.dto.CursoResumenResponse;
import ar.edu.cursos.dto.SesionUsuario;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.EstadoCurso;
import ar.edu.cursos.repositories.CursoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CursoService {

    private static final Map<EstadoCurso, String> ETIQUETAS_ESTADO = Map.ofEntries(
            Map.entry(EstadoCurso.SOLICITADO, "Solicitado"),
            Map.entry(EstadoCurso.EN_ACUERDO, "En evaluación o acuerdo"),
            Map.entry(EstadoCurso.EN_PREPARACION, "En preparación"),
            Map.entry(EstadoCurso.CONFIRMADO_PENDIENTE_INICIO, "Confirmado y pendiente de inicio"),
            Map.entry(EstadoCurso.EN_DESARROLLO, "En desarrollo"),
            Map.entry(EstadoCurso.EN_ELABORACION_INFORME, "En elaboración del informe"),
            Map.entry(EstadoCurso.EN_CIERRE, "Cierre en curso"),
            Map.entry(EstadoCurso.CANCELACION_EN_CURSO, "Cancelación en curso"),
            Map.entry(EstadoCurso.FINALIZADO, "Finalizado"),
            Map.entry(EstadoCurso.CANCELADO, "Cancelado"),
            Map.entry(EstadoCurso.RECHAZADO, "Rechazado"),
            Map.entry(EstadoCurso.CERRADO_SIN_RESPUESTA, "Cerrado sin respuesta"),
            Map.entry(EstadoCurso.NO_VIABLE, "No viable")
    );

    private static final Map<String, String> NOMBRES_ACTIVIDADES = Map.of(
            "recibir_nomina", "Recibir nómina",
            "confeccionar_lista", "Confeccionar lista definitiva",
            "evaluar_recursos", "Evaluar recursos",
            "preparar_materiales", "Preparar materiales",
            "definir_docente", "Definir docente"
    );

    private final CursoRepository cursoRepository;
    private final FlowableClient flowableClient;

    public CursoService(CursoRepository cursoRepository, FlowableClient flowableClient) {
        this.cursoRepository = cursoRepository;
        this.flowableClient = flowableClient;
    }

    public Map<String, Object> listarDeEmpresa(SesionUsuario sesion, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.validacion("Paginación inválida (page >= 0, size entre 1 y 100).");
        }
        Page<Curso> pagina = cursoRepository.findByEmpresaIdOrderByCreadoEnDesc(
                sesion.empresaIdNumerico(),
                PageRequest.of(page, size)
        );
        List<CursoResumenResponse> items = pagina.getContent().stream()
                .map(this::aResumen)
                .toList();
        return Map.of(
                "items", items,
                "total", pagina.getTotalElements(),
                "page", page,
                "size", size
        );
    }

    public CursoDetalleResponse detalle(UUID cursoId, SesionUsuario sesion) {
        Curso curso = obtenerYVerificarAcceso(cursoId, sesion);
        return new CursoDetalleResponse(
                curso.getId().toString(),
                curso.getTematica(),
                curso.getEstado().name(),
                etiqueta(curso.getEstado()),
                curso.getFechaCurso(),
                curso.getDescripcion(),
                curso.getCantidadParticipantes()
        );
    }

    public ActividadesCursoResponse actividades(UUID cursoId, SesionUsuario sesion) {
        Curso curso = obtenerYVerificarAcceso(cursoId, sesion);
        if (curso.getEstado() != EstadoCurso.EN_PREPARACION || curso.getProcessInstanceId() == null) {
            return new ActividadesCursoResponse(curso.getEstado().name(), List.of());
        }

        List<ActividadCursoResponse> items = new ArrayList<>();
        Set<String> vistas = new LinkedHashSet<>();

        JsonNode tareas = flowableClient.tareasDeInstancia(curso.getProcessInstanceId());
        for (JsonNode t : tareas.path("data")) {
            String key = t.path("taskDefinitionKey").asText("");
            if (!key.isBlank() && vistas.add(key)) {
                String nombre = NOMBRES_ACTIVIDADES.getOrDefault(key, t.path("name").asText(key));
                items.add(new ActividadCursoResponse(key, nombre, "TAREA_USUARIO"));
            }
        }

        JsonNode ejecuciones = flowableClient.ejecucionesDeInstancia(curso.getProcessInstanceId());
        for (JsonNode e : ejecuciones.path("data")) {
            String activityId = e.path("activityId").asText("");
            if ("recibir_nomina".equals(activityId) && vistas.add(activityId)) {
                items.add(new ActividadCursoResponse(activityId, NOMBRES_ACTIVIDADES.get(activityId), "ESPERA_MENSAJE"));
            }
        }

        return new ActividadesCursoResponse(curso.getEstado().name(), items);
    }

    private Curso obtenerYVerificarAcceso(UUID cursoId, SesionUsuario sesion) {
        Curso curso = cursoRepository.findById(cursoId)
                .orElseThrow(() -> ApiException.noEncontrado("Curso no encontrado."));
        if (sesion.esEmpresa()) {
            Long empresaCursoId = curso.getEmpresa() != null ? curso.getEmpresa().getId() : null;
            if (empresaCursoId == null || !empresaCursoId.equals(sesion.empresaIdNumerico())) {
                throw ApiException.noEncontrado("Curso no encontrado.");
            }
        }
        return curso;
    }

    private CursoResumenResponse aResumen(Curso curso) {
        return new CursoResumenResponse(
                curso.getId().toString(),
                curso.getTematica(),
                curso.getEstado().name(),
                etiqueta(curso.getEstado()),
                curso.getFechaCurso()
        );
    }

    private String etiqueta(EstadoCurso estado) {
        return ETIQUETAS_ESTADO.getOrDefault(estado, estado.name());
    }
}