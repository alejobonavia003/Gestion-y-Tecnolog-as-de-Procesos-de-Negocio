# Plan de la app Java de materiales

Fecha: 17/11/2026. Rama de trabajo: `codex/app-materiales-java`, creada desde
`backend-lauty` en `228af43`. El plan original se conserva como referencia;
la implementación M0–M6 fue entregada el 17/11/2026. La rama actual es
`app-materiales` (renombrada durante el trabajo). Ver [guía de uso y pruebas](../docs/MODELO_Y_INTEGRACION.md#app-de-materiales).

## Ubicación en el proyecto

Corresponde a §4.2.1 de `contexto/ProyectoCursos_V3_168631696404.pdf`, a las responsabilidades
de §2 y a los escenarios 8 y 11 de §5. En `docs/BACKEND.md#avance-y-etapas`
figura en §7.1, fuera de las etapas del backend. Puede avanzar en paralelo a
las etapas 9–11. Etapas 6 y 7: validadas con Postman según el equipo; etapa 8:
implementada en la rama de origen. La integración web es una validación distinta.

La documentación de etapas menciona V3 y consultas históricas/PM4Py, pero el PDF
disponible en este checkout es V2. Esas extensiones quedan fuera de este primer
entregable y se revisarán con la consigna V3 antes de planificarlas como requisito.

Los dos desarrolladores son distintos de los cinco usuarios de negocio:
Administración tiene dos integrantes y Docencia tres, incluidos sus jefes.

## Resultado esperado y arquitectura

Aplicación gráfica de escritorio en Java con Swing y Maven independiente dentro
de `app-materiales/`. Swing evita sumar JavaFX para una interfaz de login,
bandeja y detalle. Java 21 es el objetivo inicial del proyecto; su compatibilidad
con Flowable 6.8.0, scripts y dependencias se verifica en M1.

```text
Ventanas Swing
    → servicios de sesión y materiales (permisos y validación)
    → API Java: IdentityService / TaskService / RuntimeService
    → contexto Flowable 6.8.0 en la app
    → PostgreSQL del motor compartido con Flowable UI
```

La app usa los mismos taskId y processInstanceId que UI/REST. El contexto local
del motor accede al esquema persistente existente; no despliega procesos, no crea
otra base y no hace SQL directo sobre ACT_* para operar tareas. La base
`institucion` no es la base del motor. La API REST del backend no interviene en
las operaciones de materiales.

Configuración propuesta: variables `FLOWABLE_JDBC_URL`, `FLOWABLE_JDBC_USER`,
`FLOWABLE_JDBC_PASSWORD`, clave de proceso y clave de tarea configurables.
Desde este host, PostgreSQL está publicado en `127.0.0.1:5433`; usar el nombre de
base real configurado para el motor, sin asumir credenciales ni mostrar secretos.
Proporcionar plantilla propia con valores ficticios; el archivo local no se versiona.

Usar comprobación de esquema sin creación/actualización (`databaseSchemaUpdate=false`)
y ejecutor asíncrono local desactivado. Los jobs/timers siguen bajo el ejecutor
existente del servidor. Esto no evita la continuación síncrona: `complete` puede
ejecutar gateways, scripts, delegates y listeners en la JVM de la app. M1 debe
identificar e incorporar los componentes necesarios antes de habilitar completado.

## Etapas propias

| Etapa | Trabajo | Condición para cerrarla |
|---|---|---|
| M0 — Contrato y base | Rama, alcance, estructura y requisitos de conexión; confirmar IDs publicados. | Plan disponible y configuración/identidades/modelo identificados. |
| M1 — Conexión real por Java | Maven, Flowable 6.8.0 y PostgreSQL; contexto persistente, esquema protegido y lifecycle. Verificar IDM real y componentes del proceso. | Consultar desde Java una definición y una tarea existente con los mismos IDs que UI/REST; comprobar cierre sin alterar esquema. |
| M2 — Identificación y permisos | Servicio de sesión local; validar credenciales con el proveedor de identidad real y pertenencia a `Equipo-Administrativo`; logout. | Ambos administrativos acceden; docente, usuario inexistente y contraseña errónea se rechazan; claves no se registran ni persisten. |
| M3 — Bandeja gráfica | Login y pestañas Asignadas/Disponibles, refresco y detalle; filtros por proceso y tarea de materiales; paginación. | Solo muestra materiales del usuario o candidatas libres de su grupo; muestra curso/temática si existen en variables, y referencias sin inventar datos. |
| M4 — Reclamo | Servicio con comprobación de candidato, tarea activa y sin asignado; `TaskService.claim`; refresco tras conflicto. | Dos administrativos disputan una tarea y solo uno la obtiene; otra tarea o usuario ajeno no pueden reclamarse. |
| M5 — Completado | Reconsultar tarea, verificar tipo/proceso/asignado y ejecutar `TaskService.complete`; confirmar y refrescar. | Solo el asignado completa; no se mandan variables arbitrarias; se comprueba continuación real y convergencia con las otras ramas. |
| M6 — Entrega y defensa | Empaquetado ejecutable, instrucciones, configuración y prueba cruzada de escenarios. | Ejecutable abre en otra máquina preparada; evidencia de login → listar → reclamar → completar, misma instancia en UI/REST, concurrencia y cancelación. |

No se consideran completadas estas etapas por haber escrito clases o mocks. M1
es la primera comprobación de viabilidad técnica; si IDM o classpath no coinciden,
se resuelve antes de construir el resto sobre esa suposición.

## Reglas y pruebas que acompañan las etapas

- ID esperado de materiales: `preparar_materiales`; confirmar contra la definición
  realmente publicada. Existen referencias antiguas a `Material`: no aceptar ambos
  indiscriminadamente ni seleccionar instancias por ser las más recientes.
- Consultar asignadas por usuario; disponibles por grupo candidato y sin asignado.
  Filtrar ambas por proceso y tarea. Antes de operar, volver a validar los IDs.
- La API de bajo nivel no proporciona por sí sola la autorización de negocio.
  Comprobar membresía/candidatura al reclamar y asignación al completar; limitar
  estas acciones a la sesión de la app. No usar `setAssignee` para evitar conflictos.
- Probar cancelación con la ventana abierta: informar tarea ya inexistente y
  refrescar. Probar caída de BD y respuesta incierta: consultar el estado antes
  de permitir un reintento; nunca anunciar éxito si el resultado es desconocido.
- Acceso a motor/BD fuera del hilo de eventos Swing, mediante trabajadores en
  segundo plano; deshabilitar botones mientras se opera y cerrar recursos al salir.
  Establecer y limpiar contexto de usuario en el hilo que ejecuta cada operación,
  si lo requiere el historial; no tratarlo como sustituto de autorización.
- Pruebas unitarias de permisos/filtrado y pruebas con PostgreSQL/Flowable de la
  versión real para claim concurrente, cancelación y avance al completar. Usar
  instancias ficticias identificadas; no borrar datos compartidos para preparar pruebas.

## Pantallas y organización propuesta

Login: usuario, contraseña, estado de conexión e ingreso.

Bandeja: usuario identificado, Asignadas/Disponibles, columnas curso, tarea y
fecha, botones Actualizar, Ver detalle, Reclamar, Completar y Cerrar sesión.
El botón Completar se habilita únicamente para una tarea propia; los servicios
repiten las comprobaciones aunque el botón esté oculto o deshabilitado.

Paquete raíz `ar.edu.cursos.materiales`, con subpaquetes `config`, `motor`,
`sesion`, `servicios`, `modelo` y `ui`. El POM es propio; no modificar el POM del
backend ni incluir Spring Boot solo para mostrar ventanas. Datos adicionales de
materiales no exigidos por la consigna no se agregan al contrato del proceso.

## Coordinación de las dos líneas de trabajo

Nuestro trabajo se concentra en `app-materiales/`. El amigo continúa el backend
en su rama. El historial previo de responsables en otros README no determina
esta distribución actual. No renumerar sus etapas ni modificar issues.

Antes de M1, acordar el modelo publicado y disponibilidad de identidades. Antes
de M5, coordinar cambios de listeners/delegates/DMN/scripts que deben estar también
en el classpath local. Compartir componentes cuando existan, sin copiar lógica
incompatible entre backend, app y contenedor. No agregar una frontera asíncrona
al BPMN para ocultar dependencias sin revisar su efecto transaccional.

Los cambios de BPMN, Docker e identidades se coordinan explícitamente. Mantener
commits por etapa, actualizar la rama con avances revisados del backend cuando
se necesiten y abrir PR al terminar un incremento verificable. La rama nace
desde `backend-lauty`, por lo que un futuro PR a main puede incluir también los
commits de etapa 8 hasta que esa rama de base se integre. No hacer rebase ni
force-push de trabajo compartido automáticamente.

## Referencias técnicas

- [API y servicios Flowable](https://www.flowable.com/open-source/docs/bpmn/ch04-API): TaskService, identidad y operaciones síncronas.
- [Configuración de Flowable](https://www.flowable.com/open-source/docs/bpmn/ch03-Configuration): contexto, JDBC y esquema.
- Estas páginas actuales pueden mostrar versiones nuevas: fijar dependencias en
  **6.8.0** y contrastar APIs y comportamiento con esa versión durante M1.

## Estado de implementación y evidencia — 17/11/2026

- [x] M0: IDs publicados confirmados: `process`, `preparar_materiales`, `Equipo-Administrativo`.
- [x] M1: JAR Java 21 / Flowable 6.8.0, mismo PostgreSQL, esquema sin migraciones,
  ejecutor local desactivado; diagnóstico lee definición/tarea reales.
- [x] M2: identidad real por Java, pertenencia administrativa, sesión y logout;
  pruebas de credencial incorrecta y usuario no administrativo.
- [x] M3: Swing con bandejas paginadas, detalle y consultas fuera del hilo gráfico;
  login, carga y logout comprobados en escritorio y revisados visualmente.
- [x] M4: claim con controles del servicio; prueba de dos administrativos concurrentes,
  un único ganador y bloqueo del completado por otra persona.
- [x] M5: complete sin variables arbitrarias, contexto de usuario limpiado; pruebas
  con Groovy/join, cancelación y materiales del proceso publicado v4.
- [x] M6: JAR ejecutable con dependencias, launcher y documentación en docs;
  6 pruebas rápidas + 2 integradas aprobadas (8 en total).
- [ ] Validación manual del ejecutable en otra máquina y demostración del equipo.

La prueba del proceso publicado crea una instancia ficticia y la posiciona en
preparación; no certifica negociación ni el curso completo. Sus fixtures se
eliminan al terminar. Las consultas históricas/PM4Py y los listeners del motor
continúan como entregables independientes de esta app de materiales.
