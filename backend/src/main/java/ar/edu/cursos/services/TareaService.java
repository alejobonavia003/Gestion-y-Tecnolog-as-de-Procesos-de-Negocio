package ar.edu.cursos.services;

import static ar.edu.cursos.services.TareasProceso.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.*;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.repositories.*;

@Service
@Transactional(readOnly = true)
public class TareaService {
    private final FlowableClient motor;
    private final CursoRepository cursos;
    private final PropuestaRepository propuestas;

    public TareaService(FlowableClient motor, CursoRepository cursos, PropuestaRepository propuestas) {
        this.motor = motor; this.cursos = cursos; this.propuestas = propuestas;
    }

    public Map<String, Object> listar(SesionUsuario usuario, String bandeja, int page, int size) {
        if (!Set.of("asignadas", "disponibles").contains(bandeja) || page < 0 || size < 1 || size > 100)
            throw ApiException.validacion("Bandeja o paginación inválida (size entre 1 y 100).");
        boolean disponibles = bandeja.equals("disponibles");
        if (disponibles && usuario.grupos().isEmpty())
            return Map.of("items", List.of(), "total", 0, "page", page, "size", size);
        JsonNode respuesta = motor.tareas(usuario.usuarioId(), usuario.grupos(), disponibles, page, size);
        List<TareaResumen> items = new ArrayList<>();
        for (JsonNode tarea : respuesta.path("data")) {
            // Verificar también cada resultado, sin confiar solo en el filtro remoto.
            autorizar(tarea, usuario);
            items.add(resumen(tarea, curso(tarea)));
        }
        return Map.of("items", items, "total", respuesta.path("total").asLong(), "page", page, "size", size);
    }

    public TareaDetalle detalle(String id, SesionUsuario usuario) {
        JsonNode tarea = motor.tarea(id);
        autorizar(tarea, usuario);
        Curso curso = curso(tarea);
        Map<String, Object> variables = new LinkedHashMap<>();
        motor.variables(curso.getProcessInstanceId()).forEach(v -> variables.put(v.path("name").asText(), v.get("value")));
        Map<String, Object> contexto = new LinkedHashMap<>();
        String key = tarea.path("taskDefinitionKey").asText();
        if (Set.of(PROPUESTA, REVISION).contains(key)) {
            contexto.put("solicitud", Map.of("tematica", curso.getTematica(), "descripcion", curso.getDescripcion(),
                    "cantidadEstimadaParticipantes", curso.getCantidadParticipantes(), "fechasPreferidas", curso.getFechasPreferidas()));
        }
        if (REVISION.equals(key)) {
            contexto.put("observacionesEmpresa", variables.get("observacionesEmpresa"));
            propuestas.findFirstByCursoIdOrderByVersionDesc(curso.getId()).ifPresent(p -> {
                Map<String, Object> propuesta = new LinkedHashMap<>();
                propuesta.put("version", p.getVersion()); propuesta.put("programa", p.getPrograma());
                propuesta.put("presupuesto", Map.of("importe", p.getPresupuestoImporte().toPlainString(), "moneda", p.getPresupuestoMoneda()));
                propuesta.put("fechasPosibles", p.getFechasPosibles()); propuesta.put("estado", p.getEstado());
                propuesta.put("observacionesEmpresa", p.getObservacionesEmpresa()); propuesta.put("creada", p.getCreadaEn());
                contexto.put("propuestaVigente", propuesta);
            });
        }
        if (LISTA.equals(key)) contexto.put("participantes", variables.getOrDefault("participantes", List.of()));
        if (ARCHIVAR_SOLICITUD.equals(key)) {
            Object respuesta = variables.get("respuestaEmpresa");
            contexto.put("motivoArchivo", respuesta instanceof JsonNode n && "RECHAZADA".equals(n.asText()) ? "RECHAZADA" : "SIN_RESPUESTA");
        }
        if (Set.of(ENVIAR_INFORME, ARCHIVAR_EXPEDIENTE, "desarrollar_curso", "definir_docente").contains(key)) {
            Map<String, Object> datos = new LinkedHashMap<>();
            datos.put("descripcion", curso.getDescripcion()); datos.put("cantidadParticipantes", curso.getCantidadParticipantes());
            datos.put("fechaCurso", curso.getFechaCurso()); contexto.put("curso", datos);
            if (Set.of(ENVIAR_INFORME, ARCHIVAR_EXPEDIENTE).contains(key)) {
                Map<String, Object> informe = new LinkedHashMap<>();
                informe.put("resumen", curso.getInformeResumen()); informe.put("pdfDisponible", false); contexto.put("informe", informe);
            }
        }
        return new TareaDetalle(resumen(tarea, curso), contexto);
    }

    public TareaResumen reclamar(String id, SesionUsuario usuario) {
        JsonNode tarea = motor.tarea(id);
        autorizar(tarea, usuario);
        curso(tarea);
        if (asignado(tarea) != null) throw ApiException.conflicto("YA_RECLAMADA", "La tarea ya fue reclamada.");
        motor.reclamar(id, usuario.usuarioId());
        JsonNode reclamada = motor.tarea(id);
        return resumen(reclamada, curso(reclamada));
    }

    public void completar(String id, SesionUsuario usuario, JsonNode cuerpo) {
        JsonNode tarea = motor.tarea(id);
        if (!usuario.usuarioId().equals(asignado(tarea))) throw ApiException.sinPermiso("Solo el asignado puede completar la tarea.");
        curso(tarea);
        String key = tarea.path("taskDefinitionKey").asText();
        if (!Set.of(LISTA, RECURSOS, ARCHIVAR_SOLICITUD, ENVIAR_INFORME, ARCHIVAR_EXPEDIENTE).contains(key))
            throw ApiException.conflicto("ESTADO_INCOMPATIBLE", "Esta tarea no admite completado simple; usá su operación específica.");
        if (cuerpo == null || !cuerpo.isObject() || cuerpo.size() != 1 || !cuerpo.path("datos").isObject())
            throw ApiException.validacion("Enviá un objeto datos.");
        JsonNode datos = cuerpo.get("datos");
        Map<String, Object> variables = new LinkedHashMap<>();
        if (LISTA.equals(key)) {
            soloCampos(datos, Set.of("participantes"));
            JsonNode participantes = datos.path("participantes");
            if (!participantes.isArray() || participantes.isEmpty()) throw ApiException.validacion("Debe haber al menos un participante.");
            for (JsonNode p : participantes) {
                if (!p.isObject() || !p.path("nombre").isTextual() || p.path("nombre").asText().isBlank())
                    throw ApiException.validacion("Cada participante debe tener nombre.");
                soloCampos(p, Set.of("nombre"));
            }
            variables.put("participantes", participantes);
        } else if (RECURSOS.equals(key)) {
            Set<String> campos = Set.of("requiereAula", "requierePlataforma", "requiereEquipamiento");
            soloCampos(datos, campos);
            boolean alguno = false;
            for (String campo : campos) {
                if (!datos.path(campo).isBoolean()) throw ApiException.validacion("Indicá los tres recursos con true o false.");
                boolean valor = datos.get(campo).asBoolean(); alguno |= valor; variables.put(campo, valor);
            }
            if (!alguno) throw ApiException.validacion("Seleccioná al menos un recurso.");
        } else soloCampos(datos, Set.of());
        motor.completar(id, variables);
    }

    private void soloCampos(JsonNode datos, Set<String> campos) {
        datos.fieldNames().forEachRemaining(c -> {
            if (!campos.contains(c)) throw ApiException.validacion("Campo no admitido: " + c);
        });
    }

    private void autorizar(JsonNode tarea, SesionUsuario usuario) {
        if (usuario.usuarioId().equals(asignado(tarea))) return;
        for (JsonNode link : motor.identityLinks(tarea.path("id").asText())) {
            if ("candidate".equals(link.path("type").asText()) && usuario.grupos().contains(link.path("group").asText())) return;
        }
        throw ApiException.sinPermiso("No sos asignado ni candidato de esta tarea.");
    }

    private Curso curso(JsonNode tarea) {
        return cursos.findByProcessInstanceId(tarea.path("processInstanceId").asText())
                .orElseThrow(() -> ApiException.noEncontrado("La tarea no pertenece a un curso de la institución."));
    }

    private String asignado(JsonNode tarea) {
        return tarea.path("assignee").isTextual() && !tarea.path("assignee").asText().isBlank() ? tarea.get("assignee").asText() : null;
    }

    private TareaResumen resumen(JsonNode tarea, Curso curso) {
        return new TareaResumen(tarea.path("id").asText(), tarea.path("taskDefinitionKey").asText(), tarea.path("name").asText(),
                curso.getId().toString(), curso.getTematica(), curso.getEmpresa().getNombre(), tarea.path("createTime").asText(),
                asignado(tarea), asignado(tarea) == null);
    }
}
