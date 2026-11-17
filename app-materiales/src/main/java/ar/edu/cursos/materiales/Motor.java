package ar.edu.cursos.materiales;

import org.flowable.engine.*;

public final class Motor implements AutoCloseable {
    private final ProcessEngine engine;
    public Motor(Configuracion c) {
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
