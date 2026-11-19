package ar.edu.cursos.motor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.IdentityService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.idm.api.User;
import org.flowable.idm.api.UserQuery;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.service.delegate.DelegateTask;
import org.junit.jupiter.api.Test;

class ListenersTest {

    @Test
    void asignarListaListener_asignaAlAdministrativoConMenosTareasAsignadas() {
        IdentityService identityService = mock(IdentityService.class);
        TaskService taskService = mock(TaskService.class);
        UserQuery userQuery = mock(UserQuery.class);

        User jefe = mock(User.class);
        when(jefe.getId()).thenReturn("Jefe-Administrativo");
        User empleado = mock(User.class);
        when(empleado.getId()).thenReturn("empleado-administrativo");

        when(identityService.createUserQuery()).thenReturn(userQuery);
        when(userQuery.memberOfGroup("Equipo-Administrativo")).thenReturn(userQuery);
        when(userQuery.list()).thenReturn(new ArrayList<>(List.of(empleado, jefe)));

        TaskQuery queryJefe = mock(TaskQuery.class);
        when(queryJefe.active()).thenReturn(queryJefe);
        when(queryJefe.count()).thenReturn(3L);

        TaskQuery queryEmpleado = mock(TaskQuery.class);
        when(queryEmpleado.active()).thenReturn(queryEmpleado);
        when(queryEmpleado.count()).thenReturn(1L);

        TaskQuery baseQuery = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(baseQuery);
        when(baseQuery.taskAssignee("Jefe-Administrativo")).thenReturn(queryJefe);
        when(baseQuery.taskAssignee("empleado-administrativo")).thenReturn(queryEmpleado);

        DelegateTask task = mock(DelegateTask.class);
        when(task.getId()).thenReturn("task-100");
        when(task.getTaskDefinitionKey()).thenReturn("confeccionar_lista");

        AsignarListaListener listener = new AsignarListaListener();
        listener.aplicarAsignacion(task, identityService, taskService, "Equipo-Administrativo");

        verify(task).setAssignee("empleado-administrativo");
    }

    @Test
    void asignarListaListener_enEmpateEligePorOrdenAlfabeticoDeUserId() {
        IdentityService identityService = mock(IdentityService.class);
        TaskService taskService = mock(TaskService.class);
        UserQuery userQuery = mock(UserQuery.class);

        User u1 = mock(User.class);
        when(u1.getId()).thenReturn("empleado-administrativo");
        User u2 = mock(User.class);
        when(u2.getId()).thenReturn("Jefe-Administrativo");

        when(identityService.createUserQuery()).thenReturn(userQuery);
        when(userQuery.memberOfGroup("Equipo-Administrativo")).thenReturn(userQuery);
        when(userQuery.list()).thenReturn(new ArrayList<>(List.of(u1, u2)));

        TaskQuery q = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(q);
        when(q.taskAssignee(anyString())).thenReturn(q);
        when(q.active()).thenReturn(q);
        when(q.count()).thenReturn(2L);

        DelegateTask task = mock(DelegateTask.class);
        AsignarListaListener listener = new AsignarListaListener();
        listener.aplicarAsignacion(task, identityService, taskService, "Equipo-Administrativo");

        // "Jefe-Administrativo" < "empleado-administrativo" en orden ASCII/alfabético
        verify(task).setAssignee("Jefe-Administrativo");
    }

    @Test
    void asignarListaListener_sinIntegrantesLanzaExcepcion() {
        IdentityService identityService = mock(IdentityService.class);
        TaskService taskService = mock(TaskService.class);
        UserQuery userQuery = mock(UserQuery.class);

        when(identityService.createUserQuery()).thenReturn(userQuery);
        when(userQuery.memberOfGroup("Equipo-Administrativo")).thenReturn(userQuery);
        when(userQuery.list()).thenReturn(List.of());

        DelegateTask task = mock(DelegateTask.class);
        AsignarListaListener listener = new AsignarListaListener();

        assertThrows(FlowableException.class,
                () -> listener.aplicarAsignacion(task, identityService, taskService, "Equipo-Administrativo"));
    }

    @Test
    void finProcesoListener_resuelveEstadoFinalYCursoId() {
        DelegateExecution exec = mock(DelegateExecution.class);
        when(exec.getVariable("resultadoProceso")).thenReturn("SIN_RESPUESTA");
        when(exec.getProcessInstanceBusinessKey()).thenReturn("curso:11111111-1111-4111-8111-111111111111");

        assertEquals("CERRADO_SIN_RESPUESTA", FinProcesoListener.resolverEstadoFinal(exec));
        assertEquals("11111111-1111-4111-8111-111111111111", FinProcesoListener.resolverCursoId(exec));
    }
}
