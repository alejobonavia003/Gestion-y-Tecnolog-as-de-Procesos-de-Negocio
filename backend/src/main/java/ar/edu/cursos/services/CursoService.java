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
            "reservar_aula_virtual", "Reservar aula virtual",
            "contratar_equipamiento", "Contratar equipamiento",
            "definir_docente", "Definir docente"
    );

    private static final Set<EstadoCurso> ESTADOS_SINCRONIZABLES = Set.of(
            EstadoCurso.EN_PREPARACION,
            EstadoCurso.CONFIRMADO_PENDIENTE_INICIO,
            EstadoCurso.EN_DESARROLLO,
            EstadoCurso.EN_ELABORACION_INFORME,
            EstadoCurso.EN_CIERRE
    );

    private final CursoRepository cursoRepository;
    private final FlowableClient flowableClient;

    public CursoService(CursoRepository cursoRepository, FlowableClient flowableClient) {
        this.cursoRepository = cursoRepository;
        this.flowableClient = flowableClient;
    }

    @Transactional
    public Map<String, Object> listarDeEmpresa(SesionUsuario sesion, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.validacion("Paginación inválida (page >= 0, size entre 1 y 100).");
        }
        Page<Curso> pagina = cursoRepository.findByEmpresaIdOrderByCreadoEnDesc(
                sesion.empresaIdNumerico(),
                PageRequest.of(page, size)
        );
        pagina.getContent().forEach(this::sincronizarEstadoSiCorresponde);
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

    @Transactional
    public CursoDetalleResponse detalle(UUID cursoId, SesionUsuario sesion) {
        Curso curso = obtenerYVerificarAcceso(cursoId, sesion);
        sincronizarEstadoSiCorresponde(curso);
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

    private void sincronizarEstadoSiCorresponde(Curso curso) {
        String pid = curso.getProcessInstanceId();
        if (pid == null || curso.getEstado() == null || !ESTADOS_SINCRONIZABLES.contains(curso.getEstado())) {
            return;
        }
        try {
            JsonNode tareas = flowableClient.tareasDeInstancia(pid);
            if (tareas == null || !tareas.path("data").isArray()) {
                return;
            }
            Set<String> keys = new LinkedHashSet<>();
            for (JsonNode t : tareas.path("data")) {
                String k = t.path("taskDefinitionKey").asText("");
                if (!k.isBlank()) keys.add(k);
            }

            EstadoCurso nuevo = null;
            if (keys.contains(TareasProceso.DEFINIR_DOCENTE)) {
                nuevo = EstadoCurso.CONFIRMADO_PENDIENTE_INICIO;
            } else if (keys.contains(TareasProceso.DESARROLLAR_CURSO)) {
                nuevo = EstadoCurso.EN_DESARROLLO;
            } else if (keys.contains(TareasProceso.ELABORAR_INFORME)) {
                nuevo = EstadoCurso.EN_ELABORACION_INFORME;
            } else if (keys.contains(TareasProceso.ARCHIVAR_EXPEDIENTE)
                    || keys.contains(TareasProceso.ENVIAR_INFORME)
                    || (curso.getEstado() != EstadoCurso.EN_PREPARACION && keys.contains("liberar_equipamiento"))) {
                nuevo = EstadoCurso.EN_CIERRE;
            } else if (keys.isEmpty()) {
                JsonNode ejecuciones = flowableClient.ejecucionesDeInstancia(pid);
                if (ejecuciones != null && ejecuciones.path("data").isArray()) {
                    boolean esperandoFecha = false;
                    for (JsonNode e : ejecuciones.path("data")) {
                        if ("fecha_programa".equals(e.path("activityId").asText(""))) {
                            esperandoFecha = true;
                            break;
                        }
                    }
                    if (esperandoFecha) {
                        nuevo = EstadoCurso.CONFIRMADO_PENDIENTE_INICIO;
                    } else if (ejecuciones.path("data").isEmpty()) {
                        nuevo = flowableClient.variableHistorica(pid, "resultadoProceso")
                                .map(this::mapearResultadoFinal)
                                .orElse(null);
                    }
                }
            }

            if (nuevo != null && nuevo != curso.getEstado()) {
                curso.setEstado(nuevo);
                cursoRepository.save(curso);
            }
        } catch (ApiException ignored) {
            // Si el motor no está disponible en la lectura, se conserva el último estado persistido.
        }
    }

    private EstadoCurso mapearResultadoFinal(String valor) {
        if (valor == null) return null;
        return switch (valor.trim().toUpperCase()) {
            case "FINALIZADO" -> EstadoCurso.FINALIZADO;
            case "CANCELADO" -> EstadoCurso.CANCELADO;
            case "RECHAZADO" -> EstadoCurso.RECHAZADO;
            case "CERRADO_SIN_RESPUESTA", "SIN_RESPUESTA" -> EstadoCurso.CERRADO_SIN_RESPUESTA;
            case "NO_VIABLE" -> EstadoCurso.NO_VIABLE;
            default -> null;
        };
    }
}