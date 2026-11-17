package ar.edu.cursos.materiales;

import org.junit.jupiter.api.Test;
import org.flowable.engine.*;
import org.flowable.idm.api.User;
import org.flowable.task.api.*;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.common.engine.impl.interceptor.Command;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class MaterialesServiceTest {
    final ProcessEngine engine=mock(ProcessEngine.class,RETURNS_DEEP_STUBS);
    final Configuracion config=new Configuracion("jdbc:postgresql://localhost/flowable","db","secreto","process","preparar_materiales","Equipo-Administrativo");
    final MaterialesService service=new MaterialesService(engine,config);
    final org.flowable.idm.api.UserQuery usuarios=mock(org.flowable.idm.api.UserQuery.class,RETURNS_SELF);
    final org.flowable.idm.api.GroupQuery grupos=mock(org.flowable.idm.api.GroupQuery.class,RETURNS_SELF);
    final TaskQuery query=mock(TaskQuery.class,RETURNS_SELF);
    MaterialesServiceTest(){
        when(engine.getTaskService().createTaskQuery()).thenReturn(query);
        when(engine.getIdentityService().createUserQuery()).thenReturn(usuarios);
        when(engine.getIdentityService().createGroupQuery()).thenReturn(grupos);
        when(engine.getIdentityService().checkPassword("admin","clave")).thenReturn(true);
        when(engine.getIdentityService().createGroupQuery().groupId(config.grupo()).groupMember("admin").count()).thenReturn(1L);
        User user=mock(User.class);when(user.getFirstName()).thenReturn("Administrativo");
        when(engine.getIdentityService().createUserQuery().userId("admin").singleResult()).thenReturn(user);
        when(engine.getManagementService().executeCommand(any(Command.class))).thenAnswer(a->((Command<?>)a.getArgument(0)).execute(null));
    }
    void login(){service.ingresar("admin","clave".toCharArray());}
    Task tarea(String asignado){Task t=mock(Task.class);when(t.getAssignee()).thenReturn(asignado);when(query.singleResult()).thenReturn(t);return t;}
    @Test void login_validaIdentidadGrupoYBorraClave(){
        char[] password="clave".toCharArray();assertEquals("admin",service.ingresar("admin",password).id());
        assertArrayEquals(new char[5],password);
        assertThrows(ErrorAplicacion.class,()->service.ingresar("admin","incorrecta".toCharArray()));
        assertThrows(ErrorAplicacion.class,()->service.listar(false,0));
        when(engine.getIdentityService().createGroupQuery().groupId(config.grupo()).groupMember("admin").count()).thenReturn(0L);
        assertThrows(ErrorAplicacion.class,()->service.ingresar("admin","clave".toCharArray()));
    }
    @Test void disponibles_filtraProcesoTipoGrupoYSinAsignar(){
        login();when(query.listPage(0,20)).thenReturn(List.of());assertTrue(service.listar(true,0).isEmpty());
        verify(query).processDefinitionKey("process");verify(query).taskDefinitionKey("preparar_materiales");
        verify(query).taskCandidateGroup("Equipo-Administrativo");verify(query).taskUnassigned();
    }
    @Test void completar_rechazaSinReclamoAjenaODeOtroTipo(){
        login();tarea(null);assertThrows(ErrorAplicacion.class,()->service.completar("t"));
        tarea("otro");assertThrows(ErrorAplicacion.class,()->service.completar("t"));
        when(query.singleResult()).thenReturn(null);assertThrows(ErrorAplicacion.class,()->service.completar("t"));
        verify(engine.getTaskService(),never()).complete(anyString());
    }
    @Test void reclamar_exigeCandidaturaYTareaLibre(){
        login();tarea(null);when(engine.getTaskService().getIdentityLinksForTask("t")).thenReturn(List.of());
        assertThrows(ErrorAplicacion.class,()->service.reclamar("t"));
        IdentityLink link=mock(IdentityLink.class);when(link.getType()).thenReturn("candidate");when(link.getGroupId()).thenReturn(config.grupo());
        when(engine.getTaskService().getIdentityLinksForTask("t")).thenReturn(List.of(link));service.reclamar("t");
        verify(engine.getTaskService()).claim("t","admin");tarea("otro");assertThrows(ErrorAplicacion.class,()->service.reclamar("t"));
    }
    @Test void completar_noEnviaVariablesYLimpiaContextoInclusoConFallo(){
        login();tarea("admin");service.completar("t");verify(engine.getTaskService()).complete("t");
        var taskService=engine.getTaskService();
        doThrow(new IllegalStateException()).when(taskService).complete("t");
        assertThrows(IllegalStateException.class,()->service.completar("t"));
        verify(engine.getIdentityService(),times(2)).setAuthenticatedUserId(null);
        service.salir();assertThrows(ErrorAplicacion.class,()->service.completar("t"));
    }
}
