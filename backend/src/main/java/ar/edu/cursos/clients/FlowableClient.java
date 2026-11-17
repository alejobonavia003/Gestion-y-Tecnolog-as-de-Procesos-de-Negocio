package ar.edu.cursos.clients;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;

import ar.edu.cursos.exceptions.ApiException;

/**
 * Cliente de la API REST de Flowable (http://.../flowable-ui/process-api).
 * Es la ÚNICA clase del backend que habla con el motor. En las próximas etapas
 * se le agregan los métodos de tareas, mensajes e historial.
 */
@Component
public class FlowableClient {

    private static final Logger log = LoggerFactory.getLogger(FlowableClient.class);

    private final RestClient rest;
    private final RestClient identidad;
    private final SimpleClientHttpRequestFactory fabrica;
    private final String identidadUrl;
    private final String procesoClave;

    public FlowableClient(@Value("${flowable.rest.url}") String url,
                          @Value("${flowable.rest.identidad-url}") String identidadUrl,
                          @Value("${flowable.rest.usuario}") String usuario,
                          @Value("${flowable.rest.clave}") String clave,
                          @Value("${flowable.proceso.clave}") String procesoClave) {
        this.fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(15));

        this.rest = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .defaultHeaders(h -> h.setBasicAuth(usuario, clave))
                .build();
        this.identidad = RestClient.builder()
                .baseUrl(identidadUrl)
                .requestFactory(fabrica)
                .defaultHeaders(h -> h.setBasicAuth(usuario, clave))
                .build();
        this.identidadUrl = identidadUrl;
        this.procesoClave = procesoClave;
    }

    public String getProcesoClave() {
        return procesoClave;
    }

    // --- Instancias de proceso ------------------------------------------------

    /** Cantidad de instancias activas del proceso, sumando todas sus versiones (cantCursos). */
    public int contarInstanciasActivas() {
        JsonNode respuesta = llamar(() -> rest.get()
                .uri("/runtime/process-instances?processDefinitionKey={clave}&size=1", procesoClave)
                .retrieve()
                .body(JsonNode.class));
        return respuesta.path("total").asInt();
    }

    /**
     * Inicia una instancia del proceso. Devuelve la respuesta de Flowable:
     * "id" es el processInstanceId y "ended" es true si terminó en la misma llamada (no viable).
     */
    public JsonNode iniciarProceso(String businessKey, Map<String, Object> variables) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("processDefinitionKey", procesoClave);
        cuerpo.put("businessKey", businessKey);
        cuerpo.put("variables", aVariablesRest(variables));

        return llamar(() -> rest.post()
                .uri("/runtime/process-instances")
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class));
    }

    /** Busca una instancia por businessKey (para no iniciar dos veces el mismo curso). */
    public Optional<JsonNode> buscarInstanciaPorBusinessKey(String businessKey) {
        JsonNode respuesta = llamar(() -> rest.get()
                .uri("/runtime/process-instances?processDefinitionKey={clave}&businessKey={bk}",
                        procesoClave, businessKey)
                .retrieve()
                .body(JsonNode.class));
        JsonNode data = respuesta.path("data");
        return data.isArray() && !data.isEmpty() ? Optional.of(data.get(0)) : Optional.empty();
    }

    // --- Tareas ---------------------------------------------------------------
    public JsonNode tareas(String usuario, List<String> grupos, boolean disponibles, int page, int size) {
        return llamar(() -> rest.get().uri(b -> {
            b.path("/runtime/tasks").queryParam("processDefinitionKey", procesoClave)
                .queryParam("start", (long) page * size).queryParam("size", size)
                .queryParam("sort", "createTime").queryParam("order", "asc");
            if (disponibles) b.queryParam("candidateGroups", String.join(",", grupos)).queryParam("unassigned", true);
            else b.queryParam("assignee", usuario);
            return b.build();
        }).retrieve().body(JsonNode.class));
    }

    public JsonNode tarea(String id) {
        return llamar(() -> rest.get().uri("/runtime/tasks/{id}", id).retrieve().body(JsonNode.class));
    }

    public JsonNode identityLinks(String id) {
        return llamar(() -> rest.get().uri("/runtime/tasks/{id}/identitylinks", id).retrieve().body(JsonNode.class));
    }

    public JsonNode variables(String processInstanceId) {
        return llamar(() -> rest.get().uri("/runtime/process-instances/{id}/variables", processInstanceId)
                .retrieve().body(JsonNode.class));
    }

    public void reclamar(String id, String usuario) {
        try {
            llamar(() -> rest.post().uri("/runtime/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("action", "claim", "assignee", usuario)).retrieve().toBodilessEntity());
        } catch (ApiException e) {
            if (e.getStatus().value() == 409) throw ApiException.conflicto("YA_RECLAMADA", "La tarea ya fue reclamada.");
            throw e;
        }
    }

    public void completar(String id, Map<String, Object> variables) {
        llamar(() -> rest.post().uri("/runtime/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("action", "complete", "variables", aVariablesRest(variables)))
                .retrieve().toBodilessEntity());
    }

    // --- Identidad (usuarios y grupos de Flowable IDM) -------------------------

    /**
     * Verifica usuario y contraseña haciendo una consulta CON ESAS credenciales.
     * 401 = incorrectas. 200 o 403 = correctas (403 solo indica que el usuario no tiene
     * permiso para la API REST, pero Flowable lo autenticó).
     */
    public boolean credencialesValidas(String usuarioId, String clave) {
        RestClient comoUsuario = RestClient.builder()
                .baseUrl(identidadUrl)
                .requestFactory(fabrica)
                .defaultHeaders(h -> h.setBasicAuth(usuarioId, clave))
                .build();
        try {
            comoUsuario.get().uri("/users/{id}", usuarioId).retrieve().toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401) return false;
            if (status == 403) return true;
            log.warn("Flowable respondió {} al validar credenciales", status);
            throw ApiException.motorNoDisponible();
        } catch (ResourceAccessException e) {
            log.warn("No se pudo conectar con Flowable: {}", e.getMessage());
            throw ApiException.motorNoDisponible();
        }
    }

    /** Datos del usuario (firstName, lastName, email), o vacío si no existe. */
    public Optional<JsonNode> buscarUsuario(String usuarioId) {
        try {
            return Optional.ofNullable(llamar(() -> identidad.get()
                    .uri("/users/{id}", usuarioId)
                    .retrieve()
                    .body(JsonNode.class)));
        } catch (ApiException e) {
            if ("NO_ENCONTRADO".equals(e.getCodigo())) return Optional.empty();
            throw e;
        }
    }

    /** IDs de los grupos a los que pertenece el usuario. */
    public List<String> gruposDe(String usuarioId) {
        JsonNode respuesta = llamar(() -> identidad.get()
                .uri("/groups?member={id}&size=100", usuarioId)
                .retrieve()
                .body(JsonNode.class));
        List<String> grupos = new ArrayList<>();
        respuesta.path("data").forEach(g -> grupos.add(g.path("id").asText()));
        return grupos;
    }

    /** IDs de los usuarios de un grupo (para listar docentes y para el listener de la lista). */
    public List<String> miembrosDe(String grupoId) {
        JsonNode respuesta = llamar(() -> identidad.get()
                .uri("/users?memberOfGroup={g}&size=100", grupoId)
                .retrieve()
                .body(JsonNode.class));
        List<String> usuarios = new ArrayList<>();
        respuesta.path("data").forEach(u -> usuarios.add(u.path("id").asText()));
        return usuarios;
    }

    // --- Utilidades -----------------------------------------------------------

    /**
     * Convierte un Map de Java al formato de variables de la API REST:
     * [{"name": "...", "type": "integer", "value": 3}, ...]. Las variables null se omiten.
     */
    static List<Map<String, Object>> aVariablesRest(Map<String, Object> variables) {
        List<Map<String, Object>> lista = new ArrayList<>();
        variables.forEach((nombre, valor) -> {
            if (valor == null) {
                return;
            }
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("name", nombre);
            v.put("type", tipoRest(valor));
            v.put("value", valor instanceof Number || valor instanceof Boolean || valor instanceof JsonNode ? valor : valor.toString());
            lista.add(v);
        });
        return lista;
    }

    private static String tipoRest(Object valor) {
        if (valor instanceof JsonNode) return "json";
        if (valor instanceof Integer) return "integer";
        if (valor instanceof Long) return "long";
        if (valor instanceof Boolean) return "boolean";
        if (valor instanceof Double || valor instanceof Float) return "double";
        return "string";
    }

    /** Ejecuta una llamada a Flowable y traduce sus errores a los códigos de nuestra API. */
    private <T> T llamar(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ResourceAccessException e) {
            log.warn("No se pudo conectar con Flowable: {}", e.getMessage());
            throw ApiException.motorNoDisponible();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("Flowable respondió {}: {}", status, e.getResponseBodyAsString());
            if (status == 401 || status == 403) {
                log.error("Credenciales de Flowable rechazadas: revisá FLOWABLE_REST_USUARIO y FLOWABLE_REST_CLAVE en .env");
                throw ApiException.motorNoDisponible();
            }
            if (status == 404) {
                throw ApiException.noEncontrado("No se encontró el recurso en el motor de procesos.");
            }
            if (status == 409) {
                throw ApiException.conflicto("ESTADO_INCOMPATIBLE", "La operación no es posible en el estado actual del proceso.");
            }
            throw ApiException.motorNoDisponible();
        }
    }

    public JsonNode tareasDeInstancia(String processInstanceId) {
        return llamar(() -> rest.get()
                .uri("/runtime/tasks?processInstanceId={id}&size=100", processInstanceId)
                .retrieve()
                .body(JsonNode.class));
    }

    public JsonNode ejecucionesDeInstancia(String processInstanceId) {
        return llamar(() -> rest.get()
                .uri("/runtime/executions?processInstanceId={id}&size=100", processInstanceId)
                .retrieve()
                .body(JsonNode.class));
    }

    public Optional<String> buscarEjecucionPorMensaje(String processInstanceId, String messageName) {
        JsonNode respuesta = llamar(() -> rest.get()
                .uri("/runtime/executions?processInstanceId={pid}&messageEventSubscriptionName={msg}",
                        processInstanceId, messageName)
                .retrieve()
                .body(JsonNode.class));
        JsonNode data = respuesta.path("data");
        return data.isArray() && !data.isEmpty()
                ? Optional.of(data.get(0).path("id").asText())
                : Optional.empty();
    }

    public void enviarMensajeAEjecucion(String executionId, String messageName, Map<String, Object> variables) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("action", "messageEventReceived");
        cuerpo.put("messageName", messageName);
        cuerpo.put("variables", aVariablesRest(variables));

        llamar(() -> rest.put()
                .uri("/runtime/executions/{id}", executionId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity());
    }

    public void senalarEjecucion(String executionId, Map<String, Object> variables) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("action", "signal");
        cuerpo.put("variables", aVariablesRest(variables));

        llamar(() -> rest.put()
                .uri("/runtime/executions/{id}", executionId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .toBodilessEntity());
    }


    /**
     * Valor de una variable del proceso leído del historial: sirve con la instancia
     * activa o ya terminada. Vacío si la variable no existe.
     */
    public Optional<String> variableHistorica(String processInstanceId, String nombre) {
        JsonNode respuesta = llamar(() -> rest.get()
                .uri("/history/historic-variable-instances?processInstanceId={p}&variableName={n}",
                        processInstanceId, nombre)
                .retrieve()
                .body(JsonNode.class));
        JsonNode data = respuesta == null ? null : respuesta.path("data");
        if (data == null || !data.isArray() || data.isEmpty()) return Optional.empty();
        JsonNode valor = data.get(0).path("variable").path("value");
        return valor.isMissingNode() || valor.isNull() ? Optional.empty() : Optional.of(valor.asText());
    }
}
