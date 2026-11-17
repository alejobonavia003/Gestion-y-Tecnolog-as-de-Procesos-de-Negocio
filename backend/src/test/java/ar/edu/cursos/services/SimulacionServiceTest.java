package ar.edu.cursos.services;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.ComunicacionRequest;
import ar.edu.cursos.dto.ComunicacionResponse;
import ar.edu.cursos.dto.SolicitudSimulacionResponse;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.ComunicacionEmpresa;
import ar.edu.cursos.model.CuentaEmpresa;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.Empresa;
import ar.edu.cursos.model.EstadoCurso;
import ar.edu.cursos.model.Propuesta;
import ar.edu.cursos.repositories.ComunicacionEmpresaRepository;
import ar.edu.cursos.repositories.CuentaEmpresaRepository;
import ar.edu.cursos.repositories.CursoRepository;
import ar.edu.cursos.repositories.PropuestaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SimulacionServiceTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private CursoRepository cursos;
    private PropuestaRepository propuestas;
    private CuentaEmpresaRepository cuentas;
    private ComunicacionEmpresaRepository comunicaciones;
    private FlowableClient flowable;
    private SesionService sesiones;
    private SimulacionService service;

    @BeforeEach
    void setUp() {
        cursos = mock(CursoRepository.class);
        propuestas = mock(PropuestaRepository.class);
        cuentas = mock(CuentaEmpresaRepository.class);
        comunicaciones = mock(ComunicacionEmpresaRepository.class);
        flowable = mock(FlowableClient.class);
        sesiones = mock(SesionService.class);
        service = new SimulacionService(cursos, propuestas, cuentas, comunicaciones, flowable, sesiones, json);
    }

    @Test
    void aceptarPropuesta_habilitaCuentaEmpresaYActualizaCurso() {
        UUID cursoId = UUID.randomUUID();
        Empresa empresa = mock(Empresa.class);
        when(empresa.getId()).thenReturn(10L);
        when(empresa.getNombre()).thenReturn("Tech SA");
        when(empresa.getIdentificador()).thenReturn("30-12345678-9");

        Curso curso = mock(Curso.class);
        when(curso.getId()).thenReturn(cursoId);
        when(curso.getEmpresa()).thenReturn(empresa);
        when(curso.getProcessInstanceId()).thenReturn("proc-1");

        Propuesta p1 = new Propuesta();
        p1.setVersion(1);
        p1.setEstado(Propuesta.Estado.ENVIADA);
        p1.setPrograma("Programa");
        p1.setPresupuestoImporte(new BigDecimal("100000.00"));
        p1.setPresupuestoMoneda("ARS");

        when(cursos.findById(cursoId)).thenReturn(Optional.of(curso));
        when(comunicaciones.findByComunicacionId("com-1")).thenReturn(Optional.empty());
        when(propuestas.findFirstByCursoIdOrderByVersionDesc(cursoId)).thenReturn(Optional.of(p1));
        when(flowable.buscarEjecucionPorMensaje("proc-1", "aceptacion")).thenReturn(Optional.of("exec-1"));
        when(flowable.tareasDeInstancia("proc-1")).thenReturn(json.createObjectNode());
        when(cuentas.findByEmpresaId(10L)).thenReturn(Optional.empty());
        when(cuentas.findByUsuario("30-12345678-9")).thenReturn(Optional.empty());
        when(sesiones.hashear("1234")).thenReturn("hash");
        when(cuentas.save(any(CuentaEmpresa.class))).thenAnswer(inv -> inv.getArgument(0));

        ComunicacionRequest req = new ComunicacionRequest(
                "com-1",
                "ACEPTACION",
                1,
                OffsetDateTime.parse("2026-12-10T09:00:00-03:00"),
                null,
                null
        );

        ComunicacionResponse res = service.aplicarComunicacion(cursoId, req);

        assertTrue(res.aplicada());
        assertFalse(res.repetida());
        assertEquals("30-12345678-9", res.cuentaEmpresaUsuario());
        assertEquals(Propuesta.Estado.ACEPTADA, p1.getEstado());
        verify(flowable).enviarMensajeAEjecucion(eq("exec-1"), eq("aceptacion"), anyMap());
        verify(curso).setEstado(EstadoCurso.EN_PREPARACION);
        verify(comunicaciones).save(any(ComunicacionEmpresa.class));
    }

    @Test
    void modificacion_conVersionDesactualizada_lanza409() {
        UUID cursoId = UUID.randomUUID();
        Curso curso = mock(Curso.class);
        when(curso.getId()).thenReturn(cursoId);
        when(curso.getProcessInstanceId()).thenReturn("proc-1");

        Propuesta v2 = new Propuesta();
        v2.setVersion(2);
        v2.setEstado(Propuesta.Estado.ENVIADA);

        when(cursos.findById(cursoId)).thenReturn(Optional.of(curso));
        when(comunicaciones.findByComunicacionId("com-2")).thenReturn(Optional.empty());
        when(propuestas.findFirstByCursoIdOrderByVersionDesc(cursoId)).thenReturn(Optional.of(v2));

        ComunicacionRequest req = new ComunicacionRequest(
                "com-2",
                "MODIFICACION",
                1,
                null,
                "Bajar presupuesto",
                null
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.aplicarComunicacion(cursoId, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("VERSION_DESACTUALIZADA", ex.getCodigo());
    }

    @Test
    void enviarNomina_senalaEjecucionRecibirNomina() {
        UUID cursoId = UUID.randomUUID();
        Curso curso = mock(Curso.class);
        when(curso.getId()).thenReturn(cursoId);
        when(curso.getProcessInstanceId()).thenReturn("proc-1");

        when(cursos.findById(cursoId)).thenReturn(Optional.of(curso));
        when(comunicaciones.findByComunicacionId("com-3")).thenReturn(Optional.empty());
        when(flowable.ejecucionesDeInstancia("proc-1")).thenReturn(
                json.createObjectNode().set("data", json.createArrayNode().add(
                        json.createObjectNode().put("id", "exec-nom").put("activityId", "recibir_nomina")
                ))
        );

        ComunicacionRequest req = new ComunicacionRequest(
                "com-3",
                "NOMINA",
                null,
                null,
                null,
                List.of(new ComunicacionRequest.ParticipanteDto("Ana Pérez"))
        );

        ComunicacionResponse res = service.aplicarComunicacion(cursoId, req);

        assertTrue(res.aplicada());
        verify(flowable).senalarEjecucion(eq("exec-nom"), anyMap());
    }

    @Test
    void obtenerComunicacionesHabilitadas_enAcuerdo_incluyeAceptacionRechazoModificacion() {
        UUID cursoId = UUID.randomUUID();
        Empresa empresa = new Empresa();
        empresa.setNombre("Tech SA");

        Curso curso = mock(Curso.class);
        when(curso.getId()).thenReturn(cursoId);
        when(curso.getTematica()).thenReturn("Docker");
        when(curso.getEmpresa()).thenReturn(empresa);
        when(curso.getEstado()).thenReturn(EstadoCurso.EN_ACUERDO);
        when(curso.getProcessInstanceId()).thenReturn("proc-1");

        when(cursos.findById(cursoId)).thenReturn(Optional.of(curso));
        when(propuestas.findFirstByCursoIdOrderByVersionDesc(cursoId)).thenReturn(Optional.empty());
        when(flowable.buscarEjecucionPorMensaje("proc-1", "aceptacion")).thenReturn(Optional.of("exec-1"));
        when(flowable.ejecucionesDeInstancia("proc-1")).thenReturn(json.createObjectNode());
        when(flowable.buscarEjecucionPorMensaje("proc-1", "cancelacion")).thenReturn(Optional.empty());
        when(flowable.buscarEjecucionPorMensaje("proc-1", "cancelacionEmpresa")).thenReturn(Optional.empty());

        SolicitudSimulacionResponse res = service.obtenerComunicacionesHabilitadas(cursoId);

        assertEquals(List.of("ACEPTACION", "RECHAZO", "MODIFICACION"), res.accionesHabilitadas());
    }
}