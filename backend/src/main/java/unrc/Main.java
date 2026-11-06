package unrc;

import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;

public class Main {

    public static void main(String[] args) {

        ProcessEngine engine = ProcessEngineConfiguration
                .createStandaloneInMemProcessEngineConfiguration()
                .setJdbcDriver("org.h2.Driver")
                .setJdbcUrl("jdbc:h2:mem:cursos;DB_CLOSE_DELAY=-1")
                .setJdbcUsername("sa")
                .setJdbcPassword("")
                .setDatabaseSchemaUpdate(
                        ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE
                )
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();

        try {
            System.out.println("Motor Flowable iniciado correctamente");

            long cantidad = engine.getRepositoryService()
                    .createProcessDefinitionQuery()
                    .count();

            System.out.println("Procesos desplegados: " + cantidad);
        } finally {
            engine.close();
        }
    }
}
