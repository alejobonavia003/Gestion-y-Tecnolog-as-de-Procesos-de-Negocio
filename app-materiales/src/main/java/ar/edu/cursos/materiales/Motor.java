package ar.edu.cursos.materiales;

import org.flowable.engine.*;

public final class Motor implements AutoCloseable {
    /**
     * Zona horaria del contenedor de Flowable (por defecto UTC).
     * La base guarda las fechas como TIMESTAMP sin zona: cada motor escribe la hora de su propio reloj.
     * Si esta app corriera con la zona de la PC (Argentina, UTC-3), los fines de tarea que escribe
     * quedarían 3 horas antes que los inicios que escribe el contenedor, y las duraciones darían
     * negativas (la rama de materiales de §4.3.2 aparecía en 0 ms). Por eso el motor embebido usa
     * la misma zona que el contenedor. Se puede cambiar con la variable FLOWABLE_ZONA_HORARIA.
     */
    static final String ZONA_MOTOR = System.getenv().getOrDefault("FLOWABLE_ZONA_HORARIA", "UTC");

    private final ProcessEngine engine;
    public Motor(Configuracion c) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(ZONA_MOTOR));
        var configuracion = ProcessEngineConfiguration.createStandaloneProcessEngineConfiguration()
            .setEngineName("materiales-java")
            .setJdbcDriver("org.postgresql.Driver")
            .setJdbcUrl(c.jdbcUrl() + (c.jdbcUrl().contains("?")?"&":"?") + "connectTimeout=5&socketTimeout=20&tcpKeepAlive=true").setJdbcUsername(c.usuario()).setJdbcPassword(c.clave())
            .setJdbcMaxActiveConnections(4).setJdbcMaxIdleConnections(2)
            .setJdbcMaxWaitTime(10000)
            .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE)
            .setAsyncExecutorActivate(false);
        configuracion.addConfigurator(new org.flowable.dmn.engine.configurator.DmnEngineConfigurator());
        engine = configuracion.buildProcessEngine();
    }
    public ProcessEngine engine() { return engine; }
    @Override public void close() { engine.close(); }
}
