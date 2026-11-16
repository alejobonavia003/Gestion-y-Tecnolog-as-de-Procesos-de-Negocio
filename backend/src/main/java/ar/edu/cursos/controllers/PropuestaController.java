
package ar.edu.cursos.controllers;

import ar.edu.cursos.dto.NuevaPropuestaRequest;
import ar.edu.cursos.dto.PropuestaCreadaResponse;
import ar.edu.cursos.dto.PropuestaItemResponse;
import ar.edu.cursos.services.PropuestaService;
import ar.edu.cursos.services.SesionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PropuestaController {

    private final PropuestaService propuestaService;
    private final SesionService sesionService;

    public PropuestaController(
            PropuestaService propuestaService,
            SesionService sesionService) {
        this.propuestaService = propuestaService;
        this.sesionService = sesionService;
    }

    @PostMapping("/tareas/{taskId}/propuesta")
    public ResponseEntity<PropuestaCreadaResponse> registrarPropuesta(
            @PathVariable String taskId,
            @Valid @RequestBody NuevaPropuestaRequest request,
            HttpServletRequest httpRequest) {

        PropuestaCreadaResponse response =
                propuestaService.registrarYCompletar(
                        taskId,
                        sesionService.requerirPersonal(httpRequest),
                        request
                );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/cursos/{cursoId}/propuestas")
    public ResponseEntity<Map<String, List<PropuestaItemResponse>>> listarPropuestas(
            @PathVariable UUID cursoId,
            HttpServletRequest httpRequest) {

        List<PropuestaItemResponse> items =
                propuestaService.listarPorCurso(
                        cursoId,
                        sesionService.requerir(httpRequest)
                );

        return ResponseEntity.ok(Map.of("items", items));
    }

    @GetMapping("/cursos/{cursoId}/propuestas/{version}/pdf")
    public ResponseEntity<byte[]> descargarPdf(
            @PathVariable UUID cursoId,
            @PathVariable int version,
            HttpServletRequest httpRequest) {

        byte[] pdf = propuestaService.obtenerPdf(
                cursoId,
                version,
                sesionService.requerir(httpRequest)
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);

        headers.setContentDisposition(
                ContentDisposition.inline()
                        .filename("propuesta-v" + version + ".pdf")
                        .build()
        );

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }
}
