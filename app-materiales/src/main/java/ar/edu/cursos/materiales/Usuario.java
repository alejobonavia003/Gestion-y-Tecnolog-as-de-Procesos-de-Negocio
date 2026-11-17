package ar.edu.cursos.materiales;
public record Usuario(String id, String nombre) {
    @Override public String toString() { return nombre + " (" + id + ")"; }
}
