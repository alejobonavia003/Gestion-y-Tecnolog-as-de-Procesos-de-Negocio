package ar.edu.cursos.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.*;
import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.*;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.*;
import ar.edu.cursos.repositories.*;

class TareaServiceTest {
    final ObjectMapper json = new ObjectMapper();
    FlowableClient motor = mock(FlowableClient.class);
    CursoRepository cursos = mock(CursoRepository.class);
    PropuestaRepository propuestas = mock(PropuestaRepository.class);
    TareaService servicio = new TareaService(motor, cursos, propuestas);
    SesionUsuario usuario = SesionUsuario.personal("alejo", "Alejo", List.of("Equipo-Administrativo"), false);
    Curso curso = mock(Curso.class);

    @BeforeEach void preparar() {
        when(cursos.findByProcessInstanceId("p1")).thenReturn(Optional.of(curso));
        when(curso.getProcessInstanceId()).thenReturn("p1");
        when(curso.getId()).thenReturn(UUID.randomUUID());
        when(curso.getTematica()).thenReturn("Curso");
        Empresa empresa = mock(Empresa.class);
        when(empresa.getNombre()).thenReturn("Empresa"); when(curso.getEmpresa()).thenReturn(empresa);
    }
    JsonNode tarea(String key, String asignado) throws Exception {
        return json.readTree("{\"id\":\"t1\",\"processInstanceId\":\"p1\",\"taskDefinitionKey\":\"" + key + "\",\"assignee\":" + (asignado == null ? "null" : "\"" + asignado + "\"") + "}");
    }
    void error(String codigo, Runnable accion) { assertEquals(codigo, assertThrows(ApiException.class, accion::run).getCodigo()); }

    @Test void noAsignadoNoCompleta() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("archivar_solicitud", "otro"));
        error("SIN_PERMISO", () -> servicio.completar("t1", usuario, json.createObjectNode()));
        verify(motor, never()).completar(anyString(), anyMap());
    }
    @Test void segundoReclamoEsConflicto() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("evaluar_recursos", null), tarea("evaluar_recursos", "alejo"));
        when(motor.identityLinks("t1")).thenReturn(json.readTree("[{\"type\":\"candidate\",\"group\":\"Equipo-Administrativo\"}]"));
        assertEquals("alejo", servicio.reclamar("t1", usuario).asignado());
        error("YA_RECLAMADA", () -> servicio.reclamar("t1", usuario));
        verify(motor, times(1)).reclamar("t1", "alejo");
    }
    @Test void ajenoNoVeDetalle() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("evaluar_recursos", null));
        when(motor.identityLinks("t1")).thenReturn(json.readTree("[{\"type\":\"candidate\",\"group\":\"Equipo-Docente\"}]"));
        error("SIN_PERMISO", () -> servicio.detalle("t1", usuario));
    }
    @Test void recursosValidaYEnviaSoloVariablesAdmitidas() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("evaluar_recursos", "alejo"));
        JsonNode invalidos = json.readTree("{\"datos\":{\"requiereAula\":false,\"requierePlataforma\":false,\"requiereEquipamiento\":false}}");
        error("VALIDACION", () -> servicio.completar("t1", usuario, invalidos));
        servicio.completar("t1", usuario, json.readTree("{\"datos\":{\"requiereAula\":true,\"requierePlataforma\":false,\"requiereEquipamiento\":false}}"));
        verify(motor).completar("t1", Map.of("requiereAula", true,"requierePlataforma",false,"requiereEquipamiento",false));
    }
    @Test void participantesSeEnvianComoJson() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("confeccionar_lista", "alejo"));
        JsonNode datos = json.readTree("{\"datos\":{\"participantes\":[{\"nombre\":\"Ana\"}]}}");
        servicio.completar("t1", usuario, datos);
        verify(motor).completar("t1", Map.of("participantes", datos.path("datos").get("participantes")));
        error("VALIDACION", () -> servicio.completar("t1", usuario, json.createObjectNode().set("datos", json.createObjectNode().set("participantes", json.createArrayNode()))));
    }
    @Test void propuestaNoAdmiteCompletadoSimple() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("registrar_elaborar_propuesta", "alejo"));
        error("ESTADO_INCOMPATIBLE", () -> servicio.completar("t1", usuario, json.createObjectNode()));
    }
    @Test void detalleSeSerializaSinAnidarTarea() throws Exception {
        var detalle = new TareaDetalle(new TareaResumen("t1","evaluar_recursos","Evaluar","c1","Curso","Empresa",null,null,true), Map.of());
        JsonNode salida = json.valueToTree(detalle);
        assertEquals("t1", salida.path("taskId").asText()); assertTrue(salida.has("contexto")); assertFalse(salida.has("tarea"));
    }
    @Test void bandejaAsignadaIncluyeCursoYNoEsReclamable() throws Exception {
        when(motor.tareas("alejo", usuario.grupos(), false, 0, 20)).thenReturn(
                json.createObjectNode().put("total", 1).set("data", json.createArrayNode().add(tarea("registrar_elaborar_propuesta", "alejo"))));
        var pagina = servicio.listar(usuario, "asignadas", 0, 20);
        @SuppressWarnings("unchecked")
        var items = (List<TareaResumen>) pagina.get("items");
        assertEquals(1, items.size()); assertFalse(items.get(0).reclamable());
        assertEquals("Empresa", items.get(0).empresaNombre());
    }
    @Test void camposExtraNoSeTransmitenAlMotor() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("archivar_solicitud", "alejo"));
        JsonNode cuerpo = json.readTree("{\"datos\":{\"assignee\":\"otro\"}}");
        error("VALIDACION", () -> servicio.completar("t1", usuario, cuerpo));
        verify(motor, never()).completar(anyString(), anyMap());
    }
}
