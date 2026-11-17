# App Java de preparación de materiales

Aplicación Swing para §4.2.1 de la consigna: login de administrativos,
bandejas Asignadas/Disponibles, detalle, reclamo y completado de materiales.
Usa **la API Java de Flowable 6.8.0** y PostgreSQL del motor compartido.

Desde la raíz del repositorio, con Java 21 y Maven:

```bash
mvn -f app-materiales/pom.xml compile
./app-materiales/target/app-materiales
```

En Linux también podés abrir `target/Materiales.desktop` con doble clic (el
administrador de archivos puede pedir habilitar «Permitir ejecutar»).
Ambos lanzadores se generan automáticamente en `compile`, también cuando ejecutás
`package` o `verify`. Usan las clases compiladas y las dependencias locales de Maven;
requieren Java 21 y mantener el repositorio en su ubicación. Si lo movés, volvé a
compilar para actualizar el acceso directo. No son un instalador independiente.

Para generar además el JAR con dependencias: `mvn -f app-materiales/pom.xml clean verify`.

Lee el `.env` de la raíz (o `../.env` si se ejecuta desde esta carpeta).
También se puede iniciar con `./app-materiales/ejecutar.sh`.

- [Funcionamiento, configuración, uso y pruebas](../docs/APP_MATERIALES.md)
- [Plan y estado de implementación](PLAN_DE_IMPLEMENTACION.md)

El backend REST no es necesario para operar materiales. PostgreSQL, el esquema
existente, las identidades y el proceso publicado sí son necesarios. Flowable UI
mantiene el ejecutor de timers/jobs; la app no despliega ni modifica el modelo.
