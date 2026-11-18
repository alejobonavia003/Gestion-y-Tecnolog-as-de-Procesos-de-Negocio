package ar.edu.cursos.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.cursos.dto.LoginEmpresaRequest;
import ar.edu.cursos.dto.LoginPersonalRequest;
import ar.edu.cursos.dto.SesionUsuario;
import ar.edu.cursos.services.SesionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Endpoints #2 a #5 de docs/BACKEND.md (contrato de endpoints). */
@RestController
@RequestMapping("/api/v1/sesiones")
public class SesionController {

    private final SesionService sesiones;

    public SesionController(SesionService sesiones) {
        this.sesiones = sesiones;
    }

    @PostMapping("/personal")
    public SesionUsuario loginPersonal(@Valid @RequestBody LoginPersonalRequest datos, HttpServletRequest request) {
        SesionUsuario sesion = sesiones.loginPersonal(datos.usuarioId().trim(), datos.clave());
        sesiones.guardar(request, sesion);
        return sesion;
    }

    @PostMapping("/empresa")
    public SesionUsuario loginEmpresa(@Valid @RequestBody LoginEmpresaRequest datos, HttpServletRequest request) {
        SesionUsuario sesion = sesiones.loginEmpresa(datos.usuario().trim(), datos.clave());
        sesiones.guardar(request, sesion);
        return sesion;
    }

    @GetMapping("/actual")
    public SesionUsuario actual(HttpServletRequest request) {
        return sesiones.requerir(request);
    }

    @DeleteMapping("/actual")
    public ResponseEntity<Void> cerrar(HttpServletRequest request) {
        sesiones.cerrar(request);
        return ResponseEntity.noContent().build();
    }
}
