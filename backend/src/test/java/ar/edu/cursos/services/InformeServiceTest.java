package ar.edu.cursos.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import ar.edu.cursos.clients.FlowableClient;
import ar.edu.cursos.dto.SesionUsuario;
import ar.edu.cursos.exceptions.ApiException;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.model.Empresa;
import ar.edu.cursos.repositories.CursoRepository;

class InformeServiceTest {

    CursoRepository cursos = mock(CursoRepository.class);
    FlowableClient motor = mock(FlowableClient.class);
    InformeService servicio = new InformeService(cursos, motor);

    UUID cursoId = UUID.randomUUID();
    Curso curso = mock(Curso.class);
    SesionUsuario jefeAdmin = SesionUsuario.personal("Jefe-Administrativo", "Jefe", List.of("Equipo-Administrativo"), true);

    @BeforeEach
    void preparar() {
        Empresa empresa = mock(Empresa.class);
        when(empresa.getId()).thenReturn(10L);
        when(empresa.getNombre()).thenReturn("Empresa Demo");
        when(curso.getEmpresa()).thenReturn(empresa);
        when(curso.getTematica()).thenReturn("Docker");
        when(curso.getProcessInstanceId()).thenReturn("p1");
        when(cursos.findById(cursoId)).thenReturn(Optional.of(curso));
    }

    String codigo(Runnable accion) {
        return assertThrows(ApiException.class, accion::run).getCodigo();
    }

    @Test
    void pdf_conResumenDelProceso_generaPdfYLoGuarda() {
        when(motor.variableHistorica("p1", "informeResumen")).thenReturn(Optional.of("Se dictó completo."));
        when(motor.variableHistorica("p1", "docenteId")).thenReturn(Optional.of("docente-1"));

        byte[] pdf = servicio.pdf(cursoId, jefeAdmin);

        assertTrue(new String(pdf, 0, 5, StandardCharsets.US_ASCII).startsWith("%PDF-"));
        verify(curso).setInformeResumen("Se dictó completo.");
        verify(cursos).save(curso);
    }

    @Test
    void pdf_resumenYaGuardado_noConsultaElMotor() {
        when(curso.getInformeResumen()).thenReturn("Guardado antes.");

        servicio.pdf(cursoId, jefeAdmin);

        verify(motor, never()).variableHistorica("p1", "informeResumen");
    }

    @Test
    void pdf_sinInforme_da404() {
        when(motor.variableHistorica("p1", "informeResumen")).thenReturn(Optional.empty());

        assertEquals("NO_ENCONTRADO", codigo(() -> servicio.pdf(cursoId, jefeAdmin)));
    }

    @Test
    void pdf_empresaAjena_da404() {
        SesionUsuario otraEmpresa = SesionUsuario.empresa(20L, "Otra");

        assertEquals("NO_ENCONTRADO", codigo(() -> servicio.pdf(cursoId, otraEmpresa)));
    }

    @Test
    void pdf_empresaDuena_puedeVerlo() {
        when(curso.getInformeResumen()).thenReturn("Guardado antes.");

        assertNotNull(servicio.pdf(cursoId, SesionUsuario.empresa(10L, "Empresa Demo")));
    }

    @Test
    void pdf_personalQueNoEsJefe_da403() {
        SesionUsuario docente = SesionUsuario.personal("docente-1", "Docente", List.of("Equipo-Docente"), false);

        assertEquals("SIN_PERMISO", codigo(() -> servicio.pdf(cursoId, docente)));
    }
}
