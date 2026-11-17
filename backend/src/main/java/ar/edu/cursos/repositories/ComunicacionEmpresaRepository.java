package ar.edu.cursos.repositories;

import ar.edu.cursos.model.ComunicacionEmpresa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ComunicacionEmpresaRepository extends JpaRepository<ComunicacionEmpresa, Long> {

    Optional<ComunicacionEmpresa> findByComunicacionId(String comunicacionId);
}