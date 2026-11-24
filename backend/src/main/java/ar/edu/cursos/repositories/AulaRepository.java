package ar.edu.cursos.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.cursos.model.Aula;

public interface AulaRepository extends JpaRepository<Aula, Long> {

    List<Aula> findByActivaTrueOrderByCodigoAsc();

    Optional<Aula> findByCodigo(String codigo);
}
