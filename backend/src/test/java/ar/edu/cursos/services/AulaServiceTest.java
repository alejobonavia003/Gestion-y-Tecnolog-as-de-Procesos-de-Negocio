package ar.edu.cursos.services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ar.edu.cursos.model.Aula;
import ar.edu.cursos.model.Curso;
import ar.edu.cursos.repositories.AulaRepository;
import ar.edu.cursos.repositories.CursoRepository;

class AulaServiceTest {

    private final AulaRepository aulasRepo = mock(AulaRepository.class);
    private final CursoRepository cursosRepo = mock(CursoRepository.class);
    private final AulaService servicio = new AulaService(aulasRepo, cursosRepo);

    private Aula aulaConId(Long id, String codigo, String nombre) throws Exception {
        Aula a = new Aula(codigo, nombre, 30);
        Field f = Aula.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(a, id);
        return a;
    }

    @Test
    void reservaPrimeraLibreYCuandoEstanLasTresOcupadasDevuelveVacio() throws Exception {
        Aula a1 = aulaConId(1L, "AULA-101", "Aula 101");
        Aula a2 = aulaConId(2L, "AULA-102", "Aula 102");
        Aula a3 = aulaConId(3L, "AULA-103", "Aula 103");
        when(aulasRepo.findByActivaTrueOrderByCodigoAsc()).thenReturn(List.of(a1, a2, a3));

        // 1 y 2 ocupadas -> asigna AULA-103
        when(cursosRepo.findAulaIdsOcupadas(AulaService.ESTADOS_FINALES)).thenReturn(Set.of(1L, 2L));
        Curso c3 = new Curso();
        Optional<Aula> r3 = servicio.reservarPrimeraDisponible(c3);
        assertTrue(r3.isPresent());
        assertEquals("AULA-103", r3.get().getCodigo());
        assertEquals(a3, c3.getAula());
        verify(cursosRepo).save(c3);

        // Las 3 ocupadas -> devuelve empty y resumen indica 0 disponibles
        when(cursosRepo.findAulaIdsOcupadas(AulaService.ESTADOS_FINALES)).thenReturn(Set.of(1L, 2L, 3L));
        Curso c4 = new Curso();
        assertTrue(servicio.reservarPrimeraDisponible(c4).isEmpty());
        assertNull(c4.getAula());

        Map<String, Object> resumen = servicio.resumenDisponibilidad();
        assertEquals(3, resumen.get("total"));
        assertEquals(0, resumen.get("disponibles"));
        assertEquals(false, resumen.get("hayDisponibles"));
    }
}
