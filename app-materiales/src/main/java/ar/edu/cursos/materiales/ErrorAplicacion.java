package ar.edu.cursos.materiales;

import org.flowable.common.engine.api.*;
public class ErrorAplicacion extends RuntimeException {
    public ErrorAplicacion(String mensaje) { super(mensaje); }
    public static String mensaje(Throwable e) {
        for(Throwable t=e;t!=null;t=t.getCause()) {
            if(t instanceof ErrorAplicacion) return t.getMessage();
            if(t instanceof FlowableTaskAlreadyClaimedException || t instanceof FlowableOptimisticLockingException)
                return "La tarea cambió o fue reclamada por otra persona. Actualizá la bandeja.";
            if(t instanceof FlowableObjectNotFoundException)
                return "La tarea ya no está disponible; puede haber sido completada o cancelada.";
        }
        return "No se pudo confirmar la operación. Revisá la conexión y actualizá la bandeja antes de reintentar. Si persiste, verificá los scripts/listeners del proceso.";
    }
}
