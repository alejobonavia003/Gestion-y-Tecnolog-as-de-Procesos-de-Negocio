package ar.edu.cursos.controllers;

import java.util.UUID;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.cursos.services.InformeService;
import ar.edu.cursos.services.SesionService;
import jakarta.servlet.http.HttpServletRequest;

/** Endpoint #19 de ENDPOINTS_WEB.md (etapa 9). */
@RestController
@RequestMapping("/api/v1/cursos")
public class InformeController {

    private final InformeService informes;
    private final SesionService sesiones;

    public InformeController(InformeService informes, SesionService sesiones) {
        this.informes = informes;
        this.sesiones = sesiones;
    }

    @GetMapping("/{cursoId}/informe/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable UUID cursoId, HttpServletRequest request) {
        byte[] pdf = informes.pdf(cursoId, sesiones.requerir(request));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline().filename("informe-" + cursoId + ".pdf").build());
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
