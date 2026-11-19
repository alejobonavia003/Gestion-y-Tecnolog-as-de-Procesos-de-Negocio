package ar.edu.cursos.motor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Execution Listener (evento "end" del proceso).
 * Al finalizar la instancia del proceso en Flowable, actualiza el estado del curso
 * en la base de datos institucional según la variable resultadoProceso:
 * FINALIZADO, CANCELADO, RECHAZADO, CERRADO_SIN_RESPUESTA o NO_VIABLE.
 */
public class FinProcesoListener implements ExecutionListener {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(FinProcesoListener.class);

    private static final Set<String> ESTADOS_VALIDOS = Set.of(
            "FINALIZADO",
            "CANCELADO",
            "RECHAZADO",
            "CERRADO_SIN_RESPUESTA",
            "NO_VIABLE"
    );

    @Override
    public void notify(DelegateExecution execution) {
        String estadoFinal = resolverEstadoFinal(execution);
        String cursoId = resolverCursoId(execution);
        String processInstanceId = execution.getProcessInstanceId();

        if (estadoFinal == null) {
            log.warn("[FinProcesoListener] Instancia {} finalizó sin variable resultadoProceso válida. No se actualiza BD institucional.",
                    processInstanceId);
            return;
        }

        actualizarEstadoEnBaseInstitucional(cursoId, processInstanceId, estadoFinal);
    }

    static String resolverEstadoFinal(DelegateExecution execution) {
        Object valor = execution.getVariable("resultadoProceso");
        if (valor == null) {
            valor = execution.getVariable("resultado");
        }
        if (valor == null) {
            return null;
        }
        String texto = valor.toString().trim().toUpperCase(Locale.ROOT);
        if ("SIN_RESPUESTA".equals(texto)) {
            texto = "CERRADO_SIN_RESPUESTA";
        }
        return ESTADOS_VALIDOS.contains(texto) ? texto : null;
    }

    static String resolverCursoId(DelegateExecution execution) {
        Object varCursoId = execution.getVariable("cursoId");
        if (varCursoId != null && !varCursoId.toString().isBlank()) {
            return varCursoId.toString().trim();
        }
        String bk = execution.getProcessInstanceBusinessKey();
        if (bk != null && bk.startsWith("curso:") && bk.length() > 6) {
            return bk.substring(6).trim();
        }
        return null;
    }

    private void actualizarEstadoEnBaseInstitucional(String cursoId, String processInstanceId, String estadoFinal) {
        String usuario = firstNonBlank(
                System.getenv("INSTITUCION_DB_USER"),
                System.getenv("POSTGRES_USER"),
                System.getenv("SPRING_DATASOURCE_USERNAME"),
                "flowable"
        );
        String clave = firstNonBlank(
                System.getenv("INSTITUCION_DB_PASSWORD"),
                System.getenv("POSTGRES_PASSWORD"),
                System.getenv("SPRING_DATASOURCE_PASSWORD"),
                "flow10"
        );

        List<String> urls = construirUrlsCandidatas();
        SQLException ultimoError = null;

        for (String url : urls) {
            try (Connection conn = DriverManager.getConnection(url, usuario, clave)) {
                int filas = ejecutarUpdate(conn, cursoId, processInstanceId, estadoFinal);
                log.info("[FinProcesoListener] Curso (id={}, pid={}) actualizado a estado '{}' en BD institucional ({} fila/s).",
                        cursoId, processInstanceId, estadoFinal, filas);
                return;
            } catch (SQLException e) {
                ultimoError = e;
                log.debug("[FinProcesoListener] No se pudo conectar a {}: {}", url, e.getMessage());
            }
        }

        log.error("[FinProcesoListener] Falló la actualización del estado final '{}' para cursoId={}, pid={}",
                estadoFinal, cursoId, processInstanceId, ultimoError);
    }

    private int ejecutarUpdate(Connection conn, String cursoId, String processInstanceId, String estadoFinal) throws SQLException {
        if (cursoId != null && !cursoId.isBlank()) {
            String sqlPorId = "UPDATE curso SET estado = ?, actualizado_en = NOW() WHERE id = CAST(? AS uuid)";
            try (PreparedStatement ps = conn.prepareStatement(sqlPorId)) {
                ps.setString(1, estadoFinal);
                ps.setString(2, cursoId);
                int actualizados = ps.executeUpdate();
                if (actualizados > 0) {
                    return actualizados;
                }
            }
        }
        if (processInstanceId != null && !processInstanceId.isBlank()) {
            String sqlPorPid = "UPDATE curso SET estado = ?, actualizado_en = NOW() WHERE process_instance_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sqlPorPid)) {
                ps.setString(1, estadoFinal);
                ps.setString(2, processInstanceId);
                return ps.executeUpdate();
            }
        }
        return 0;
    }

    static List<String> construirUrlsCandidatas() {
        String explicita = System.getenv("INSTITUCION_DB_URL");
        if (explicita != null && !explicita.isBlank()) {
            return List.of(explicita.trim());
        }
        String dbName = firstNonBlank(System.getenv("INSTITUCION_DB_NAME"), "institucion");
        String springUrl = System.getenv("SPRING_DATASOURCE_URL");
        if (springUrl != null && springUrl.startsWith("jdbc:postgresql://")) {
            int ultimaBarra = springUrl.lastIndexOf('/');
            if (ultimaBarra > "jdbc:postgresql://".length()) {
                String derivada = springUrl.substring(0, ultimaBarra + 1) + dbName;
                return List.of(
                        derivada,
                        "jdbc:postgresql://postgres:5432/" + dbName,
                        "jdbc:postgresql://127.0.0.1:5433/" + dbName
                );
            }
        }
        return List.of(
                "jdbc:postgresql://postgres:5432/" + dbName,
                "jdbc:postgresql://127.0.0.1:5433/" + dbName
        );
    }

    private static String firstNonBlank(String... valores) {
        for (String v : valores) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
