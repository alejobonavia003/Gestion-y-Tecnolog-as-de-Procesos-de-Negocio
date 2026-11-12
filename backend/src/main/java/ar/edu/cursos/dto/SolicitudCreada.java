package ar.edu.cursos.dto;

/** Respuesta 201 de POST /solicitudes. */
public record SolicitudCreada(String cursoId, String processInstanceId, String businessKey) {
}