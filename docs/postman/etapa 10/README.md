# Postman — Etapa 10 (simulador de la empresa y modelo BPMN)

| Archivo | Qué es |
|---|---|
| `Etapa_10.postman_collection.json` | Colección **Cursos GyTPN - Etapa 10**: 7 escenarios, uno por carpeta. |
| `Local_Etapa_10.postman_environment.json` | Entorno **Cursos - Local (etapa 10)**, igual al de la etapa 9. |

## Requisitos

1. Backend en 8081 y Flowable en 8080 con la app **publicada de nuevo** (la del 18/11:
   `motor-flowable/modelos/Organizacion de cursos.zip`; ver `motor-flowable/modelos/README.md`).
2. Usuarios creados (`motor-flowable/idm/crear-usuarios.sh`).
3. Importar los dos `.json`, elegir el entorno y completar `flowablePass` (*Current value*).
4. Pocas instancias activas: con 5 o más el curso es NO_VIABLE y los escenarios fallan en
   «Crear solicitud». Revisar con **00 - Utilidades** y borrar sobrantes con la carpeta 9
   de la colección de la etapa 9. Cada escenario borra su instancia al final.

Cada carpeta se corre con **Run folder** (elegir el entorno en el Runner). Son independientes entre sí.

## Escenarios

| Carpeta | Qué prueba | Requisito del modelo |
|---|---|---|
| 01 Aceptación y preparación | Versión vigente, `fechaCurso`, idempotencia por `comunicacionId` (200 repetida / 409), curso `EN_PREPARACION`, `habilitar_acceso_empresa` automático, nómina (receive task), `confeccionar_lista`, `evaluar_recursos`, aula reservada por script | mensajes `aceptacion`, `recibir_nomina` |
| 02 Rechazo | `resultadoProceso=RECHAZADO` y tarea humana `archivar_solicitud` | listener en `evento-rechazo` |
| 03 Modificaciones | `revisar_propuesta` → v2, v1 desactualizada (409), `recordatorios` vuelve a 0 | mensaje `modificaciones` |
| 04 Cancelación | Solo en preparación (409 antes), compensación y `CANCELADO` | evento `cancelacion` dentro de `preparar_curso` |
| 05 Falta de aulas | `simularSinAulas=true` → `BpmnError SIN_AULAS` → `notificarCierre` | boundary `SIN_AULAS` |
| 06 Falta de respuesta | Dispara el timer `P5D` a mano 3 veces: 2 recordatorios y cierre `CERRADO_SIN_RESPUESTA` | timer + contador `recordatorios` |
| 07 Errores generales | 404 y 400 sin curso en marcha | — |

> **Dispara el timer sin esperar 5 días:** `GET management/timer-jobs?processInstanceId=…` y
> `POST management/timer-jobs/{id}` con `{"action":"move"}`. Si tu versión de Flowable no
> acepta `move`, probar con `management/jobs/{id}` y `{"action":"execute"}`.

## Lo que esta etapa NO cubre

- **NO_VIABLE por 5 cursos** y **finalización completa** (informe, `archivar_expediente`, liberación
  de recursos): son de la etapa 11 y dependen del `FinProcesoListener`.
- **Asignación automática de `confeccionar_lista`** (`AsignarListaListener`): todavía no existe;
  la carpeta 01 solo verifica que la tarea se crea.
- **Estado final del curso en la base** para rechazo, sin respuesta y fin normal: lo escribirá
  `FinProcesoListener`. Por eso esas carpetas verifican el estado en Flowable y no en `/cursos/{id}`.

## Hallazgos al preparar la colección

1. **`/simulacion/**` no usa sesión a propósito:** simula los mails de la empresa, no al personal. Por eso la colección llama a esos endpoints sin cookie (solo usa login del personal para crear la solicitud, registrar la propuesta y trabajar las tareas).
2. La colección está escrita contra el código y el BPMN, **sin haberla corrido** contra un
   Flowable real: correr las 7 carpetas y ajustar lo que falle.
