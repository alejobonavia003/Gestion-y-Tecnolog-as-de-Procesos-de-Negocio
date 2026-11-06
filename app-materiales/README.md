# Aplicación gráfica de materiales

Responsable: **Luka**.

Espacio para la aplicación intranet con interfaz gráfica que identifica al administrativo, lista, reclama y completa `Preparar materiales`. Debe usar la **API Java de Flowable** sobre la base del motor compartido; no sustituir esa integración por REST ni usar H2 en memoria.

Dependencias: identidades, tarea `Material`, configuración y componentes del motor coordinados con Alejo; [contrato de integración](../docs/ACUERDO_DE_INTEGRACION.md). La configuración embebida de acceso debe acordarse antes de implementar; esta carpeta no crea un segundo entorno Flowable ni un módulo Maven.
