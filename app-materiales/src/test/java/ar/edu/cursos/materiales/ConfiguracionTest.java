package ar.edu.cursos.materiales;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
class ConfiguracionTest {
    @TempDir Path temp;
    @Test void archivoExplicitoConfiguraPostgresSinExponerClave() throws Exception {
        Path archivo=temp.resolve("materiales.env.local");
        Files.writeString(archivo,"FLOWABLE_JDBC_URL=jdbc:postgresql://127.0.0.1:5433/pruebas\nFLOWABLE_JDBC_USER=usuario\nFLOWABLE_JDBC_PASSWORD='clave=ficticia'\n");
        Configuracion c=Configuracion.cargar(archivo);
        assertEquals("clave=ficticia",c.clave());assertTrue(c.jdbcUrl().endsWith("/pruebas"));assertFalse(c.toString().contains("ficticia"));
        Files.writeString(archivo,"FLOWABLE_JDBC_URL=jdbc:h2:mem:aislada\n");
        assertThrows(IllegalArgumentException.class,()->Configuracion.cargar(archivo));
    }
}
