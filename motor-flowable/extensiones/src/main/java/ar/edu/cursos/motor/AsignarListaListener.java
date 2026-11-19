package ar.edu.cursos.motor;

import java.util.Comparator;
import java.util.List;

import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.IdentityService;
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.flowable.engine.impl.util.CommandContextUtil;
import org.flowable.idm.api.User;
import org.flowable.task.service.delegate.DelegateTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task Listener (evento "create" en confeccionar_lista).
 * Consulta cuántas tareas de usuario pendientes tiene ASIGNADAS cada integrante
 * del grupo Administración (sin contar tareas donde el grupo es solo candidato)
 * y asigna la nueva tarea a quien tenga menos. En caso de empate, desempata por
 * ID de usuario en orden alfabético ascendente.
 */
public class AsignarListaListener implements TaskListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(AsignarListaListener.class);
    public static final String GRUPO_DEFAULT = "Equipo-Administrativo";

    @Override
    public void notify(DelegateTask delegateTask) {
        ProcessEngineConfigurationImpl config = CommandContextUtil.getProcessEngineConfiguration();
        if (config == null) {
            throw new FlowableException("No hay contexto de motor activo para ejecutar AsignarListaListener.");
        }
        aplicarAsignacion(delegateTask, config.getIdentityService(), config.getTaskService(), obtenerGrupo());
    }

    void aplicarAsignacion(DelegateTask delegateTask, IdentityService identityService, TaskService taskService, String grupoId) {
        List<User> integrantes = identityService.createUserQuery()
                .memberOfGroup(grupoId)
                .list();

        if (integrantes == null || integrantes.isEmpty()) {
            throw new FlowableException("El grupo '" + grupoId + "' no tiene integrantes configurados en Flowable IDM.");
        }

        integrantes.sort(Comparator.comparing(User::getId));

        String elegido = null;
        long menorCarga = Long.MAX_VALUE;
        String idTareaActual = delegateTask.getId();

        for (User usuario : integrantes) {
            String userId = usuario.getId();
            long asignadas = taskService.createTaskQuery()
                    .taskAssignee(userId)
                    .active()
                    .count();

            // Si la tarea actual ya figura asignada a este usuario en la sesión, no contarla como carga previa.
            if (idTareaActual != null && userId.equals(delegateTask.getAssignee()) && asignadas > 0) {
                asignadas--;
            }

            log.info("[AsignarListaListener] Administrativo '{}' tiene {} tarea(s) pendiente(s) asignada(s)", userId, asignadas);

            if (asignadas < menorCarga) {
                menorCarga = asignadas;
                elegido = userId;
            }
        }

        delegateTask.setAssignee(elegido);
        log.info("[AsignarListaListener] Tarea '{}' (id={}) asignada a '{}' (carga previa: {})",
                delegateTask.getTaskDefinitionKey(), idTareaActual, elegido, menorCarga);
    }

    private String obtenerGrupo() {
        String env = System.getenv("GRUPO_ADMINISTRACION");
        return (env != null && !env.isBlank()) ? env.trim() : GRUPO_DEFAULT;
    }
}
