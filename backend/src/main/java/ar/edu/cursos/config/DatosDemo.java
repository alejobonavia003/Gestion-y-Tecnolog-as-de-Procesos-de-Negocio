package ar.edu.cursos.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.cursos.model.CuentaEmpresa;
import ar.edu.cursos.model.Empresa;
import ar.edu.cursos.repositories.CuentaEmpresaRepository;
import ar.edu.cursos.repositories.EmpresaRepository;
import ar.edu.cursos.services.SesionService;

/** Crea una empresa con cuenta para probar el portal. Solo con cursos.datos-demo=true. */
@Component
@ConditionalOnProperty(name = "cursos.datos-demo", havingValue = "true")
public class DatosDemo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);

    private final EmpresaRepository empresas;
    private final CuentaEmpresaRepository cuentas;
    private final SesionService sesiones;

    public DatosDemo(EmpresaRepository empresas, CuentaEmpresaRepository cuentas, SesionService sesiones) {
        this.empresas = empresas;
        this.cuentas = cuentas;
        this.sesiones = sesiones;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (cuentas.findByUsuario("empresa-demo").isPresent()) {
            return;
        }
        Empresa empresa = new Empresa();
        empresa.setNombre("Empresa Demo S.A.");
        empresa.setIdentificador("30-00000000-1");
        empresa.setContactoNombre("Contacto Demo");
        empresa.setContactoEmail("contacto@empresa-demo.test");
        empresas.save(empresa);

        CuentaEmpresa cuenta = new CuentaEmpresa();
        cuenta.setEmpresa(empresa);
        cuenta.setUsuario("empresa-demo");
        cuenta.setClaveHash(sesiones.hashear("1234"));
        cuentas.save(cuenta);

        log.info("Datos demo: creada la empresa 'empresa-demo' con clave '1234'");
    }
}
