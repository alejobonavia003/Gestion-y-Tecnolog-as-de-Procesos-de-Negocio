package ar.edu.cursos.simulador;

import java.util.List;

public record SolicitudSimulacion(
        String cursoId,
        String tematica,
        String empresaNombre,
        String estado,
        Integer propuestaVersionVigente,
        String programaPropuesto,
        String presupuestoPropuesto,
        List<String> fechasPosibles,
        List<String> accionesHabilitadas
) {
    public boolean admite(String accion) {
        return accionesHabilitadas != null && accionesHabilitadas.contains(accion);
    }

    public boolean tienePropuesta() {
        return propuestaVersionVigente != null && propuestaVersionVigente > 0;
    }
}
