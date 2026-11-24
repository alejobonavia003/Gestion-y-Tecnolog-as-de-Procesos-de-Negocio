package ar.edu.cursos.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.EstadoCurso;

public interface CursoRepository extends JpaRepository<Curso, UUID> {

    /** Portal de empresa: solo los cursos de esa empresa. */
    List<Curso> findByEmpresaIdOrderByCreadoEnDesc(Long empresaId);

    @EntityGraph(attributePaths = "empresa")
    Optional<Curso> findByClaveIdempotencia(String claveIdempotencia);

    @EntityGraph(attributePaths = "empresa")
    Optional<Curso> findByProcessInstanceId(String processInstanceId);

    Page<Curso> findByEmpresaIdOrderByCreadoEnDesc(Long empresaId, Pageable pageable);

    @Query("select distinct c.aula.id from Curso c where c.aula is not null and c.estado not in :estadosFinales")
    Set<Long> findAulaIdsOcupadas(@Param("estadosFinales") Collection<EstadoCurso> estadosFinales);
}
