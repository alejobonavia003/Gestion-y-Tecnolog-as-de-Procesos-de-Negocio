package ar.edu.cursos.materiales;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.flowable.engine.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in: una prueba contra el PostgreSQL existente. Limpia solamente sus fixtures. */
@EnabledIfSystemProperty(named="materiales.integracion",matches="true")
class IntegracionTest {
    @Test void recorridoJavaRealConPermisosConcurrenciaCancelacionYScript() throws Exception {
        Configuracion base=Configuracion.cargar(null);
        String suf=UUID.randomUUID().toString().replace("-","").substring(0,12);
        String grupo="mat-test-"+suf, key="mat_test_"+suf;
        String a="mat-a-"+suf,b="mat-b-"+suf,d="mat-doc-"+suf;
        Configuracion c=new Configuracion(base.jdbcUrl(),base.usuario(),base.clave(),key,"preparar_materiales",grupo);
        try(Motor motor=new Motor(c)) {
            ProcessEngine e=motor.engine();var ids=e.getIdentityService();var ts=e.getTaskService();var rt=e.getRuntimeService();var repo=e.getRepositoryService();
            String deployment=null;
            try {
                var g=ids.newGroup(grupo);g.setName("Fixture materiales");ids.saveGroup(g);
                for(String id:List.of(a,b,d)){var u=ids.newUser(id);u.setFirstName(id);u.setPassword("fixture-solo-test");ids.saveUser(u);}
                ids.createMembership(a,grupo);ids.createMembership(b,grupo);
                var sa=new MaterialesService(e,c);var sb=new MaterialesService(e,c);
                assertThrows(ErrorAplicacion.class,()->sa.ingresar(a,"incorrecta".toCharArray()));
                assertThrows(ErrorAplicacion.class,()->sa.ingresar(d,"fixture-solo-test".toCharArray()));
                sa.ingresar(a,"fixture-solo-test".toCharArray());sb.ingresar(b,"fixture-solo-test".toCharArray());
                String xml="""
                    <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" targetNamespace="materiales-test">
                    <process id="%s" isExecutable="true">
                      <startEvent id="inicio"/><sequenceFlow id="s1" sourceRef="inicio" targetRef="fork"/>
                      <parallelGateway id="fork"/>
                      <sequenceFlow id="s2" sourceRef="fork" targetRef="preparar_materiales"/>
                      <sequenceFlow id="s3" sourceRef="fork" targetRef="otra_rama"/>
                      <userTask id="preparar_materiales" name="Preparar materiales" flowable:candidateGroups="%s"/>
                      <userTask id="otra_rama" name="Otra rama"/>
                      <sequenceFlow id="s4" sourceRef="preparar_materiales" targetRef="join"/>
                      <sequenceFlow id="s5" sourceRef="otra_rama" targetRef="join"/>
                      <parallelGateway id="join"/>
                      <sequenceFlow id="s6" sourceRef="join" targetRef="confirmar"/>
                      <scriptTask id="confirmar" scriptFormat="groovy"><script>execution.setVariable('confirmado', true)</script></scriptTask>
                      <sequenceFlow id="s7" sourceRef="confirmar" targetRef="siguiente"/>
                      <userTask id="siguiente" name="Continuación confirmada"/>
                      <sequenceFlow id="s8" sourceRef="siguiente" targetRef="fin"/><endEvent id="fin"/>
                    </process></definitions>
                    """.formatted(key,grupo);
                deployment=repo.createDeployment().name("TEST app-materiales "+suf).addString("materiales-test.bpmn20.xml",xml).deploy().getId();
                var pi=rt.startProcessInstanceByKey(key,Map.of("cursoId","fixture-"+suf,"tematica","Prueba Java","empresaNombre","Ficticia","descripcion","Materiales de prueba"));
                String task=ts.createTaskQuery().processInstanceId(pi.getId()).taskDefinitionKey(c.tarea()).singleResult().getId();
                assertEquals(task,sa.listar(true,0).getFirst().id());
                assertEquals(pi.getId(),sa.detalle(task).instancia());
                assertThrows(ErrorAplicacion.class,()->sa.completar(task));
                var pool=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);
                try {
                    List<Future<Boolean>> futures=new ArrayList<>();
                    for(var s:List.of(sa,sb))futures.add(pool.submit(()->{inicio.await();try{s.reclamar(task);return true;}catch(RuntimeException ex){return false;}}));
                    inicio.countDown();int ganadores=0;for(var f:futures)if(f.get(20,TimeUnit.SECONDS))ganadores++;
                    assertEquals(1,ganadores);
                } finally {pool.shutdownNow();}
                String quien=ts.createTaskQuery().taskId(task).singleResult().getAssignee();
                var ganador=quien.equals(a)?sa:sb;var otro=quien.equals(a)?sb:sa;
                assertThrows(ErrorAplicacion.class,()->otro.completar(task));
                String otra=ts.createTaskQuery().processInstanceId(pi.getId()).taskDefinitionKey("otra_rama").singleResult().getId();
                assertThrows(ErrorAplicacion.class,()->ganador.completar(otra));
                ts.complete(otra);ganador.completar(task);
                assertNull(ts.createTaskQuery().taskId(task).singleResult());
                assertEquals(true,rt.getVariable(pi.getId(),"confirmado"));
                assertEquals(1,ts.createTaskQuery().processInstanceId(pi.getId()).taskDefinitionKey("siguiente").count());
                var cancelada=rt.startProcessInstanceByKey(key);
                String cancelTask=ts.createTaskQuery().processInstanceId(cancelada.getId()).taskDefinitionKey(c.tarea()).singleResult().getId();
                sa.reclamar(cancelTask);rt.deleteProcessInstance(cancelada.getId(),"Fixture cancelación app-materiales");
                assertThrows(ErrorAplicacion.class,()->sa.completar(cancelTask));
                sa.salir();assertThrows(ErrorAplicacion.class,()->sa.listar(false,0));
                System.out.println("Integración OK: identidad Java, claim concurrente, permisos, cancelación y continuación Groovy con join.");
            } finally {
                if(deployment!=null)repo.deleteDeployment(deployment,true);
                for(String id:List.of(a,b))ids.deleteMembership(id,grupo);
                for(String id:List.of(a,b,d))ids.deleteUser(id);
                ids.deleteGroup(grupo);
            }
        }
    }
}
