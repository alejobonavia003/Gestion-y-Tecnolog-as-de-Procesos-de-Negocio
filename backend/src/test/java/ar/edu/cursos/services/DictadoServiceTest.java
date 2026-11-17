package ar.edu.cursos.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.SesionUsuario;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.EstadoCurso;
import ar.edu.cursos.repositories.CursoRepository;

class DictadoServiceTest {

    final ObjectMapper json = new ObjectMapper();
    FlowableClient motor = mock(FlowableClient.class);
    CursoRepository cursos = mock(CursoRepository.class);
    TareaService tareas = mock(TareaService.class);
    DictadoService servicio = new DictadoService(motor, cursos, tareas);

    SesionUsuario jefeDocente = SesionUsuario.personal("Jefe-Docente", "Jefe", List.of("Equipo-Docente"), true);
    Curso curso = mock(Curso.class);

    @BeforeEach
    void preparar() {
        when(cursos.findByProcessInstanceId("p1")).thenReturn(Optional.of(curso));
        when(tareas.listarDocentes()).thenReturn(List.of(
                Map.<String, Object>of("usuarioId", "Jefe-Docente", "nombre", "Jefe", "esJefe", true),
                Map.<String, Object>of("usuarioId", "docente-1", "nombre", "Docente 1", "esJefe", false)));
    }

    JsonNode tarea(String key, String asignado) throws Exception {
        return json.readTree("{\"id\":\"t1\",\"processInstanceId\":\"p1\",\"taskDefinitionKey\":\"" + key
                + "\",\"assignee\":" + (asignado == null ? "null" : "\"" + asignado + "\"") + "}");
    }

    String codigo(Runnable accion) {
        return assertThrows(ApiException.class, accion::run).getCodigo();
    }

    @Test
    void definirDocente_completaConLaVariableYCambiaElEstado() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("definir_docente", "Jefe-Docente"));

        servicio.definirDocente("t1", jefeDocente, "docente-1");

        verify(motor).completar("t1", Map.of("docenteId", "docente-1"));
        verify(curso).setEstado(EstadoCurso.CONFIRMADO_PENDIENTE_INICIO);
    }

    @Test
    void definirDocente_docenteFueraDelEquipo_da400() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("definir_docente", "Jefe-Docente"));

        assertEquals("VALIDACION", codigo(() -> servicio.definirDocente("t1", jefeDocente, "empleado-administrativo")));
        verify(motor, never()).completar(anyString(), anyMap());
    }

    @Test
    void definirDocente_noAsignado_da403() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("definir_docente", "otro"));

        assertEquals("SIN_PERMISO", codigo(() -> servicio.definirDocente("t1", jefeDocente, "docente-1")));
    }

    @Test
    void definirDocente_otraTarea_da409() throws Exception {
        when(motor.tarea("t1")).thenReturn(tarea("evaluar_recursos", "Jefe-Docente"));

        assertEquals("ESTADO_INCOMPATIBLE", codigo(() -> servicio.definirDocente("t1", jefeDocente, "docente-1")));
    }
}
