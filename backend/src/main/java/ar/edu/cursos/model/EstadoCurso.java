package ar.edu.cursos.model;

/**
 * Estado del curso que se guarda en la BD institucional.
 * Los intermedios los calcula el backend a partir de Flowable (etapa 8);
 * los finales los escribe el Execution Listener de fin del proceso.
 */
public enum EstadoCurso {
    SOLICITADO,
    EN_ACUERDO,
    EN_PREPARACION,
    CONFIRMADO_PENDIENTE_INICIO,
    EN_DESARROLLO,
    EN_ELABORACION_INFORME,
    EN_CIERRE,
    CANCELACION_EN_CURSO,
    // Finales
    FINALIZADO,
    CANCELADO,
    RECHAZADO,
    CERRADO_SIN_RESPUESTA,
    NO_VIABLE
}
