package ar.edu.cursos.materiales;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.flowable.engine.HistoryService;
import org.flowable.engine.IdentityService;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.idm.api.User;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;

/**
 * Consultas históricas sobre las ejecuciones del proceso en Flowable (§4.3 de la consigna):
 *  - 4.3.1 Resultados de las solicitudes (cantidad y porcentaje sobre el total de instancias terminadas).
 *  - 4.3.2 Análisis de la preparación paralela (duración promedio de inscripciones, materiales y recursos
 *          en cursos que completaron normalmente la preparación, excluyendo cancelaciones).
 *  - 4.3.3 Distribución de tareas (cantidad de tareas de usuario completadas por cada administrativo y docente,
 *          excluyendo tareas pendientes e interrumpidas o canceladas).
 */
public final class AnalisisHistoricoService {

    public static final String GRUPO_DOCENTES_DEFAULT = "Equipo-Docente";

    private static final List<Map.Entry<String, String>> CATEGORIAS_RESULTADO = List.of(
            Map.entry("FINALIZADO", "Curso finalizado"),
            Map.entry("CANCELADO", "Cancelación"),
            Map.entry("RECHAZADO", "Rechazo de la propuesta"),
            Map.entry("NO_VIABLE", "Solicitud no viable"),
            Map.entry("CERRADO_SIN_RESPUESTA", "Cierre por falta de respuesta")
    );

    private static final Set<String> ACTIVIDADES_CANCELACION_PREPARACION = Set.of(
            "sp_cancelacion",
            "marcar_cancelado",
            "confirmar_cancelacion",
            "fin_cancelacion",
            "marcar_sin_aulas",
            "fin_sin_aulas",
            "notificarCierre"
    );

    private static final Set<String> ACTIVIDADES_ACCIONES_RECURSOS = Set.of(
            "reservar_aula_fisica",
            "reservar_aula_virtual",
            "contratar_equipamiento"
    );

    private static final Set<String> ACTIVIDADES_PREPARACION = Set.of(
            "recibir_nomina",
            "confeccionar_lista",
            "preparar_materiales",
            "evaluar_recursos",
            "reservar_aula_fisica",
            "reservar_aula_virtual",
            "contratar_equipamiento"
    );

    public record FilaResultadoSolicitud(
            String codigo,
            String resultado,
            long cantidad,
            double porcentaje
    ) {}

    public record FilaRamaPreparacion(
            String codigo,
            String rama,
            long cursosConsiderados,
            long duracionPromedioMs,
            String duracionPromedioFormateada
    ) {}

    public record FilaDistribucionTarea(
            String usuarioId,
            String nombre,
            String rol,
            long tareasCompletadas
    ) {}

    public record ReporteHistorico(
            long totalInstanciasTerminadas,
            List<FilaResultadoSolicitud> resultadosSolicitudes,
            long totalCursosPreparacionCompleta,
            List<FilaRamaPreparacion> ramasPreparacion,
            List<FilaDistribucionTarea> distribucionTareas
    ) {
        public String formatearConsola() {
            StringBuilder sb = new StringBuilder();
            sb.append("================================================================================\n");
            sb.append(" 4.3 ANÁLISIS DE LAS EJECUCIONES DEL PROCESO (HISTORIAL FLOWABLE)\n");
            sb.append("================================================================================\n\n");

            sb.append("4.3.1 Resultados de las solicitudes (Total instancias terminadas: ")
              .append(totalInstanciasTerminadas).append(")\n");
            sb.append("--------------------------------------------------------------------------------\n");
            sb.append(String.format(Locale.ROOT, "%-36s | %10s | %12s%n", "Resultado", "Cantidad", "Porcentaje"));
            sb.append("--------------------------------------------------------------------------------\n");
            for (FilaResultadoSolicitud f : resultadosSolicitudes) {
                sb.append(String.format(Locale.ROOT, "%-36s | %10d | %11.2f%%%n",
                        f.resultado(), f.cantidad(), f.porcentaje()));
            }
            sb.append("--------------------------------------------------------------------------------\n\n");

            sb.append("4.3.2 Análisis de la preparación paralela (Cursos con preparación completa: ")
              .append(totalCursosPreparacionCompleta).append(")\n");
            sb.append("--------------------------------------------------------------------------------\n");
            sb.append(String.format(Locale.ROOT, "%-36s | %10s | %22s%n",
                    "Rama de preparación", "Cursos", "Duración promedio"));
            sb.append("--------------------------------------------------------------------------------\n");
            for (FilaRamaPreparacion r : ramasPreparacion) {
                sb.append(String.format(Locale.ROOT, "%-36s | %10d | %22s%n",
                        r.rama(), r.cursosConsiderados(), r.duracionPromedioFormateada()));
            }
            sb.append("--------------------------------------------------------------------------------\n\n");

            sb.append("4.3.3 Distribución de tareas completadas por personal\n");
            sb.append("--------------------------------------------------------------------------------\n");
            sb.append(String.format(Locale.ROOT, "%-25s | %-22s | %-15s | %10s%n",
                    "Usuario", "Nombre", "Rol / Grupo", "Tareas"));
            sb.append("--------------------------------------------------------------------------------\n");
            for (FilaDistribucionTarea d : distribucionTareas) {
                sb.append(String.format(Locale.ROOT, "%-25s | %-22s | %-15s | %10d%n",
                        d.usuarioId(), recortar(d.nombre(), 22), d.rol(), d.tareasCompletadas()));
            }
            sb.append("--------------------------------------------------------------------------------");
            return sb.toString();
        }

        private static String recortar(String texto, int max) {
            if (texto == null) return "";
            return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
        }
    }

    private final ProcessEngine engine;
    private final Configuracion config;

    public AnalisisHistoricoService(ProcessEngine engine, Configuracion config) {
        this.engine = engine;
        this.config = config;
    }

    public ReporteHistorico generarReporte() {
        HistoryService history = engine.getHistoryService();
        IdentityService identity = engine.getIdentityService();

        ResultadoSolicitudesCalculado resSolicitudes = calcularResultadosSolicitudes(history);
        PreparacionCalculada resPreparacion = calcularPreparacionParalela(history);
        List<FilaDistribucionTarea> resTareas = calcularDistribucionTareas(history, identity);

        return new ReporteHistorico(
                resSolicitudes.totalTerminadas(),
                resSolicitudes.filas(),
                resPreparacion.cursosCompletados(),
                resPreparacion.filas(),
                resTareas
        );
    }

    private record ResultadoSolicitudesCalculado(long totalTerminadas, List<FilaResultadoSolicitud> filas) {}

    private ResultadoSolicitudesCalculado calcularResultadosSolicitudes(HistoryService history) {
        List<HistoricProcessInstance> terminadas = history.createHistoricProcessInstanceQuery()
                .processDefinitionKey(config.proceso())
                .finished()
                .includeProcessVariables()
                .list();

        Map<String, Long> conteo = new LinkedHashMap<>();
        for (var cat : CATEGORIAS_RESULTADO) {
            conteo.put(cat.getKey(), 0L);
        }

        long totalValidas = 0L;
        if (terminadas != null) {
            for (HistoricProcessInstance inst : terminadas) {
                String clasificacion = clasificarResultadoInstancia(history, inst);
                if (clasificacion != null && conteo.containsKey(clasificacion)) {
                    conteo.put(clasificacion, conteo.get(clasificacion) + 1L);
                    totalValidas++;
                }
            }
        }

        List<FilaResultadoSolicitud> filas = new ArrayList<>();
        for (var cat : CATEGORIAS_RESULTADO) {
            long cant = conteo.getOrDefault(cat.getKey(), 0L);
            double pct = totalValidas == 0L ? 0.0 : (cant * 100.0) / totalValidas;
            filas.add(new FilaResultadoSolicitud(cat.getKey(), cat.getValue(), cant, pct));
        }

        return new ResultadoSolicitudesCalculado(totalValidas, filas);
    }

    private String clasificarResultadoInstancia(HistoryService history, HistoricProcessInstance inst) {
        Map<String, Object> vars = new LinkedHashMap<>();
        if (inst.getProcessVariables() != null && !inst.getProcessVariables().isEmpty()) {
            vars.putAll(inst.getProcessVariables());
        } else if (inst.getId() != null) {
            List<HistoricVariableInstance> histVars = history.createHistoricVariableInstanceQuery()
                    .processInstanceId(inst.getId())
                    .list();
            if (histVars != null) {
                for (HistoricVariableInstance hv : histVars) {
                    if (hv.getVariableName() != null && hv.getValue() != null) {
                        vars.put(hv.getVariableName(), hv.getValue());
                    }
                }
            }
        }

        String normalizado = normalizarResultado(vars.get("resultadoProceso"));
        if (normalizado == null) {
            normalizado = normalizarResultado(vars.get("resultado"));
        }
        if (normalizado != null) {
            return normalizado;
        }

        // Respaldo por variables de flujo o evento de fin alcanzado
        String endId = inst.getEndActivityId() == null ? "" : inst.getEndActivityId().trim();
        if (esTrue(vars.get("cursoCancelado")) || "fin_cancelado".equals(endId)) {
            return "CANCELADO";
        }
        if (esFalse(vars.get("resultadoViabilidad")) || "fin_no_viable".equals(endId)) {
            return "NO_VIABLE";
        }
        String respEmpresa = texto(vars.get("respuestaEmpresa")).toUpperCase(Locale.ROOT);
        if ("RECHAZADA".equals(respEmpresa) || "RECHAZADO".equals(respEmpresa)) {
            return "RECHAZADO";
        }
        if ("fin_rechazado_o_sin_respuesta".equals(endId)) {
            return "CERRADO_SIN_RESPUESTA";
        }
        if ("fin_finalizado".equals(endId)) {
            return "FINALIZADO";
        }
        // Si la instancia no fue eliminada manualmente por REST y terminó sin marca explícita
        if (inst.getDeleteReason() == null || inst.getDeleteReason().isBlank()) {
            return "FINALIZADO";
        }
        return null;
    }

    private static String normalizarResultado(Object valor) {
        if (valor == null) return null;
        String t = valor.toString().trim().toUpperCase(Locale.ROOT);
        return switch (t) {
            case "FINALIZADO" -> "FINALIZADO";
            case "CANCELADO" -> "CANCELADO";
            case "RECHAZADO", "RECHAZADA" -> "RECHAZADO";
            case "NO_VIABLE" -> "NO_VIABLE";
            case "CERRADO_SIN_RESPUESTA", "SIN_RESPUESTA" -> "CERRADO_SIN_RESPUESTA";
            default -> null;
        };
    }

    private record PreparacionCalculada(long cursosCompletados, List<FilaRamaPreparacion> filas) {}

    private PreparacionCalculada calcularPreparacionParalela(HistoryService history) {
        List<HistoricProcessInstance> instancias = history.createHistoricProcessInstanceQuery()
                .processDefinitionKey(config.proceso())
                .list();

        long cursosCompletados = 0L;
        long sumaInscripcionesMs = 0L;
        long sumaMaterialesMs = 0L;
        long sumaRecursosMs = 0L;

        if (instancias != null) {
            for (HistoricProcessInstance inst : instancias) {
                if (inst.getId() == null) continue;
                List<HistoricActivityInstance> acts = history.createHistoricActivityInstanceQuery()
                        .processInstanceId(inst.getId())
                        .list();
                if (acts == null || acts.isEmpty()) continue;

                DuracionRamasCurso duracion = evaluarPreparacionCurso(acts);
                if (duracion != null) {
                    cursosCompletados++;
                    sumaInscripcionesMs += duracion.inscripcionesMs();
                    sumaMaterialesMs += duracion.materialesMs();
                    sumaRecursosMs += duracion.recursosMs();
                }
            }
        }

        long promInscripciones = cursosCompletados == 0L ? 0L : Math.round((double) sumaInscripcionesMs / cursosCompletados);
        long promMateriales = cursosCompletados == 0L ? 0L : Math.round((double) sumaMaterialesMs / cursosCompletados);
        long promRecursos = cursosCompletados == 0L ? 0L : Math.round((double) sumaRecursosMs / cursosCompletados);

        List<FilaRamaPreparacion> filas = List.of(
                new FilaRamaPreparacion(
                        "INSCRIPCIONES",
                        "Rama de inscripciones",
                        cursosCompletados,
                        promInscripciones,
                        formatearDuracion(promInscripciones, cursosCompletados)
                ),
                new FilaRamaPreparacion(
                        "MATERIALES",
                        "Rama de materiales",
                        cursosCompletados,
                        promMateriales,
                        formatearDuracion(promMateriales, cursosCompletados)
                ),
                new FilaRamaPreparacion(
                        "RECURSOS",
                        "Rama de recursos",
                        cursosCompletados,
                        promRecursos,
                        formatearDuracion(promRecursos, cursosCompletados)
                )
        );

        return new PreparacionCalculada(cursosCompletados, filas);
    }

    private record DuracionRamasCurso(long inscripcionesMs, long materialesMs, long recursosMs) {}

    private DuracionRamasCurso evaluarPreparacionCurso(List<HistoricActivityInstance> acts) {
        // Excluir cualquier preparación interrumpida por cancelación o falta de aulas
        for (HistoricActivityInstance a : acts) {
            String actId = a.getActivityId();
            if (actId == null) continue;
            if (ACTIVIDADES_CANCELACION_PREPARACION.contains(actId)) {
                return null;
            }
            if (ACTIVIDADES_PREPARACION.contains(actId)) {
                if (a.getDeleteReason() != null && !a.getDeleteReason().isBlank()) {
                    return null;
                }
                if (a.getEndTime() == null) {
                    return null; // Rama todavía en ejecución
                }
            }
        }

        // 1. Rama de inscripciones: desde activación (recibir_nomina) hasta fin de confeccionar_lista
        Date inicioInscripciones = minInicio(acts, Set.of("recibir_nomina"));
        if (inicioInscripciones == null) {
            inicioInscripciones = minInicio(acts, Set.of("confeccionar_lista"));
        }
        Date finInscripciones = maxFin(acts, Set.of("confeccionar_lista"));
        if (inicioInscripciones == null || finInscripciones == null) {
            return null;
        }

        // 2. Rama de materiales: desde activación hasta fin de preparar_materiales
        Date inicioMateriales = minInicio(acts, Set.of("preparar_materiales"));
        Date finMateriales = maxFin(acts, Set.of("preparar_materiales"));
        if (inicioMateriales == null || finMateriales == null) {
            return null;
        }

        // 3. Rama de recursos: desde activación de evaluar_recursos hasta completar todas las acciones seleccionadas
        Date inicioRecursos = minInicio(acts, Set.of("evaluar_recursos"));
        Date finEvaluar = maxFin(acts, Set.of("evaluar_recursos"));
        Date finAccionesRecursos = maxFin(acts, ACTIVIDADES_ACCIONES_RECURSOS);
        if (inicioRecursos == null || finEvaluar == null || finAccionesRecursos == null) {
            return null;
        }
        Date finGatewayRecursos = maxFin(acts, Set.of("gw_recursos_listos"));
        Date finRecursos = (finGatewayRecursos != null && finGatewayRecursos.after(finAccionesRecursos))
                ? finGatewayRecursos
                : finAccionesRecursos;

        long dInscripciones = Math.max(0L, finInscripciones.getTime() - inicioInscripciones.getTime());
        long dMateriales = Math.max(0L, finMateriales.getTime() - inicioMateriales.getTime());
        long dRecursos = Math.max(0L, finRecursos.getTime() - inicioRecursos.getTime());

        return new DuracionRamasCurso(dInscripciones, dMateriales, dRecursos);
    }

    private static Date minInicio(List<HistoricActivityInstance> acts, Set<String> ids) {
        Date min = null;
        for (HistoricActivityInstance a : acts) {
            if (a.getActivityId() != null && ids.contains(a.getActivityId()) && a.getStartTime() != null) {
                if (min == null || a.getStartTime().before(min)) {
                    min = a.getStartTime();
                }
            }
        }
        return min;
    }

    private static Date maxFin(List<HistoricActivityInstance> acts, Set<String> ids) {
        Date max = null;
        for (HistoricActivityInstance a : acts) {
            if (a.getActivityId() != null && ids.contains(a.getActivityId()) && a.getEndTime() != null) {
                if (max == null || a.getEndTime().after(max)) {
                    max = a.getEndTime();
                }
            }
        }
        return max;
    }

    private List<FilaDistribucionTarea> calcularDistribucionTareas(HistoryService history, IdentityService identity) {
        Map<String, InfoPersonal> personal = cargarPersonal(identity);

        List<HistoricTaskInstance> tareas = history.createHistoricTaskInstanceQuery()
                .processDefinitionKey(config.proceso())
                .finished()
                .list();

        Map<String, Long> conteoPorUsuario = new LinkedHashMap<>();
        for (String uid : personal.keySet()) {
            conteoPorUsuario.put(uid, 0L);
        }

        if (tareas != null) {
            for (HistoricTaskInstance t : tareas) {
                // Excluir tareas pendientes y tareas interrumpidas o canceladas
                if (t.getEndTime() == null) continue;
                if (t.getDeleteReason() != null && !t.getDeleteReason().isBlank()) continue;
                String assignee = t.getAssignee();
                if (assignee == null || assignee.isBlank()) continue;
                String uid = assignee.trim();

                personal.computeIfAbsent(uid, id -> new InfoPersonal(id, id, clasificarRolPorId(id)));
                conteoPorUsuario.put(uid, conteoPorUsuario.getOrDefault(uid, 0L) + 1L);
            }
        }

        List<FilaDistribucionTarea> filas = new ArrayList<>();
        for (var entry : personal.entrySet()) {
            InfoPersonal p = entry.getValue();
            long cant = conteoPorUsuario.getOrDefault(p.usuarioId(), 0L);
            filas.add(new FilaDistribucionTarea(p.usuarioId(), p.nombre(), p.rol(), cant));
        }
        return filas;
    }

    private record InfoPersonal(String usuarioId, String nombre, String rol) {}

    private Map<String, InfoPersonal> cargarPersonal(IdentityService identity) {
        Map<String, InfoPersonal> mapa = new LinkedHashMap<>();
        if (identity != null) {
            agregarMiembrosGrupo(identity, config.grupo(), "Administración", mapa);
            agregarMiembrosGrupo(identity, GRUPO_DOCENTES_DEFAULT, "Docentes", mapa);
        }
        if (mapa.isEmpty()) {
            // Respaldo con los 5 integrantes definidos en la consigna si IDM aún no fue consultado
            mapa.put("Jefe-Administrativo", new InfoPersonal("Jefe-Administrativo", "Jefe Administrativo", "Administración"));
            mapa.put("empleado-administrativo", new InfoPersonal("empleado-administrativo", "Empleado Administrativo", "Administración"));
            mapa.put("Jefe-Docente", new InfoPersonal("Jefe-Docente", "Jefe Docente", "Docentes"));
            mapa.put("docente-1", new InfoPersonal("docente-1", "Docente 1", "Docentes"));
            mapa.put("docente-2", new InfoPersonal("docente-2", "Docente 2", "Docentes"));
        }
        return mapa;
    }

    private void agregarMiembrosGrupo(IdentityService identity, String grupoId, String etiquetaRol, Map<String, InfoPersonal> destino) {
        List<User> usuarios = identity.createUserQuery().memberOfGroup(grupoId).list();
        if (usuarios == null || usuarios.isEmpty()) return;
        List<User> ordenados = new ArrayList<>(usuarios);
        ordenados.sort(Comparator.comparing(User::getId));
        for (User u : ordenados) {
            if (u.getId() == null || u.getId().isBlank()) continue;
            String id = u.getId().trim();
            String nombre = ((u.getFirstName() == null ? "" : u.getFirstName()) + " "
                    + (u.getLastName() == null ? "" : u.getLastName())).trim();
            destino.putIfAbsent(id, new InfoPersonal(id, nombre.isBlank() ? id : nombre, etiquetaRol));
        }
    }

    private static String clasificarRolPorId(String id) {
        String lower = id.toLowerCase(Locale.ROOT);
        if (lower.contains("docente")) return "Docentes";
        if (lower.contains("admin") || lower.contains("empleado")) return "Administración";
        return "Personal";
    }

    public static String formatearDuracion(long millis, long cursosConsiderados) {
        if (cursosConsiderados <= 0) {
            return "Sin datos (0 cursos)";
        }
        if (millis < 1000L) {
            return millis + " ms";
        }
        long totalSegundos = Math.round(millis / 1000.0);
        long dias = totalSegundos / 86400;
        long horas = (totalSegundos % 86400) / 3600;
        long minutos = (totalSegundos % 3600) / 60;
        long segundos = totalSegundos % 60;

        List<String> partes = new ArrayList<>();
        if (dias > 0) partes.add(dias + "d");
        if (horas > 0 || dias > 0) partes.add(String.format(Locale.ROOT, "%02dh", horas));
        if (minutos > 0 || horas > 0 || dias > 0) partes.add(String.format(Locale.ROOT, "%02dm", minutos));
        partes.add(String.format(Locale.ROOT, "%02ds", segundos));
        return String.join(" ", partes);
    }

    private static boolean esTrue(Object o) {
        return Boolean.TRUE.equals(o) || "true".equalsIgnoreCase(texto(o));
    }

    private static boolean esFalse(Object o) {
        return Boolean.FALSE.equals(o) || "false".equalsIgnoreCase(texto(o));
    }

    private static String texto(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
