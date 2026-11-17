# Documentación compartida

Responsables: **Alejo, Lauty y Luka**; cada uno mantiene los acuerdos de su componente.
Este directorio no contiene credenciales ni reemplaza los materiales de [contexto/](../contexto/).

## Índice

<<<<<<< HEAD
| Documento | Para qué sirve |
|---|---|
| [BACKEND_PROGRESO_Y_ETAPAS.md](BACKEND_PROGRESO_Y_ETAPAS.md) | Qué está hecho, qué falta y cómo se cierra cada etapa del backend. **Empezar por acá.** |
| [BACKEND.md](BACKEND.md) | Guía técnica del backend: estructura, cómo correrlo y endpoints por etapa. |
| [ENDPOINTS_WEB.md](ENDPOINTS_WEB.md) | Contrato de cada endpoint que consume la web (request, response, errores, contexto por tarea). |
| [ACUERDO_DE_INTEGRACION.md](ACUERDO_DE_INTEGRACION.md) | Identificadores, variables, estados, permisos, persistencia y contratos entre componentes. |
| [Referencias del modelo BPMN para el equipo.md](Referencias%20del%20modelo%20BPMN%20para%20el%20equipo.md) | Guía del modelo publicado en Flowable. |
| [ETAPA_6_IMPLEMENTADA.md](ETAPA_6_IMPLEMENTADA.md) | Registro de la implementación de la etapa 6 (tareas). |
| [notas_y_tips_de_clases_grabadas_sobre_el_proyecto.md](notas_y_tips_de_clases_grabadas_sobre_el_proyecto.md) | Aclaraciones de las clases que no están en la consigna (jar de listeners, timers cortos, formularios por referencia, criterios de la defensa). |
| [postman/](postman/) | Colecciones de Postman por etapa (ver abajo). |

## Colecciones de Postman

| Carpeta | Contenido |
|---|---|
| [postman/etapa 6/](postman/etapa%206/) | Tareas: bandejas, detalle, reclamo y completado. |
| [postman/etapa 7/](postman/etapa%207/) | Propuestas, versiones y PDF. |
| [postman/etapa 8/](postman/etapa%208/) | Portal de empresa y actividades. |
| [postman/etapa 9/](postman/etapa%209/) | **Colección general** (`Cursos GyTPN - Backend`): sesiones, solicitudes, atajos de Flowable y la etapa 9 paso a paso. Es la que se sigue ampliando. |

Las colecciones usan la cookie de sesión del login (no Basic Auth) contra el backend,
y Basic Auth con el usuario técnico solo en los requests que van directo a Flowable.
=======
La [guía de la app Java de materiales](APP_MATERIALES.md) explica configuración, pantallas, compilación y pruebas mínimas.

El [acuerdo de integración](ACUERDO_DE_INTEGRACION.md) reúne identificadores, variables, estados, permisos, persistencia y contratos REST iniciales. Distingue lo inspeccionado de las convenciones propuestas y de las decisiones pendientes.

Dependencias: [consigna y modelos](../contexto/), [backend existente](../backend/) y revisión cruzada cuando cambien contratos. Este directorio no reemplaza los materiales de referencia ni contiene credenciales.

El [contrato del primer recorrido integrado](INTEGRACION_PRIMER_RECORRIDO.md) fija la referencia publicada y las preguntas para coordinar etapas 9–10 con web/BPMN. La [guía de almacenamiento PDF](ALMACENAMIENTO_PDF_DOCKER.md) explica permisos, persistencia y la prueba real de I2.
>>>>>>> app-materiales
