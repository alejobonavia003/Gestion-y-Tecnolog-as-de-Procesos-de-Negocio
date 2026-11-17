package ar.edu.cursos.simulador;

import java.util.List;

public record SolicitudSimulacion(
        String cursoId,
        String tematica,
        String empresaNombre,
        String estado,
        Integer propuestaVersionVigente,
        List<String> fechasPosibles,
        List<String> accionesHabilitadas
) {
    public boolean admite(String accion) {
        return accionesHabilitadas != null && accionesHabilitadas.contains(accion);
    }
}
