# Motor Flowable

Responsable: **Alejo**.

Espacio para la futura implementación del BPMN ejecutable, DMN, configuración de usuarios y grupos, delegates, listeners, mensajes, temporizadores y compensaciones. Este README no crea un módulo Maven ni un motor nuevo.

Los modelos actuales y la consigna permanecen en [contexto/](../contexto/). La prueba `unrc.Main` permanece en [backend/](../backend/) y no es el motor institucional compartido.

Dependencias: entorno existente de [Docker Compose](../docker-compose.yml), servicios institucionales de Lauty y [contrato de integración](../docs/ACUERDO_DE_INTEGRACION.md). Coordinar con Luka los IDs de tareas y el acceso por API Java al mismo motor.
