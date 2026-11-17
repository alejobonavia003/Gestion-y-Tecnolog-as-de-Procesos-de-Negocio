package ar.edu.cursos.materiales;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/** Prueba enfocada de materiales con la definición REAL; no recorre todo el curso. */
@EnabledIfSystemProperty(named="materiales.integracion",matches="true")
class ProcesoPublicadoTest {
    @Test void materialesDelProcesoPublicadoSeCompletanPorJava() throws Exception {
        Configuracion c=Configuracion.cargar(null);
        String usuario="mat-real-"+UUID.randomUUID().toString().substring(0,8);
        String instancia=null;
        try(Motor motor=new Motor(c)) {
            var e=motor.engine();var ids=e.getIdentityService();var rt=e.getRuntimeService();var ts=e.getTaskService();
            try {
                var user=ids.newUser(usuario);user.setPassword("fixture-solo-test");ids.saveUser(user);ids.createMembership(usuario,c.grupo());
                var proceso=rt.startProcessInstanceByKey(c.proceso(),"test-materiales-"+usuario,Map.of(
                    "cursoId","fixture-"+usuario,"tematica","Prueba aislada de materiales", "empresaNombre","Empresa ficticia",
                    "descripcion","Verificación de la API Java", "cantCursos",0));
                instancia=proceso.getId();
                // Preparar el escenario sin alterar la definición ni otras instancias.
                rt.createChangeActivityStateBuilder().processInstanceId(instancia)
                    .moveActivityIdTo("registrar_elaborar_propuesta","preparar_curso").changeState();
                var servicio=new MaterialesService(e,c);servicio.ingresar(usuario,"fixture-solo-test".toCharArray());
                String pi=instancia;
                Material tarea=servicio.listar(true,0).stream().filter(t->pi.equals(t.instancia())).findFirst().orElseThrow();
                assertEquals("Prueba aislada de materiales",tarea.tematica());
                servicio.reclamar(tarea.id());servicio.completar(tarea.id());
                assertNull(ts.createTaskQuery().taskId(tarea.id()).singleResult());
                assertNotNull(rt.createProcessInstanceQuery().processInstanceId(instancia).singleResult(),"El proceso debe esperar las otras ramas");
                assertTrue(ts.createTaskQuery().processInstanceId(instancia).count()>0);
                System.out.println("Proceso publicado OK: materiales completados por Java; otras ramas siguen activas.");
            } finally {
                if(instancia!=null) {
                    if(rt.createProcessInstanceQuery().processInstanceId(instancia).count()>0)rt.deleteProcessInstance(instancia,"Fin de prueba app-materiales");
                    e.getHistoryService().deleteHistoricProcessInstance(instancia);
                }
                ids.deleteMembership(usuario,c.grupo());ids.deleteUser(usuario);
            }
        }
    }
}
