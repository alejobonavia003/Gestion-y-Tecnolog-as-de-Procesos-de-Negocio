package ar.edu.cursos.simulador;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public record Configuracion(String baseUrl) {

    public static Configuracion cargar(Path archivo) throws IOException {
        Map<String, String> v = new HashMap<>();
        Path raiz = Path.of(".env");
        if (!Files.exists(raiz)) {
            raiz = Path.of("../.env");
        }
        leer(raiz, v);
        v.putAll(System.getenv());
        if (archivo != null) {
            if (!Files.isRegularFile(archivo)) {
                throw new IllegalArgumentException("No existe el archivo de configuración indicado.");
            }
            leer(archivo, v);
        }
        String url = v.getOrDefault("SIMULADOR_BACKEND_URL", "http://localhost:8081/api/v1");
        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return new Configuracion(url);
    }

    private static void leer(Path p, Map<String, String> v) throws IOException {
        if (!Files.exists(p)) {
            return;
        }
        for (String linea : Files.readAllLines(p)) {
            String s = linea.strip();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            if (s.startsWith("export ")) {
                s = s.substring(7).strip();
            }
            int i = s.indexOf('=');
            if (i < 1) {
                continue;
            }
            String valor = s.substring(i + 1).strip();
            if (valor.length() >= 2 && ((valor.startsWith("\"") && valor.endsWith("\"")) || (valor.startsWith("'") && valor.endsWith("'")))) {
                valor = valor.substring(1, valor.length() - 1);
            }
            v.put(s.substring(0, i).strip(), valor);
        }
    }
}
