package ar.edu.cursos.services;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.ActividadesCursoResponse;
import ar.edu.cursos.dto.CursoDetalleResponse;
import ar.edu.cursos.dto.SesionUsuario;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.Empresa;
import ar.edu.cursos.model.EstadoCurso;
import ar.edu.cursos.repositories.CursoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CursoServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final CursoRepository cursoRepository = mock(CursoRepository.class);
    private final FlowableClient flowableClient = mock(FlowableClient.class);
    private final CursoService service = new CursoService(cursoRepository, flowableClient);

    @Test
    void listarDeEmpresa_devuelveSoloCursosDeLaSesion() {
        SesionUsuario empresa = SesionUsuario.empresa(10L, "Empresa Demo");
        Curso curso = mock(Curso.class);
        UUID cursoId = UUID.randomUUID();
        when(curso.getId()).thenReturn(cursoId);
        when(curso.getTematica()).thenReturn("Docker");
        when(curso.getEstado()).thenReturn(EstadoCurso.EN_PREPARACION);

        when(cursoRepository.findByEmpresaIdOrderByCreadoEnDesc(10L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(curso)));

        Map<String, Object> res = service.listarDeEmpresa(empresa, 0, 20);

        assertEquals(1L, res.get("total"));
        assertEquals(0, res.get("page"));
        assertEquals(20, res.get("size"));
        assertEquals(1, ((List<?>) res.get("items")).size());
    }

    @Test
    void detalle_cursoDeOtraEmpresa_devuelve404() {
        UUID cursoId = UUID.randomUUID();
        SesionUsuario otraEmpresa = SesionUsuario.empresa(20L, "Otra Empresa");

        Empresa emp10 = mock(Empresa.class);
        when(emp10.getId()).thenReturn(10L);

        Curso curso = new Curso();
        curso.setEmpresa(emp10);

        when(cursoRepository.findById(cursoId)).thenReturn(Optional.of(curso));

        ApiException ex = assertThrows(ApiException.class, () -> service.detalle(cursoId, otraEmpresa));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    @Test
    void actividades_enPreparacion_devuelveTareasYEsperaDeNomina() throws Exception {
        UUID cursoId = UUID.randomUUID();
        SesionUsuario empresa = SesionUsuario.empresa(10L, "Empresa Demo");

        Empresa emp10 = mock(Empresa.class);
        when(emp10.getId()).thenReturn(10L);

        Curso curso = new Curso();
        curso.setEmpresa(emp10);
        curso.setEstado(EstadoCurso.EN_PREPARACION);
        curso.setProcessInstanceId("proc-1");

        when(cursoRepository.findById(cursoId)).thenReturn(Optional.of(curso));
        when(flowableClient.tareasDeInstancia("proc-1")).thenReturn(
                json.readTree("{\"data\":[{\"taskDefinitionKey\":\"preparar_materiales\",\"name\":\"Preparar materiales\"}]}")
        );
        when(flowableClient.ejecucionesDeInstancia("proc-1")).thenReturn(
                json.readTree("{\"data\":[{\"activityId\":\"recibir_nomina\"}]}")
        );

        ActividadesCursoResponse res = service.actividades(cursoId, empresa);

        assertEquals("EN_PREPARACION", res.estado());
        assertEquals(2, res.items().size());
        assertEquals("TAREA_USUARIO", res.items().get(0).tipo());
        assertEquals("ESPERA_MENSAJE", res.items().get(1).tipo());
    }

    @Test
    void actividades_fueraDePreparacion_devuelveListaVacia() {
        UUID cursoId = UUID.randomUUID();
        SesionUsuario personal = SesionUsuario.personal("Jefe-Administrativo", "Marcos", List.of("Equipo-Administrativo"), true);

        Curso curso = new Curso();
        curso.setEstado(EstadoCurso.EN_ACUERDO);
        curso.setProcessInstanceId("proc-1");

        when(cursoRepository.findById(cursoId)).thenReturn(Optional.of(curso));

        ActividadesCursoResponse res = service.actividades(cursoId, personal);

        assertEquals("EN_ACUERDO", res.estado());
        assertTrue(res.items().isEmpty());
        verifyNoInteractions(flowableClient);
    }

    @Test
    void detalle_empresaDuena_devuelveDatosCompletos() {
        UUID cursoId = UUID.randomUUID();
        SesionUsuario empresa = SesionUsuario.empresa(10L, "Empresa Demo");

        Empresa emp10 = mock(Empresa.class);
        when(emp10.getId()).thenReturn(10L);

        Curso curso = mock(Curso.class);
        when(curso.getId()).thenReturn(cursoId);
        when(curso.getEmpresa()).thenReturn(emp10);
        when(curso.getTematica()).thenReturn("Java 21");
        when(curso.getEstado()).thenReturn(EstadoCurso.EN_PREPARACION);
        when(curso.getDescripcion()).thenReturn("Curso práctico");
        when(curso.getCantidadParticipantes()).thenReturn(12);

        when(cursoRepository.findById(cursoId)).thenReturn(Optional.of(curso));

        CursoDetalleResponse res = service.detalle(cursoId, empresa);

        assertEquals("Java 21", res.nombre());
        assertEquals("En preparación", res.estadoEtiqueta());
        assertEquals(12, res.cantidadParticipantes());
    }
}