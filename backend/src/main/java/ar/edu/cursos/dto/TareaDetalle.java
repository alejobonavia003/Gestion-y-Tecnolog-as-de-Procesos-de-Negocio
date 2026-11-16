package ar.edu.cursos.dto;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

public record TareaDetalle(@JsonUnwrapped TareaResumen tarea, Map<String, Object> contexto) {}
