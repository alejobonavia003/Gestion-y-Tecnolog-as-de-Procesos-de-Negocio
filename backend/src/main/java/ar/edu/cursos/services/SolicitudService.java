package ar.edu.cursos.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.NuevaSolicitudRequest;
import ar.edu.cursos.dto.SolicitudCreada;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.Empresa;
import ar.edu.cursos.model.EstadoCurso;
import ar.edu.cursos.repositories.CursoRepository;
import ar.edu.cursos.repositories.EmpresaRepository;

/**
 * Formulario público: guarda la solicitud y arranca el proceso en Flowable.
 *
 * El curso se guarda ANTES de iniciar el proceso: si no es viable, la instancia
 * termina dentro de la misma llamada y el listener de fin necesita encontrar el curso.
 * Guardar en la BD y llamar a Flowable no es una única transacción: si Flowable falla,
 * el curso queda sin processInstanceId y el reintento (misma Idempotency-Key) lo completa.
 */
@Service
public class SolicitudService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudService.class);

    private final CursoRepository cursos;
    private final EmpresaRepository empresas;
    private final FlowableClient flowable;
    private final ObjectMapper json;

    public SolicitudService(CursoRepository cursos, EmpresaRepository empresas,
                            FlowableClient flowable, ObjectMapper json) {
        this.cursos = cursos;
        this.empresas = empresas;
        this.flowable = flowable;
        this.json = json;
    }

    public SolicitudCreada registrar(String claveIdempotencia, NuevaSolicitudRequest datos) {
        if (claveIdempotencia.isBlank() || claveIdempotencia.length() > 100) {
            throw ApiException.validacion("El header Idempotency-Key no es válido.");
        }
        String hash = hashDe(datos);

        // ¿Es un reintento de una solicitud que ya recibimos?
        Curso curso = cursos.findByClaveIdempotencia(claveIdempotencia).orElse(null);
        if (curso != null) {
            if (!hash.equals(curso.getHashSolicitud())) {
                throw ApiException.conflicto("ESTADO_INCOMPATIBLE",
                        "Esta solicitud ya fue enviada con otros datos.");
            }
            if (curso.getProcessInstanceId() != null) {
                return respuesta(curso); // ya estaba completa: misma respuesta
            }
        } else {
            curso = guardarSolicitud(claveIdempotencia, hash, datos);
        }

        iniciarEnMotor(curso);
        return respuesta(curso);
    }

    // --- Pasos ----------------------------------------------------------------

    private Curso guardarSolicitud(String clave, String hash, NuevaSolicitudRequest datos) {
        Empresa empresa = buscarOCrearEmpresa(datos);

        Curso curso = new Curso();
        curso.setEmpresa(empresa);
        curso.setTematica(datos.tematica().trim());
        curso.setDescripcion(datos.descripcion().trim());
        curso.setCantidadParticipantes(datos.cantidadEstimadaParticipantes());
        curso.setFechasPreferidas(datos.fechasPreferidas().trim());
        curso.setEstado(EstadoCurso.SOLICITADO);
        curso.setClaveIdempotencia(clave);
        curso.setHashSolicitud(hash);
        try {
            return cursos.save(curso);
        } catch (DataIntegrityViolationException e) {
            // Dos envíos simultáneos con la misma clave: el otro ya la guardó
            throw ApiException.conflicto("ESTADO_INCOMPATIBLE",
                    "La solicitud se está procesando. Probá de nuevo en unos segundos.");
        }
    }

    /** Reutiliza la empresa si vino el mismo identificador (CUIT); si no, crea una nueva. */
    private Empresa buscarOCrearEmpresa(NuevaSolicitudRequest datos) {
        String identificador = vacioANull(datos.empresa().identificador());
        Empresa empresa = identificador == null
                ? new Empresa()
                : empresas.findByIdentificador(identificador).orElseGet(Empresa::new);

        empresa.setNombre(datos.empresa().nombre().trim());
        empresa.setIdentificador(identificador);
        empresa.setContactoNombre(datos.contacto().nombre().trim());
        empresa.setContactoEmail(datos.contacto().email().trim());
        empresa.setContactoTelefono(vacioANull(datos.contacto().telefono()));
        return empresas.save(empresa);
    }

    private void iniciarEnMotor(Curso curso) {
        String businessKey = businessKeyDe(curso);

        // Si un intento anterior llegó a crear la instancia pero no se guardó el ID, la reutilizamos
        JsonNode instancia = flowable.buscarInstanciaPorBusinessKey(businessKey).orElse(null);
        boolean termino;
        if (instancia != null) {
            termino = false;
        } else {
            instancia = flowable.iniciarProceso(businessKey, variablesDe(curso));
            termino = instancia.path("ended").asBoolean(false);
        }

        curso.setProcessInstanceId(instancia.path("id").asText());
        // Si terminó en la misma llamada fue "no viable" (la DMN la cerró enseguida)
        curso.setEstado(termino ? EstadoCurso.NO_VIABLE : EstadoCurso.EN_ACUERDO);
        cursos.save(curso);
        log.info("Curso {} → instancia {} ({})", curso.getId(), curso.getProcessInstanceId(), curso.getEstado());
    }

    /** Variables con las que arranca el proceso. Los nombres tienen que coincidir con el BPMN. */
    private Map<String, Object> variablesDe(Curso curso) {
        Empresa empresa = curso.getEmpresa();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("cursoId", curso.getId().toString());
        v.put("empresaNombre", empresa.getNombre());
        v.put("contactoNombre", empresa.getContactoNombre());
        v.put("contactoEmail", empresa.getContactoEmail());
        v.put("tematica", curso.getTematica());
        v.put("descripcion", curso.getDescripcion());
        v.put("cantidadParticipantes", curso.getCantidadParticipantes());
        v.put("fechasPreferidas", curso.getFechasPreferidas());
        // Entrada de la tabla DMN reglaViabilidad: cursos en ejecución ANTES de iniciar este
        v.put("cantCursos", flowable.contarInstanciasActivas());
        return v;
    }

    // --- Utilidades -------------------------------------------------------------

    private SolicitudCreada respuesta(Curso curso) {
        return new SolicitudCreada(curso.getId().toString(), curso.getProcessInstanceId(), businessKeyDe(curso));
    }

    private static String businessKeyDe(Curso curso) {
        return "curso:" + curso.getId();
    }

    private String hashDe(NuevaSolicitudRequest datos) {
        try {
            byte[] bytes = json.writeValueAsString(datos).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular el hash de la solicitud", e);
        }
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}