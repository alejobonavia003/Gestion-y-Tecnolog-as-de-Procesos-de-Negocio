package ar.edu.cursos.materiales;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.idm.api.User;
import org.flowable.idm.api.UserQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnalisisHistoricoServiceTest {

    private ProcessEngine engine;
    private HistoryService history;
    private IdentityService identity;
    private HistoricProcessInstanceQuery procFinishedQuery;
    private HistoricProcessInstanceQuery procAllQuery;
    private HistoricTaskInstanceQuery taskQuery;
    private Configuracion config;
    private AnalisisHistoricoService service;

    @BeforeEach
    void setUp() {
        engine = mock(ProcessEngine.class);
        history = mock(HistoryService.class);
        identity = mock(IdentityService.class);
        config = new Configuracion(
                "jdbc:postgresql://localhost/flowable",
                "flowable",
                "flow10",
                "process",
                "preparar_materiales",
                "Equipo-Administrativo"
        );

        when(engine.getHistoryService()).thenReturn(history);
        when(engine.getIdentityService()).thenReturn(identity);

        procFinishedQuery = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        procAllQuery = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        when(history.createHistoricProcessInstanceQuery()).thenReturn(procFinishedQuery, procAllQuery);

        taskQuery = mock(HistoricTaskInstanceQuery.class, RETURNS_SELF);
        when(history.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);

        UserQuery adminQuery = mock(UserQuery.class);
        UserQuery docQuery = mock(UserQuery.class);
        UserQuery baseUserQuery = mock(UserQuery.class);
        when(identity.createUserQuery()).thenReturn(baseUserQuery);
        when(baseUserQuery.memberOfGroup("Equipo-Administrativo")).thenReturn(adminQuery);
        when(baseUserQuery.memberOfGroup("Equipo-Docente")).thenReturn(docQuery);

        List<User> admins = List.of(
                usuario("Jefe-Administrativo", "Marcos", "Jefe"),
                usuario("empleado-administrativo", "Ana", "Admin")
        );
        List<User> docentes = List.of(
                usuario("Jefe-Docente", "María", "Docente"),
                usuario("docente-1", "Juan", "Pérez"),
                usuario("docente-2", "Lucía", "Gómez")
        );
        when(adminQuery.list()).thenReturn(admins);
        when(docQuery.list()).thenReturn(docentes);

        service = new AnalisisHistoricoService(engine, config);
    }

    @Test
    void resultadosSolicitudes_calculaCantidadYPorcentajeSobreTerminadas() {
        HistoricProcessInstance i1 = instancia("p1", Map.of("resultadoProceso", "FINALIZADO"), "fin_finalizado");
        HistoricProcessInstance i2 = instancia("p2", Map.of("resultadoProceso", "FINALIZADO"), "fin_finalizado");
        HistoricProcessInstance i3 = instancia("p3", Map.of("resultadoProceso", "CANCELADO"), "fin_cancelado");
        HistoricProcessInstance i4 = instancia("p4", Map.of("resultadoProceso", "RECHAZADO"), "fin_rechazado_o_sin_respuesta");
        HistoricProcessInstance i5 = instancia("p5", Map.of("resultadoProceso", "NO_VIABLE"), "fin_no_viable");
        HistoricProcessInstance i6 = instancia("p6", Map.of("resultadoProceso", "CERRADO_SIN_RESPUESTA"), "fin_rechazado_o_sin_respuesta");
        HistoricProcessInstance i7 = instancia("p7", Map.of("cursoCancelado", true), "fin_cancelado");
        HistoricProcessInstance i8 = instancia("p8", Map.of("respuestaEmpresa", "RECHAZADA"), "fin_rechazado_o_sin_respuesta");

        when(procFinishedQuery.list()).thenReturn(List.of(i1, i2, i3, i4, i5, i6, i7, i8));
        when(procAllQuery.list()).thenReturn(List.of());
        when(taskQuery.list()).thenReturn(List.of());

        var reporte = service.generarReporte();
        assertEquals(8L, reporte.totalInstanciasTerminadas());
        assertEquals(5, reporte.resultadosSolicitudes().size());

        Map<String, AnalisisHistoricoService.FilaResultadoSolicitud> porCodigo = Map.of(
                reporte.resultadosSolicitudes().get(0).codigo(), reporte.resultadosSolicitudes().get(0),
                reporte.resultadosSolicitudes().get(1).codigo(), reporte.resultadosSolicitudes().get(1),
                reporte.resultadosSolicitudes().get(2).codigo(), reporte.resultadosSolicitudes().get(2),
                reporte.resultadosSolicitudes().get(3).codigo(), reporte.resultadosSolicitudes().get(3),
                reporte.resultadosSolicitudes().get(4).codigo(), reporte.resultadosSolicitudes().get(4)
        );

        assertEquals(2L, porCodigo.get("FINALIZADO").cantidad());
        assertEquals(25.0, porCodigo.get("FINALIZADO").porcentaje(), 0.01);

        assertEquals(2L, porCodigo.get("CANCELADO").cantidad());
        assertEquals(25.0, porCodigo.get("CANCELADO").porcentaje(), 0.01);

        assertEquals(2L, porCodigo.get("RECHAZADO").cantidad());
        assertEquals(25.0, porCodigo.get("RECHAZADO").porcentaje(), 0.01);

        assertEquals(1L, porCodigo.get("NO_VIABLE").cantidad());
        assertEquals(12.5, porCodigo.get("NO_VIABLE").porcentaje(), 0.01);

        assertEquals(1L, porCodigo.get("CERRADO_SIN_RESPUESTA").cantidad());
        assertEquals(12.5, porCodigo.get("CERRADO_SIN_RESPUESTA").porcentaje(), 0.01);
    }

    @Test
    void preparacionParalela_calculaPromediosYExcluyeCanceladasOIncompletas() {
        HistoricProcessInstance normal1 = instancia("c1", Map.of(), null);
        HistoricProcessInstance normal2 = instancia("c2", Map.of(), null);
        HistoricProcessInstance cancelada = instancia("c-canc", Map.of(), null);
        HistoricProcessInstance incompleta = instancia("c-inc", Map.of(), null);

        when(procFinishedQuery.list()).thenReturn(List.of());
        when(procAllQuery.list()).thenReturn(List.of(normal1, normal2, cancelada, incompleta));
        when(taskQuery.list()).thenReturn(List.of());

        // Curso 1: Inscripciones = 60s, Materiales = 120s, Recursos (con 2 acciones) = 180s
        HistoricActivityInstanceQuery q1 = mock(HistoricActivityInstanceQuery.class, RETURNS_SELF);
        List<HistoricActivityInstance> acts1 = List.of(
                actividad("recibir_nomina", 0L, 20_000L, null),
                actividad("confeccionar_lista", 20_000L, 60_000L, null),
                actividad("preparar_materiales", 0L, 120_000L, null),
                actividad("evaluar_recursos", 0L, 30_000L, null),
                actividad("reservar_aula_fisica", 30_000L, 40_000L, null),
                actividad("contratar_equipamiento", 30_000L, 180_000L, null),
                actividad("fin_preparar", 180_000L, 180_000L, null)
        );
        when(q1.list()).thenReturn(acts1);

        // Curso 2: Inscripciones = 120s, Materiales = 60s, Recursos (1 acción) = 60s
        HistoricActivityInstanceQuery q2 = mock(HistoricActivityInstanceQuery.class, RETURNS_SELF);
        List<HistoricActivityInstance> acts2 = List.of(
                actividad("recibir_nomina", 10_000L, 50_000L, null),
                actividad("confeccionar_lista", 50_000L, 130_000L, null),
                actividad("preparar_materiales", 10_000L, 70_000L, null),
                actividad("evaluar_recursos", 10_000L, 30_000L, null),
                actividad("reservar_aula_virtual", 30_000L, 70_000L, null),
                actividad("fin_preparar", 130_000L, 130_000L, null)
        );
        when(q2.list()).thenReturn(acts2);

        // Curso cancelado durante preparación (debe excluirse)
        HistoricActivityInstanceQuery qCanc = mock(HistoricActivityInstanceQuery.class, RETURNS_SELF);
        List<HistoricActivityInstance> actsCanc = List.of(
                actividad("recibir_nomina", 0L, 10_000L, null),
                actividad("confeccionar_lista", 10_000L, 20_000L, null),
                actividad("preparar_materiales", 0L, 30_000L, "cancelled"),
                actividad("evaluar_recursos", 0L, 10_000L, null),
                actividad("reservar_aula_fisica", 10_000L, 15_000L, null),
                actividad("marcar_cancelado", 30_000L, 31_000L, null)
        );
        when(qCanc.list()).thenReturn(actsCanc);

        // Curso con preparación aún en curso (contratar_equipamiento sin endTime -> debe excluirse)
        HistoricActivityInstanceQuery qInc = mock(HistoricActivityInstanceQuery.class, RETURNS_SELF);
        List<HistoricActivityInstance> actsInc = List.of(
                actividad("recibir_nomina", 0L, 10_000L, null),
                actividad("confeccionar_lista", 10_000L, 20_000L, null),
                actividad("preparar_materiales", 0L, 25_000L, null),
                actividad("evaluar_recursos", 0L, 10_000L, null),
                actividad("contratar_equipamiento", 10_000L, null, null)
        );
        when(qInc.list()).thenReturn(actsInc);

        when(history.createHistoricActivityInstanceQuery()).thenReturn(q1, q2, qCanc, qInc);

        var reporte = service.generarReporte();
        assertEquals(2L, reporte.totalCursosPreparacionCompleta());
        assertEquals(3, reporte.ramasPreparacion().size());

        // Promedio Inscripciones: (60s + 120s) / 2 = 90s (01m 30s)
        var rInsc = reporte.ramasPreparacion().get(0);
        assertEquals(90_000L, rInsc.duracionPromedioMs());
        assertEquals("01m 30s", rInsc.duracionPromedioFormateada());

        // Promedio Materiales: (120s + 60s) / 2 = 90s (01m 30s)
        var rMat = reporte.ramasPreparacion().get(1);
        assertEquals(90_000L, rMat.duracionPromedioMs());
        assertEquals("01m 30s", rMat.duracionPromedioFormateada());

        // Promedio Recursos: (180s + 60s) / 2 = 120s (02m 00s)
        var rRec = reporte.ramasPreparacion().get(2);
        assertEquals(120_000L, rRec.duracionPromedioMs());
        assertEquals("02m 00s", rRec.duracionPromedioFormateada());
    }

    @Test
    void distribucionTareas_cuentaSoloCompletadasExcluyendoPendientesYCanceladas() {
        when(procFinishedQuery.list()).thenReturn(List.of());
        when(procAllQuery.list()).thenReturn(List.of());

        List<HistoricTaskInstance> tareas = List.of(
                tareaHistorica("Jefe-Administrativo", new Date(1000L), null),
                tareaHistorica("Jefe-Administrativo", new Date(2000L), null),
                tareaHistorica("empleado-administrativo", new Date(3000L), null),
                // Tarea interrumpida por cancelación -> no debe contarse
                tareaHistorica("empleado-administrativo", new Date(4000L), "cancelled"),
                // Tarea pendiente sin endTime -> no debe contarse
                tareaHistorica("empleado-administrativo", null, null),
                tareaHistorica("Jefe-Docente", new Date(5000L), null),
                tareaHistorica("docente-1", new Date(6000L), null)
        );
        when(taskQuery.list()).thenReturn(tareas);

        var reporte = service.generarReporte();
        assertEquals(5, reporte.distribucionTareas().size());

        Map<String, Long> porUsuario = Map.of(
                reporte.distribucionTareas().get(0).usuarioId(), reporte.distribucionTareas().get(0).tareasCompletadas(),
                reporte.distribucionTareas().get(1).usuarioId(), reporte.distribucionTareas().get(1).tareasCompletadas(),
                reporte.distribucionTareas().get(2).usuarioId(), reporte.distribucionTareas().get(2).tareasCompletadas(),
                reporte.distribucionTareas().get(3).usuarioId(), reporte.distribucionTareas().get(3).tareasCompletadas(),
                reporte.distribucionTareas().get(4).usuarioId(), reporte.distribucionTareas().get(4).tareasCompletadas()
        );

        assertEquals(2L, porUsuario.get("Jefe-Administrativo"));
        assertEquals(1L, porUsuario.get("empleado-administrativo"));
        assertEquals(1L, porUsuario.get("Jefe-Docente"));
        assertEquals(1L, porUsuario.get("docente-1"));
        assertEquals(0L, porUsuario.get("docente-2"));
        assertTrue(reporte.formatearConsola().contains("4.3.1 Resultados de las solicitudes"));
        assertTrue(reporte.formatearConsola().contains("4.3.2 Análisis de la preparación paralela"));
        assertTrue(reporte.formatearConsola().contains("4.3.3 Distribución de tareas"));
    }

    private static User usuario(String id, String nombre, String apellido) {
        User u = mock(User.class);
        when(u.getId()).thenReturn(id);
        when(u.getFirstName()).thenReturn(nombre);
        when(u.getLastName()).thenReturn(apellido);
        return u;
    }

    private static HistoricProcessInstance instancia(String id, Map<String, Object> vars, String endActivityId) {
        HistoricProcessInstance i = mock(HistoricProcessInstance.class);
        when(i.getId()).thenReturn(id);
        when(i.getProcessVariables()).thenReturn(vars);
        when(i.getEndActivityId()).thenReturn(endActivityId);
        return i;
    }

    private static HistoricActivityInstance actividad(String activityId, Long inicioMs, Long finMs, String deleteReason) {
        HistoricActivityInstance a = mock(HistoricActivityInstance.class);
        when(a.getActivityId()).thenReturn(activityId);
        when(a.getStartTime()).thenReturn(inicioMs == null ? null : new Date(inicioMs));
        when(a.getEndTime()).thenReturn(finMs == null ? null : new Date(finMs));
        when(a.getDeleteReason()).thenReturn(deleteReason);
        return a;
    }

    private static HistoricTaskInstance tareaHistorica(String assignee, Date endTime, String deleteReason) {
        HistoricTaskInstance t = mock(HistoricTaskInstance.class);
        when(t.getAssignee()).thenReturn(assignee);
        when(t.getEndTime()).thenReturn(endTime);
        when(t.getDeleteReason()).thenReturn(deleteReason);
        return t;
    }
}
