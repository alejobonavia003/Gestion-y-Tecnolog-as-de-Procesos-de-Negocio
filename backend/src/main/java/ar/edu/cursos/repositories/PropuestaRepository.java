package ar.edu.cursos.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.cursos.model.Propuesta;

public interface PropuestaRepository extends JpaRepository<Propuesta, Long> {

    List<Propuesta> findByCursoIdOrderByVersionAsc(UUID cursoId);

    Optional<Propuesta> findByCursoIdAndVersion(UUID cursoId, int version);

    Optional<Propuesta> findFirstByCursoIdOrderByVersionDesc(UUID cursoId);
}
