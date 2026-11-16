package ar.edu.cursos.dto;

import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Lo que se guarda en la sesión y lo que devuelve /sesiones/actual.
 * Personal: {tipo:"PERSONAL", usuarioId, nombre, grupos, esJefe}
 * Empresa:  {tipo:"EMPRESA", empresaId, nombre}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SesionUsuario(
        String tipo,
        String usuarioId,
        String nombre,
        List<String> grupos,
        Boolean esJefe,
        String empresaId) implements Serializable {

    public static SesionUsuario personal(String usuarioId, String nombre, List<String> grupos, boolean esJefe) {
        return new SesionUsuario("PERSONAL", usuarioId, nombre, List.copyOf(grupos), esJefe, null);
    }

    public static SesionUsuario empresa(Long empresaId, String nombre) {
        return new SesionUsuario("EMPRESA", null, nombre, null, null, String.valueOf(empresaId));
    }

    @JsonIgnore
    public boolean esPersonal() {
        return "PERSONAL".equals(tipo);
    }

    @JsonIgnore
    public boolean esEmpresa() {
        return "EMPRESA".equals(tipo);
    }

    /** empresaId como número, para buscar en la BD. */
    @JsonIgnore
    public Long empresaIdNumerico() {
        return empresaId == null ? null : Long.valueOf(empresaId);
    }
}
