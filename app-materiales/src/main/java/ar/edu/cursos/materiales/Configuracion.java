package ar.edu.cursos.materiales;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

/** Archivo local explícito > entorno > .env de la raíz. No ejecuta comandos del archivo. */
public record Configuracion(String jdbcUrl, String usuario, String clave, String proceso, String tarea, String grupo) {
    public static Configuracion cargar(Path archivo) throws IOException {
        Map<String,String> v = new HashMap<>();
        Path raiz = Path.of(".env");
        if (!Files.exists(raiz)) raiz = Path.of("../.env");
        leer(raiz, v);
        v.putAll(System.getenv());
        if (archivo != null) {
            if (!Files.isRegularFile(archivo)) throw new IllegalArgumentException("No existe el archivo de configuración indicado.");
            leer(archivo, v);
        }
        String base = v.getOrDefault("POSTGRES_DB", "flowable");
        String url = v.getOrDefault("FLOWABLE_JDBC_URL", "jdbc:postgresql://127.0.0.1:5433/" + base);
        if (!url.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException("La app requiere PostgreSQL, la misma base que Flowable UI.");
        return new Configuracion(url, requerido(v,"FLOWABLE_JDBC_USER","POSTGRES_USER"),
            requerido(v,"FLOWABLE_JDBC_PASSWORD","POSTGRES_PASSWORD"),
            v.getOrDefault("FLOWABLE_PROCESO_CLAVE", "process"),
            v.getOrDefault("FLOWABLE_TAREA_MATERIALES", "preparar_materiales"),
            v.getOrDefault("GRUPO_ADMINISTRACION", "Equipo-Administrativo"));
    }
    private static String requerido(Map<String,String> v,String k,String alternativo) {
        String s=v.getOrDefault(k,v.get(alternativo));
        if(s==null || s.isBlank()) throw new IllegalArgumentException("Falta configurar " + k + " (o " + alternativo + ").");
        return s;
    }
    private static void leer(Path p, Map<String,String> v) throws IOException {
        if (!Files.exists(p)) return;
        for(String linea:Files.readAllLines(p)) {
            String s=linea.strip();
            if(s.isEmpty() || s.startsWith("#")) continue;
            if(s.startsWith("export ")) s=s.substring(7).strip();
            int i=s.indexOf('='); if(i<1) continue;
            String valor=s.substring(i+1).strip();
            if(valor.length()>=2 && ((valor.startsWith("\"") && valor.endsWith("\"")) || (valor.startsWith("'") && valor.endsWith("'")))) valor=valor.substring(1,valor.length()-1);
            v.put(s.substring(0,i).strip(),valor);
        }
    }
    @Override public String toString() { return "Configuracion[credenciales ocultas]"; }
}
