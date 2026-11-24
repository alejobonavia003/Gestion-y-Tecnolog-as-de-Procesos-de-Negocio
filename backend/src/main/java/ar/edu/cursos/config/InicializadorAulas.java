package ar.edu.cursos.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.cursos.model.Aula;
import ar.edu.cursos.repositories.AulaRepository;

/** Carga las 3 aulas físicas de la institución si la tabla está vacía. */
@Component
public class InicializadorAulas implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorAulas.class);

    private final AulaRepository aulas;

    public InicializadorAulas(AulaRepository aulas) {
        this.aulas = aulas;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (aulas.count() > 0) {
            return;
        }
        aulas.saveAll(List.of(
                new Aula("AULA-101", "Aula 101", 30),
                new Aula("AULA-102", "Aula 102", 30),
                new Aula("AULA-103", "Aula 103", 30)
        ));
        log.info("Aulas iniciales creadas: AULA-101, AULA-102, AULA-103");
    }
}
