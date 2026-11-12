package ar.edu.cursos.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ar.edu.cursos.model.CuentaEmpresa;

public interface CuentaEmpresaRepository extends JpaRepository<CuentaEmpresa, Long> {

    Optional<CuentaEmpresa> findByUsuario(String usuario);

    Optional<CuentaEmpresa> findByEmpresaId(Long empresaId);
}
