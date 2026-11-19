# App Java de preparación de materiales y consultas históricas

Aplicación Swing para **§4.2.1** y **§4.3** de la consigna:
- **Preparación de materiales (§4.2.1):** login de administrativos, bandejas Asignadas/Disponibles, detalle, reclamo y completado de `preparar_materiales`.
- **Análisis de las ejecuciones del proceso (§4.3):** consultas históricas sobre Flowable (`HistoryService`):
  - **4.3.1 Resultados de las solicitudes:** cantidad y porcentaje sobre el total de instancias terminadas (`Curso finalizado`, `Cancelación`, `Rechazo de la propuesta`, `Solicitud no viable`, `Cierre por falta de respuesta`).
  - **4.3.2 Análisis de la preparación paralela:** duración promedio de las ramas de *inscripciones*, *materiales* y *recursos* en cursos con preparación completada normalmente (excluyendo cancelaciones).
  - **4.3.3 Distribución de tareas:** cantidad de tareas de usuario completadas por cada administrativo y docente (excluyendo pendientes e interrumpidas/canceladas).

Usa **la API Java de Flowable 6.8.0** y PostgreSQL del motor compartido.

Desde la raíz del repositorio, con Java 21 y Maven:

```bash
mvn -f app-materiales/pom.xml compile
./app-materiales/target/app-materiales
```

Para ejecutar las **consultas históricas (§4.3) por consola** sin abrir la ventana gráfica:

```bash
./app-materiales/ejecutar.sh --analisis
```

*(También están disponibles dentro de la interfaz gráfica mediante el botón **«Análisis histórico (§4.3)»**).*

En Linux también podés abrir `target/Materiales.desktop` con doble clic (el
administrador de archivos puede pedir habilitar «Permitir ejecutar»).
Ambos lanzadores se generan automáticamente en `compile`, también cuando ejecutás
`package` o `verify`. Usan las clases compiladas y las dependencias locales de Maven;
requieren Java 21 y mantener el repositorio en su ubicación. Si lo movés, volvé a
compilar para actualizar el acceso directo. No son un instalador independiente.

Para generar además el JAR con dependencias: `mvn -f app-materiales/pom.xml clean verify`.

Lee el `.env` de la raíz (o `../.env` si se ejecuta desde esta carpeta).
También se puede iniciar con `./app-materiales/ejecutar.sh`.

- [Funcionamiento, configuración, uso y pruebas](../docs/MODELO_Y_INTEGRACION.md#app-de-materiales)
- [Plan y estado de implementación](PLAN_DE_IMPLEMENTACION.md)

El backend REST no es necesario para operar materiales. PostgreSQL, el esquema
existente, las identidades y el proceso publicado sí son necesarios. Flowable UI
mantiene el ejecutor de timers/jobs; la app no despliega ni modifica el modelo.
