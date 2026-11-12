package ar.edu.cursos.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.cursos.model.Curso;

public interface CursoRepository extends JpaRepository<Curso, UUID> {

    /** Portal de empresa: solo los cursos de esa empresa. */
    List<Curso> findByEmpresaIdOrderByCreadoEnDesc(Long empresaId);

    Optional<Curso> findByClaveIdempotencia(String claveIdempotencia);

    Optional<Curso> findByProcessInstanceId(String processInstanceId);
}
