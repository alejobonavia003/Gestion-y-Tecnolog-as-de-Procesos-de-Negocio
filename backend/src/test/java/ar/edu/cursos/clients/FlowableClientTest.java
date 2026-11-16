package ar.edu.cursos.clients;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import ar.edu.cursos.exceptions.ApiException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class FlowableClientTest {
    @Test void filtraPorProcesoYGruposYTraduceConflicto() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        server.createContext("/runtime/tasks", ex -> {
            query.set(ex.getRequestURI().getQuery());
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean post = ex.getRequestMethod().equals("POST");
            byte[] bytes = (post ? "{}" : "{\"data\":[],\"total\":0}").getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(post ? 409 : 200, bytes.length);
            ex.getResponseBody().write(bytes); ex.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort();
            FlowableClient client = new FlowableClient(url, url, "test", "test", "process");
            client.tareas("alejo", List.of("Equipo-Administrativo"), true, 1, 20);
            assertTrue(query.get().contains("processDefinitionKey=process"));
            assertTrue(query.get().contains("candidateGroups=Equipo-Administrativo"));
            assertTrue(query.get().contains("unassigned=true")); assertTrue(query.get().contains("start=20"));
            ApiException error = assertThrows(ApiException.class, () -> client.reclamar("t1", "alejo"));
            assertEquals("YA_RECLAMADA", error.getCodigo());
            assertEquals("alejo", new ObjectMapper().readTree(body.get()).path("assignee").asText());
        } finally { server.stop(0); }
    }
    @Test void conservaParticipantesComoJson() throws Exception {
        var participantes = new ObjectMapper().readTree("[{\"nombre\":\"Ana\"}]");
        var variables = FlowableClient.aVariablesRest(Map.of("participantes", participantes));
        assertEquals("json", variables.get(0).get("type"));
        assertEquals(participantes, variables.get(0).get("value"));
    }
}
