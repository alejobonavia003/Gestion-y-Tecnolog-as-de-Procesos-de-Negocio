package ar.edu.cursos.dto;

public record TareaResumen(String taskId, String taskDefinitionKey, String nombre,
        String cursoId, String cursoNombre, String empresaNombre, String creada,
        String asignado, boolean reclamable) {}
