package ar.edu.cursos.services;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.NuevaPropuestaRequest;
import ar.edu.cursos.dto.PropuestaCreadaResponse;
import ar.edu.cursos.dto.SesionUsuario;
import ar.edu.cursos.dto.TareaDetalle;
import ar.edu.cursos.dto.TareaResumen;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.Empresa;
import ar.edu.cursos.model.Propuesta;
import ar.edu.cursos.repositories.CursoRepository;
import ar.edu.cursos.repositories.PropuestaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PropuestaServiceTest {

    @TempDir
    Path tempDir;

    private PropuestaRepository propuestaRepository;
    private CursoRepository cursoRepository;
    private FlowableClient flowableClient;
    private TareaService tareaService;
    private PropuestaService service;

    @BeforeEach
    void setUp() {
        propuestaRepository = mock(PropuestaRepository.class);
        cursoRepository = mock(CursoRepository.class);
        flowableClient = mock(FlowableClient.class);
        tareaService = mock(TareaService.class);
        service = new PropuestaService(propuestaRepository, cursoRepository, flowableClient, tareaService, tempDir.toString());
    }

    private TareaDetalle crearTarea(String id, String clave, String asignado, UUID cursoId) {
        TareaResumen resumen = new TareaResumen(
                id, clave, "Propuesta", cursoId.toString(), "Curso", "Empresa", null, asignado, asignado == null
        );
        return new TareaDetalle(resumen, Map.of());
    }

@Test
    void registrarPropuestaInicial_creaVersion1GeneraPdfYCompletaTarea() throws Exception {
        UUID cursoId = UUID.randomUUID();
        SesionUsuario staff = SesionUsuario.personal("ana", "Ana", List.of("Equipo-Administrativo"), true);

        Empresa empresa = new Empresa();
        empresa.setNombre("Tech SA");

        Curso curso = new Curso();
        curso.setTematica("Docker y Kubernetes");
        curso.setEmpresa(empresa);

        when(tareaService.detalle("task-1", staff))
                .thenReturn(crearTarea("task-1", "registrar_elaborar_propuesta", "ana", cursoId));
        when(cursoRepository.findById(cursoId)).thenReturn(Optional.of(curso));
        when(propuestaRepository.findByCursoIdOrderByVersionAsc(cursoId)).thenReturn(List.of());
        when(propuestaRepository.saveAndFlush(any(Propuesta.class))).thenAnswer(inv -> inv.getArgument(0));

        NuevaPropuestaRequest req = new NuevaPropuestaRequest(
                "Módulo 1: Contenedores. Módulo 2: Orquestación.",
                new NuevaPropuestaRequest.PresupuestoDto("150000.00", "ARS"),
                List.of(OffsetDateTime.parse("2026-12-01T10:00:00-03:00")),
                null
        );

        PropuestaCreadaResponse res = service.registrarYCompletar("task-1", staff, req);

        assertEquals(1, res.propuestaVersion());
        assertTrue(res.pdfDisponible());

        Path pdfEsperado = tempDir.resolve(cursoId + "-v1.pdf");
        assertTrue(Files.exists(pdfEsperado));
        assertTrue(Files.size(pdfEsperado) > 500);

        verify(flowableClient).completar("task-1", Map.of(
                "propuestaVersion", 1,
                "recordatorios", 0
        ));
    }

    @Test
    void revisarPropuesta_creaVersion2SinPisarVersion1() {
        UUID cursoId = UUID.randomUUID();
        SesionUsuario staff = SesionUsuario.personal("ana", "Ana", List.of("Equipo-Administrativo"), true);

        Curso curso = new Curso();
        curso.setTematica("Java 21");

        Propuesta v1 = new Propuesta();
        v1.setVersion(1);
        v1.setEstado(Propuesta.Estado.MODIFICACIONES);
        v1.setPresupuestoImporte(new BigDecimal("100000.00"));
        v1.setPresupuestoMoneda("ARS");

        when(tareaService.detalle("task-2", staff))
                .thenReturn(crearTarea("task-2", "revisar_propuesta", "ana", cursoId));
        when(cursoRepository.findById(cursoId)).thenReturn(Optional.of(curso));
        when(propuestaRepository.findByCursoIdOrderByVersionAsc(cursoId)).thenReturn(List.of(v1));
        when(propuestaRepository.saveAndFlush(any(Propuesta.class))).thenAnswer(inv -> inv.getArgument(0));

        NuevaPropuestaRequest req = new NuevaPropuestaRequest(
                "Programa ajustado",
                new NuevaPropuestaRequest.PresupuestoDto("120000.00", "ARS"),
                List.of(OffsetDateTime.parse("2026-12-10T09:00:00-03:00")),
                1
        );

        PropuestaCreadaResponse res = service.registrarYCompletar("task-2", staff, req);

        assertEquals(2, res.propuestaVersion());
        assertTrue(Files.exists(tempDir.resolve(cursoId + "-v2.pdf")));
    }

    @Test
    void revisarPropuesta_conVersionDesactualizada_lanza409() {
        UUID cursoId = UUID.randomUUID();
        SesionUsuario staff = SesionUsuario.personal("ana", "Ana", List.of("Equipo-Administrativo"), true);

        Curso curso = new Curso();

        Propuesta v2 = new Propuesta();
        v2.setVersion(2);
        v2.setEstado(Propuesta.Estado.ENVIADA);

        when(tareaService.detalle("task-2", staff))
                .thenReturn(crearTarea("task-2", "revisar_propuesta", "ana", cursoId));
        when(cursoRepository.findById(cursoId)).thenReturn(Optional.of(curso));
        when(propuestaRepository.findByCursoIdOrderByVersionAsc(cursoId)).thenReturn(List.of(v2));

        NuevaPropuestaRequest req = new NuevaPropuestaRequest(
                "Programa",
                new NuevaPropuestaRequest.PresupuestoDto("1000.00", "USD"),
                List.of(OffsetDateTime.now()),
                1
        );

        ApiException ex = assertThrows(ApiException.class, () -> service.registrarYCompletar("task-2", staff, req));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("VERSION_DESACTUALIZADA", ex.getCodigo());
    }

    @Test
    void obtenerPdf_deOtraEmpresa_devuelve404() {
        UUID cursoId = UUID.randomUUID();
        SesionUsuario otraEmpresa = SesionUsuario.empresa(20L, "Otra Empresa");

        Empresa emp10 = mock(Empresa.class);
        when(emp10.getId()).thenReturn(10L);

        Curso curso = new Curso();
        curso.setEmpresa(emp10);

        when(cursoRepository.findById(cursoId)).thenReturn(Optional.of(curso));

        ApiException ex = assertThrows(ApiException.class, () -> service.obtenerPdf(cursoId, 1, otraEmpresa));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}