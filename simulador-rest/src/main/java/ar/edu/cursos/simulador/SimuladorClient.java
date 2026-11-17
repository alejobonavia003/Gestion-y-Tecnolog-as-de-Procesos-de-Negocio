package ar.edu.cursos.simulador;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SimuladorClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public SimuladorClient(Configuracion config) {
        this.baseUrl = config.baseUrl();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public List<SolicitudSimulacion> listarSolicitudes() {
        JsonNode resp = enviar("GET", "/simulacion/solicitudes", null);
        List<SolicitudSimulacion> lista = new ArrayList<>();
        for (JsonNode n : resp.path("items")) {
            lista.add(parsearSolicitud(n));
        }
        return lista;
    }

    public SolicitudSimulacion consultarComunicaciones(String cursoId) {
        JsonNode resp = enviar("GET", "/simulacion/solicitudes/" + cursoId + "/comunicaciones", null);
        return parsearSolicitud(resp);
    }

    public Path descargarPdfPropuesta(String cursoId, int version) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/simulacion/solicitudes/" + cursoId + "/propuesta-pdf"))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new ErrorAplicacion("No se encontró el PDF de la propuesta (HTTP " + resp.statusCode() + ").");
            }
            Path archivo = Files.createTempFile("propuesta-" + cursoId + "-v" + version + "-", ".pdf");
            Files.write(archivo, resp.body());
            return archivo;
        } catch (IOException e) {
            throw new ErrorAplicacion("No se pudo descargar el PDF de la propuesta: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorAplicacion("Descarga interrumpida.");
        }
    }

    public String enviarComunicacion(
            String cursoId,
            String tipo,
            Integer propuestaVersion,
            String fechaCurso,
            String observacionesEmpresa,
            List<String> participantes
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("comunicacionId", "sim-" + UUID.randomUUID());
        body.put("tipo", tipo);
        if (propuestaVersion != null) {
            body.put("propuestaVersion", propuestaVersion);
        }
        if (fechaCurso != null && !fechaCurso.isBlank()) {
            body.put("fechaCurso", fechaCurso.trim());
        }
        if (observacionesEmpresa != null && !observacionesEmpresa.isBlank()) {
            body.put("observacionesEmpresa", observacionesEmpresa.trim());
        }
        if (participantes != null && !participantes.isEmpty()) {
            List<Map<String, String>> listaPart = new ArrayList<>();
            for (String p : participantes) {
                listaPart.add(Map.of("nombre", p.trim()));
            }
            body.put("participantes", listaPart);
        }

        JsonNode resp = enviar("POST", "/simulacion/solicitudes/" + cursoId + "/comunicaciones", body);
        String cuenta = resp.path("cuentaEmpresaUsuario").asText("");
        if (!cuenta.isBlank() && !resp.path("cuentaEmpresaUsuario").isNull()) {
            return "Comunicación " + tipo + " enviada. Cuenta de portal habilitada para la empresa: usuario '" + cuenta + "' (clave demo: 1234).";
        }
        return "Comunicación " + tipo + " enviada correctamente.";
    }

    private SolicitudSimulacion parsearSolicitud(JsonNode n) {
        Integer version = n.path("propuestaVersionVigente").isNumber()
                ? n.get("propuestaVersionVigente").asInt()
                : null;
        String programa = n.path("programaPropuesto").isNull() ? "" : n.path("programaPropuesto").asText("");
        String presupuesto = n.path("presupuestoPropuesto").isNull() ? "" : n.path("presupuestoPropuesto").asText("");
        List<String> fechas = new ArrayList<>();
        for (JsonNode f : n.path("fechasPosibles")) {
            fechas.add(f.asText());
        }
        List<String> acciones = new ArrayList<>();
        for (JsonNode a : n.path("accionesHabilitadas")) {
            acciones.add(a.asText());
        }
        return new SolicitudSimulacion(
                n.path("cursoId").asText(""),
                n.path("tematica").asText(""),
                n.path("empresaNombre").asText(""),
                n.path("estado").asText(""),
                version,
                programa,
                presupuesto,
                fechas,
                acciones
        );
    }

    private JsonNode enviar(String metodo, String ruta, Object cuerpo) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + ruta))
                    .timeout(Duration.ofSeconds(15));
            if (cuerpo != null) {
                String jsonBody = json.writeValueAsString(cuerpo);
                req.header("Content-Type", "application/json");
                req.method(metodo, HttpRequest.BodyPublishers.ofString(jsonBody));
            } else {
                req.method(metodo, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status >= 200 && status < 300) {
                String raw = resp.body();
                return (raw == null || raw.isBlank()) ? json.createObjectNode() : json.readTree(raw);
            }
            String mensaje = "Error HTTP " + status;
            if (resp.body() != null && !resp.body().isBlank()) {
                try {
                    JsonNode err = json.readTree(resp.body());
                    mensaje = err.path("mensaje").asText(mensaje) + " (" + err.path("codigo").asText("HTTP_" + status) + ")";
                } catch (Exception ignored) {
                }
            }
            throw new ErrorAplicacion(mensaje);
        } catch (IOException e) {
            throw new ErrorAplicacion("No se pudo conectar con el backend en " + baseUrl + ". Verificá que esté corriendo en el puerto 8081.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErrorAplicacion("La operación fue interrumpida.");
        }
    }
}
