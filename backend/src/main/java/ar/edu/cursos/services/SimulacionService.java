package ar.edu.cursos.services;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.ComunicacionRequest;
import ar.edu.cursos.dto.ComunicacionResponse;
import ar.edu.cursos.dto.SolicitudSimulacionResponse;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.ComunicacionEmpresa;
import ar.edu.cursos.model.CuentaEmpresa;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.Empresa;
import ar.edu.cursos.model.EstadoCurso;
import ar.edu.cursos.model.Propuesta;
import ar.edu.cursos.repositories.ComunicacionEmpresaRepository;
import ar.edu.cursos.repositories.CuentaEmpresaRepository;
import ar.edu.cursos.repositories.CursoRepository;
import ar.edu.cursos.repositories.PropuestaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SimulacionService {

    private static final Logger log = LoggerFactory.getLogger(SimulacionService.class);

    private final CursoRepository cursos;
    private final PropuestaRepository propuestas;
    private final CuentaEmpresaRepository cuentas;
    private final ComunicacionEmpresaRepository comunicaciones;
    private final FlowableClient flowable;
    private final SesionService sesiones;
    private final ObjectMapper json;

    public SimulacionService(
            CursoRepository cursos,
            PropuestaRepository propuestas,
            CuentaEmpresaRepository cuentas,
            ComunicacionEmpresaRepository comunicaciones,
            FlowableClient flowable,
            SesionService sesiones,
            ObjectMapper json
    ) {
        this.cursos = cursos;
        this.propuestas = propuestas;
        this.cuentas = cuentas;
        this.comunicaciones = comunicaciones;
        this.flowable = flowable;
        this.sesiones = sesiones;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listarSolicitudes() {
        List<Curso> todos = cursos.findAll(Sort.by(Sort.Direction.DESC, "creadoEn"));
        List<SolicitudSimulacionResponse> items = todos.stream()
                .map(this::armarEstadoSimulacion)
                .toList();
        return Map.of("items", items);
    }

    @Transactional(readOnly = true)
    public SolicitudSimulacionResponse obtenerComunicacionesHabilitadas(UUID cursoId) {
        Curso curso = cursos.findById(cursoId)
                .orElseThrow(() -> ApiException.noEncontrado("Curso no encontrado."));
        return armarEstadoSimulacion(curso);
    }

    @Transactional
    public ComunicacionResponse aplicarComunicacion(UUID cursoId, ComunicacionRequest req) {
        Curso curso = cursos.findById(cursoId)
                .orElseThrow(() -> ApiException.noEncontrado("Curso no encontrado."));

        String comId = req.comunicacionId().trim();
        String tipo = req.tipo().trim().toUpperCase();
        String hash = calcularHash(cursoId, req);

        Optional<ComunicacionEmpresa> previa = comunicaciones.findByComunicacionId(comId);
        if (previa.isPresent()) {
            ComunicacionEmpresa existente = previa.get();
            if (!existente.getCurso().getId().equals(cursoId) || !existente.getHashPayload().equals(hash)) {
                throw ApiException.conflicto(
                        "ESTADO_INCOMPATIBLE",
                        "El comunicacionId ya fue utilizado con otros datos o para otro curso."
                );
            }
            String usuarioCuenta = cuentas.findByEmpresaId(curso.getEmpresa().getId())
                    .map(CuentaEmpresa::getUsuario)
                    .orElse(null);
            return new ComunicacionResponse(comId, tipo, true, true, usuarioCuenta);
        }

        if (curso.getProcessInstanceId() == null) {
            throw ApiException.conflicto("ESTADO_INCOMPATIBLE", "El curso no tiene una instancia activa en el motor.");
        }

        String usuarioCuenta = switch (tipo) {
            case "ACEPTACION" -> procesarAceptacion(curso, req);
            case "RECHAZO" -> {
                procesarRechazo(curso, req);
                yield null;
            }
            case "MODIFICACION" -> {
                procesarModificacion(curso, req);
                yield null;
            }
            case "NOMINA" -> {
                procesarNomina(curso, req);
                yield null;
            }
            case "CANCELACION" -> {
                procesarCancelacion(curso);
                yield null;
            }
            default -> throw ApiException.validacion("Tipo de comunicación no admitido: " + tipo);
        };

        ComunicacionEmpresa registro = new ComunicacionEmpresa();
        registro.setComunicacionId(comId);
        registro.setCurso(curso);
        registro.setTipo(tipo);
        registro.setHashPayload(hash);
        comunicaciones.save(registro);

        return new ComunicacionResponse(comId, tipo, true, false, usuarioCuenta);
    }

    private String procesarAceptacion(Curso curso, ComunicacionRequest req) {
        Propuesta vigente = validarVersionPropuesta(curso.getId(), req.propuestaVersion());
        if (req.fechaCurso() == null) {
            throw ApiException.validacion("Debe indicar fechaCurso para aceptar la propuesta.");
        }

        String execId = flowable.buscarEjecucionPorMensaje(curso.getProcessInstanceId(), "aceptacion")
                .orElseThrow(() -> ApiException.conflicto(
                        "ESTADO_INCOMPATIBLE",
                        "El curso no está esperando respuesta de propuesta en este momento."
                ));

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("respuestaEmpresa", "ACEPTADA");
        vars.put("fechaCurso", req.fechaCurso().toString());
        flowable.enviarMensajeAEjecucion(execId, "aceptacion", vars);

        completarHabilitarAccesoSiEsTareaHumana(curso.getProcessInstanceId());

        vigente.setEstado(Propuesta.Estado.ACEPTADA);
        propuestas.save(vigente);

        curso.setPrograma(vigente.getPrograma());
        curso.setPresupuestoImporte(vigente.getPresupuestoImporte());
        curso.setPresupuestoMoneda(vigente.getPresupuestoMoneda());
        curso.setFechaCurso(req.fechaCurso());
        curso.setEstado(EstadoCurso.EN_PREPARACION);
        cursos.save(curso);

        CuentaEmpresa cuenta = habilitarOReutilizarCuentaEmpresa(curso.getEmpresa());
        return cuenta.getUsuario();
    }

    private void procesarRechazo(Curso curso, ComunicacionRequest req) {
        Propuesta vigente = validarVersionPropuesta(curso.getId(), req.propuestaVersion());

        String execId = flowable.buscarEjecucionPorMensaje(curso.getProcessInstanceId(), "rechazo")
                .orElseThrow(() -> ApiException.conflicto(
                        "ESTADO_INCOMPATIBLE",
                        "El curso no está esperando respuesta de propuesta en este momento."
                ));

        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("respuestaEmpresa", "RECHAZADA");
        flowable.enviarMensajeAEjecucion(execId, "rechazo", vars);

        vigente.setEstado(Propuesta.Estado.RECHAZADA);
        propuestas.save(vigente);
    }

    private void procesarModificacion(Curso curso, ComunicacionRequest req) {
        Propuesta vigente = validarVersionPropuesta(curso.getId(), req.propuestaVersion());
        if (req.observacionesEmpresa() == null || req.observacionesEmpresa().isBlank()) {
            throw ApiException.validacion("Debe indicar observacionesEmpresa para solicitar modificaciones.");
        }

        String execId = flowable.buscarEjecucionPorMensaje(curso.getProcessInstanceId(), "modificaciones")
                .orElseThrow(() -> ApiException.conflicto(
                        "ESTADO_INCOMPATIBLE",
                        "El curso no está esperando respuesta de propuesta en este momento."
                ));

        String obs = req.observacionesEmpresa().trim();
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("respuestaEmpresa", "MODIFICACIONES");
        vars.put("observacionesEmpresa", obs);
        flowable.enviarMensajeAEjecucion(execId, "modificaciones", vars);

        vigente.setEstado(Propuesta.Estado.MODIFICACIONES);
        vigente.setObservacionesEmpresa(obs);
        propuestas.save(vigente);
    }

    private void procesarNomina(Curso curso, ComunicacionRequest req) {
        if (req.participantes() == null || req.participantes().isEmpty()) {
            throw ApiException.validacion("Debe enviar al menos un participante en la nómina.");
        }

        String execId = buscarEjecucionPorActividad(curso.getProcessInstanceId(), "recibir_nomina")
                .orElseThrow(() -> ApiException.conflicto(
                        "ESTADO_INCOMPATIBLE",
                        "El curso no está esperando la recepción de la nómina."
                ));

        JsonNode participantesJson = json.valueToTree(req.participantes());
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("participantes", participantesJson);
        flowable.senalarEjecucion(execId, vars);
    }

    private void procesarCancelacion(Curso curso) {
        Optional<String> execCancelacion = flowable.buscarEjecucionPorMensaje(curso.getProcessInstanceId(), "cancelacion");
        String nombreMensaje = "cancelacion";
        if (execCancelacion.isEmpty()) {
            execCancelacion = flowable.buscarEjecucionPorMensaje(curso.getProcessInstanceId(), "cancelacionEmpresa");
            nombreMensaje = "cancelacionEmpresa";
        }

        String execId = execCancelacion.orElseThrow(() -> ApiException.conflicto(
                "ESTADO_INCOMPATIBLE",
                "La cancelación solo está habilitada durante la preparación del curso."
        ));

        flowable.enviarMensajeAEjecucion(execId, nombreMensaje, Map.of("resultadoProceso", "CANCELADO"));
        curso.setEstado(EstadoCurso.CANCELADO);
        cursos.save(curso);
    }

    private Propuesta validarVersionPropuesta(UUID cursoId, Integer versionEnviada) {
        if (versionEnviada == null) {
            throw ApiException.validacion("Debe indicar propuestaVersion.");
        }
        Propuesta vigente = propuestas.findFirstByCursoIdOrderByVersionDesc(cursoId)
                .orElseThrow(() -> ApiException.conflicto(
                        "ESTADO_INCOMPATIBLE",
                        "Todavía no existe una propuesta emitida para este curso."
                ));
        if (vigente.getVersion() != versionEnviada || vigente.getEstado() != Propuesta.Estado.ENVIADA) {
            throw ApiException.conflicto(
                    "VERSION_DESACTUALIZADA",
                    "La versión indicada no coincide con la propuesta enviada vigente (v" + vigente.getVersion() + ")."
            );
        }
        return vigente;
    }

    private CuentaEmpresa habilitarOReutilizarCuentaEmpresa(Empresa empresa) {
        Optional<CuentaEmpresa> existente = cuentas.findByEmpresaId(empresa.getId());
        if (existente.isPresent()) {
            return existente.get();
        }
        String baseUsuario = empresa.getIdentificador() != null && !empresa.getIdentificador().isBlank()
                ? empresa.getIdentificador().trim()
                : "empresa-" + empresa.getId();
        String usuario = cuentas.findByUsuario(baseUsuario).isPresent()
                ? "empresa-" + empresa.getId()
                : baseUsuario;

        CuentaEmpresa nueva = new CuentaEmpresa();
        nueva.setEmpresa(empresa);
        nueva.setUsuario(usuario);
        nueva.setClaveHash(sesiones.hashear("1234"));
        nueva.setActiva(true);
        CuentaEmpresa guardada = cuentas.save(nueva);
        log.info("Cuenta de empresa habilitada: usuario='{}' (clave demo '1234')", usuario);
        return guardada;
    }

    private void completarHabilitarAccesoSiEsTareaHumana(String processInstanceId) {
        JsonNode tareas = flowable.tareasDeInstancia(processInstanceId);
        for (JsonNode t : tareas.path("data")) {
            if ("habilitar_acceso_empresa".equals(t.path("taskDefinitionKey").asText())) {
                flowable.completar(t.path("id").asText(), Map.of());
            }
        }
    }

    private Optional<String> buscarEjecucionPorActividad(String processInstanceId, String activityId) {
        JsonNode ejecuciones = flowable.ejecucionesDeInstancia(processInstanceId);
        for (JsonNode e : ejecuciones.path("data")) {
            if (activityId.equals(e.path("activityId").asText())) {
                return Optional.of(e.path("id").asText());
            }
        }
        return Optional.empty();
    }

    private SolicitudSimulacionResponse armarEstadoSimulacion(Curso curso) {
        Optional<Propuesta> propOpt = propuestas.findFirstByCursoIdOrderByVersionDesc(curso.getId());
        Integer versionVigente = propOpt.map(Propuesta::getVersion).orElse(null);
        List< java.time.OffsetDateTime > fechas = propOpt.map(Propuesta::getFechasPosibles).orElse(List.of());

        List<String> acciones = new ArrayList<>();
        String pid = curso.getProcessInstanceId();
        if (pid != null) {
            try {
                if (flowable.buscarEjecucionPorMensaje(pid, "aceptacion").isPresent()) {
                    acciones.add("ACEPTACION");
                    acciones.add("RECHAZO");
                    acciones.add("MODIFICACION");
                }
                if (buscarEjecucionPorActividad(pid, "recibir_nomina").isPresent()) {
                    acciones.add("NOMINA");
                }
                if (flowable.buscarEjecucionPorMensaje(pid, "cancelacion").isPresent()
                        || flowable.buscarEjecucionPorMensaje(pid, "cancelacionEmpresa").isPresent()) {
                    acciones.add("CANCELACION");
                }
            } catch (ApiException ignored) {
                // Si la instancia ya terminó o no está en el motor, no hay acciones habilitadas.
            }
        }

        return new SolicitudSimulacionResponse(
                curso.getId().toString(),
                curso.getTematica(),
                curso.getEmpresa() != null ? curso.getEmpresa().getNombre() : "-",
                curso.getEstado().name(),
                versionVigente,
                fechas,
                acciones
        );
    }

    private String calcularHash(UUID cursoId, ComunicacionRequest req) {
        try {
            String raw = cursoId + "|" + json.writeValueAsString(req);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular el hash de la comunicación", e);
        }
    }
}