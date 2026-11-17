package ar.edu.cursos.materiales;

import java.util.*;
import java.util.function.Supplier;
import org.flowable.engine.*;
import org.flowable.task.api.*;

/** Todas las operaciones vuelven a comprobar sesión, tipo de tarea y permisos. */
public final class MaterialesService {
    private final ProcessEngine engine;
    private final Configuracion config;
    private Usuario sesion;
    public MaterialesService(ProcessEngine engine, Configuracion config) { this.engine=engine; this.config=config; }
    public synchronized Usuario ingresar(String id, char[] clave) {
        sesion=null;
        try {
            if(id==null || id.isBlank() || clave.length==0 || !engine.getIdentityService().checkPassword(id.strip(),new String(clave)))
                throw new ErrorAplicacion("Usuario o contraseña incorrectos.");
            String usuario=id.strip();
            if(!esAdministrativo(usuario)) throw new ErrorAplicacion("Solo puede ingresar personal de Administración.");
            var u=engine.getIdentityService().createUserQuery().userId(usuario).singleResult();
            String nombre=((u.getFirstName()==null?"":u.getFirstName())+" "+(u.getLastName()==null?"":u.getLastName())).strip();
            sesion=new Usuario(usuario,nombre.isBlank()?usuario:nombre);
            return sesion;
        } finally { Arrays.fill(clave,'\0'); }
    }
    public synchronized void salir() { sesion=null; }
    private boolean esAdministrativo(String id) {
        return engine.getIdentityService().createGroupQuery().groupId(config.grupo()).groupMember(id).count()>0;
    }
    private Usuario requerir() {
        if(sesion==null) throw new ErrorAplicacion("Ingresá nuevamente para continuar.");
        if(!esAdministrativo(sesion.id())) { sesion=null; throw new ErrorAplicacion("El usuario ya no pertenece a Administración."); }
        return sesion;
    }
    private <T> T como(Usuario u, Supplier<T> operacion) {
        engine.getIdentityService().setAuthenticatedUserId(u.id());
        try { return operacion.get(); }
        finally { engine.getIdentityService().setAuthenticatedUserId(null); }
    }
    private TaskQuery consulta() {
        return engine.getTaskService().createTaskQuery().processDefinitionKey(config.proceso())
            .taskDefinitionKey(config.tarea()).active();
    }
    public synchronized long cantidad(boolean disponibles) {
        Usuario u=requerir(); return filtro(u,disponibles).count();
    }
    private TaskQuery filtro(Usuario u, boolean disponibles) {
        return disponibles ? consulta().taskCandidateGroup(config.grupo()).taskUnassigned() : consulta().taskAssignee(u.id());
    }
    public synchronized List<Material> listar(boolean disponibles,int pagina) {
        Usuario u=requerir();
        if(pagina<0 || pagina>100000) throw new ErrorAplicacion("Página inválida.");
        return como(u,()->filtro(u,disponibles).orderByTaskCreateTime().asc().listPage(pagina*20,20).stream().map(this::resumen).toList());
    }
    public synchronized Material detalle(String id) {
        Usuario u=requerir(); return como(u,()-> {
            Task t=tarea(id);
            if(!u.id().equals(t.getAssignee()) && !(t.getAssignee()==null && candidata(id)))
                throw new ErrorAplicacion("No tenés permiso para consultar esta tarea.");
            return resumen(t);
        });
    }
    public synchronized void reclamar(String id) {
        Usuario u=requerir(); como(u,()->engine.getManagementService().executeCommand(ctx->{
            Task t=tarea(id);
            if(t.getAssignee()!=null) throw new ErrorAplicacion("La tarea ya fue reclamada. Actualizá la bandeja.");
            if(!candidata(id)) throw new ErrorAplicacion("No sos candidato para esta tarea.");
            engine.getTaskService().claim(id,u.id()); return null;
        }));
    }
    public synchronized void completar(String id) {
        Usuario u=requerir(); como(u,()->engine.getManagementService().executeCommand(ctx->{
            Task t=tarea(id);
            if(!u.id().equals(t.getAssignee())) throw new ErrorAplicacion("Solo el asignado puede completar. Reclamá primero la tarea disponible.");
            engine.getTaskService().complete(id); return null;
        }));
    }
    private Task tarea(String id) {
        Task t=consulta().taskId(id).singleResult();
        if(t==null) throw new ErrorAplicacion("La tarea no está activa o no es de materiales de este proceso. Actualizá la bandeja.");
        return t;
    }
    private boolean candidata(String id) {
        return engine.getTaskService().getIdentityLinksForTask(id).stream()
            .anyMatch(l->"candidate".equals(l.getType()) && config.grupo().equals(l.getGroupId()));
    }
    private Material resumen(Task t) {
        var v=engine.getRuntimeService().getVariables(t.getProcessInstanceId(),List.of("cursoId","tematica","empresaNombre","descripcion"));
        return new Material(t.getId(),t.getProcessInstanceId(),texto(v,"cursoId"),texto(v,"tematica"),texto(v,"empresaNombre"),
            texto(v,"descripcion"),t.getAssignee(),t.getCreateTime()==null?null:t.getCreateTime().toInstant());
    }
    private static String texto(Map<String,Object> v,String key) {
        Object o=v.get(key); return o instanceof String s?s:"";
    }
}
