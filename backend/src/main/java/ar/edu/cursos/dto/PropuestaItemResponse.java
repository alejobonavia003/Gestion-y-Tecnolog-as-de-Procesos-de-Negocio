package ar.edu.cursos.dto;

import ar.edu.cursos.model.Propuesta;

import java.time.OffsetDateTime;
import java.util.List;

public record PropuestaItemResponse(
        Integer version,
        String programa,
        NuevaPropuestaRequest.PresupuestoDto presupuesto,
        List<OffsetDateTime> fechasPosibles,
        String estado,
        String observacionesEmpresa,
        OffsetDateTime creada
) {
    public static PropuestaItemResponse fromEntity(Propuesta p) {
        return new PropuestaItemResponse(
                p.getVersion(),
                p.getPrograma(),
                new NuevaPropuestaRequest.PresupuestoDto(
                        p.getPresupuestoImporte().toPlainString(),
                        p.getPresupuestoMoneda()
                ),
                p.getFechasPosibles(),
                p.getEstado().name(),
                p.getObservacionesEmpresa(),
                p.getCreadaEn()
        );
    }
}