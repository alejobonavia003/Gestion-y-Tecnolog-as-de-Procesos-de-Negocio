package ar.edu.cursos.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.SesionUsuario;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.CuentaEmpresa;
import ar.edu.cursos.repositories.CuentaEmpresaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Login y sesión. El personal se valida contra Flowable (no tiene cuentas propias);
 * las empresas, contra cuenta_empresa con BCrypt.
 * Los demás servicios usan requerirPersonal()/requerirEmpresa() para saber quién llama:
 * NUNCA se confía en un usuarioId o empresaId enviado por el navegador.
 */
@Service
public class SesionService {

    private static final String ATRIBUTO = "sesion";
    private static final ApiException CREDENCIALES_INCORRECTAS =
            new ApiException(HttpStatus.UNAUTHORIZED, "NO_AUTENTICADO", "Usuario o contraseña incorrectos.");

    private final FlowableClient flowable;
    private final CuentaEmpresaRepository cuentas;
    private final BCryptPasswordEncoder encriptador = new BCryptPasswordEncoder();
    private final String grupoAdministracion;
    private final String grupoDocentes;
    private final String jefeAdministrativo;
    private final String jefeDocente;

    public SesionService(FlowableClient flowable,
                         CuentaEmpresaRepository cuentas,
                         @Value("${cursos.grupo.administracion}") String grupoAdministracion,
                         @Value("${cursos.grupo.docentes}") String grupoDocentes,
                         @Value("${cursos.usuario.jefe-administrativo}") String jefeAdministrativo,
                         @Value("${cursos.usuario.jefe-docente}") String jefeDocente) {
        this.flowable = flowable;
        this.cuentas = cuentas;
        this.grupoAdministracion = grupoAdministracion;
        this.grupoDocentes = grupoDocentes;
        this.jefeAdministrativo = jefeAdministrativo;
        this.jefeDocente = jefeDocente;
    }

    // --- Login ------------------------------------------------------------------

    public SesionUsuario loginPersonal(String usuarioId, String clave) {
        if (!flowable.credencialesValidas(usuarioId, clave)) {
            throw CREDENCIALES_INCORRECTAS;
        }
        List<String> grupos = flowable.gruposDe(usuarioId).stream()
                .filter(g -> g.equals(grupoAdministracion) || g.equals(grupoDocentes))
                .toList();
        if (grupos.isEmpty()) {
            throw ApiException.sinPermiso("Tu usuario no pertenece a Administración ni al equipo docente.");
        }
        String nombre = flowable.buscarUsuario(usuarioId)
                .map(u -> (u.path("firstName").asText("") + " " + u.path("lastName").asText("")).trim())
                .filter(n -> !n.isBlank())
                .orElse(usuarioId);
        boolean esJefe = usuarioId.equals(jefeAdministrativo) || usuarioId.equals(jefeDocente);
        return SesionUsuario.personal(usuarioId, nombre, grupos, esJefe);
    }

    @Transactional(readOnly = true)
    public SesionUsuario loginEmpresa(String usuario, String clave) {
        CuentaEmpresa cuenta = cuentas.findByUsuario(usuario)
                .filter(CuentaEmpresa::isActiva)
                .filter(c -> encriptador.matches(clave, c.getClaveHash()))
                .orElseThrow(() -> CREDENCIALES_INCORRECTAS);
        return SesionUsuario.empresa(cuenta.getEmpresa().getId(), cuenta.getEmpresa().getNombre());
    }

    /** Hash BCrypt de una contraseña (al crear cuentas de empresa). */
    public String hashear(String clave) {
        return encriptador.encode(clave);
    }

    // --- Sesión HTTP --------------------------------------------------------------

    /** Guarda la sesión. Cambia el id de sesión para evitar "session fixation". */
    public void guardar(HttpServletRequest request, SesionUsuario sesion) {
        HttpSession anterior = request.getSession(false);
        if (anterior != null) {
            anterior.invalidate();
        }
        request.getSession(true).setAttribute(ATRIBUTO, sesion);
    }

    public Optional<SesionUsuario> actual(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        return sesion == null ? Optional.empty() : Optional.ofNullable((SesionUsuario) sesion.getAttribute(ATRIBUTO));
    }

    public SesionUsuario requerir(HttpServletRequest request) {
        return actual(request).orElseThrow(ApiException::noAutenticado);
    }

    public SesionUsuario requerirPersonal(HttpServletRequest request) {
        SesionUsuario sesion = requerir(request);
        if (!sesion.esPersonal()) {
            throw ApiException.sinPermiso("Esta operación es solo para el personal de la institución.");
        }
        return sesion;
    }

    public SesionUsuario requerirEmpresa(HttpServletRequest request) {
        SesionUsuario sesion = requerir(request);
        if (!sesion.esEmpresa()) {
            throw ApiException.sinPermiso("Esta operación es solo para empresas.");
        }
        return sesion;
    }

    public void cerrar(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
    }
}
