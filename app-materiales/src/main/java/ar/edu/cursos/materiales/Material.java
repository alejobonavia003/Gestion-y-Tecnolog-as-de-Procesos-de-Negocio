package ar.edu.cursos.materiales;
import java.time.Instant;
public record Material(String id, String instancia, String cursoId, String tematica, String empresa,
                       String descripcion, String asignado, Instant creada) {}
