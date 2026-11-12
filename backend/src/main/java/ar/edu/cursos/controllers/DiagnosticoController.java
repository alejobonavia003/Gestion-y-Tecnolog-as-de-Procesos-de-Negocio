package ar.edu.cursos.controllers;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.cursos.clients.FlowableClient;

/** TEMPORAL: para probar la conexión con Flowable. Se borra al terminar el backend. */
@RestController
@RequestMapping("/api/v1/diagnostico")
public class DiagnosticoController {

    private final FlowableClient flowable;

    public DiagnosticoController(FlowableClient flowable) {
        this.flowable = flowable;
    }

    @GetMapping("/flowable")
    public Map<String, Object> flowable() {
        return Map.of(
                "proceso", flowable.getProcesoClave(),
                "instanciasActivas", flowable.contarInstanciasActivas());
    }
}