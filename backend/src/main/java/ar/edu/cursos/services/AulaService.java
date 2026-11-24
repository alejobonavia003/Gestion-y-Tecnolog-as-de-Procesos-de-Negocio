package ar.edu.cursos.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.cursos.model.Aula;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.EstadoCurso;
import ar.edu.cursos.repositories.AulaRepository;
import ar.edu.cursos.repositories.CursoRepository;

@Service
@Transactional(readOnly = true)
public class AulaService {

    public static final Set<EstadoCurso> ESTADOS_FINALES = Set.of(
            EstadoCurso.FINALIZADO,
            EstadoCurso.CANCELADO,
            EstadoCurso.RECHAZADO,
            EstadoCurso.CERRADO_SIN_RESPUESTA,
            EstadoCurso.NO_VIABLE
    );

    private final AulaRepository aulas;
    private final CursoRepository cursos;

    public AulaService(AulaRepository aulas, CursoRepository cursos) {
        this.aulas = aulas;
        this.cursos = cursos;
    }

    /** Resumen de disponibilidad actual de las aulas físicas activas. */
    public Map<String, Object> resumenDisponibilidad() {
        List<Aula> activas = aulas.findByActivaTrueOrderByCodigoAsc();
        Set<Long> ocupadas = cursos.findAulaIdsOcupadas(ESTADOS_FINALES);

        int libres = 0;
        List<Map<String, Object>> detalle = new ArrayList<>();
        for (Aula aula : activas) {
            boolean disponible = !ocupadas.contains(aula.getId());
            if (disponible) libres++;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("codigo", aula.getCodigo());
            item.put("nombre", aula.getNombre());
            item.put("disponible", disponible);
            detalle.add(item);
        }

        Map<String, Object> resumen = new LinkedHashMap<>();
        resumen.put("total", activas.size());
        resumen.put("disponibles", libres);
        resumen.put("hayDisponibles", libres > 0);
        resumen.put("aulas", detalle);
        return resumen;
    }

    /**
     * Asigna automáticamente la primera aula física disponible al curso.
     * Un aula se considera ocupada mientras esté asignada a un curso no finalizado/cancelado.
     */
    @Transactional
    public synchronized Optional<Aula> reservarPrimeraDisponible(Curso curso) {
        if (curso.getAula() != null) {
            return Optional.of(curso.getAula());
        }
        List<Aula> activas = aulas.findByActivaTrueOrderByCodigoAsc();
        Set<Long> ocupadas = cursos.findAulaIdsOcupadas(ESTADOS_FINALES);
        for (Aula aula : activas) {
            if (!ocupadas.contains(aula.getId())) {
                curso.setAula(aula);
                cursos.save(curso);
                return Optional.of(aula);
            }
        }
        return Optional.empty();
    }
}
