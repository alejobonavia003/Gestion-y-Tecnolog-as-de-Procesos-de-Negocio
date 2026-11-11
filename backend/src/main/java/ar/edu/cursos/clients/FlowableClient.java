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
    private final String procesoClave;

    public FlowableClient(@Value("${flowable.rest.url}") String url,
                          @Value("${flowable.rest.usuario}") String usuario,
                          @Value("${flowable.rest.clave}") String clave,
                          @Value("${flowable.proceso.clave}") String procesoClave) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(15));

        this.rest = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .defaultHeaders(h -> h.setBasicAuth(usuario, clave))
                .build();
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
            v.put("value", valor instanceof Number || valor instanceof Boolean ? valor : valor.toString());
            lista.add(v);
        });
        return lista;
    }

    private static String tipoRest(Object valor) {
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
}