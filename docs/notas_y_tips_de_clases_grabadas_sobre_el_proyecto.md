# Puntos Clave de las Clases Grabadas (No explícitos en el PDF)

Este documento recopila las aclaraciones técnicas, atajos prácticos, advertencias de errores comunes y criterios de evaluación que el docente mencionó en los audios de clase respecto al proyecto en **Flowable**, y que no figuran textualmente en la consigna (`ProyectoCursos_V2.pdf`).

---

## 1. Despliegue de Código Java en Docker (Flowable)
En el PDF se pide implementar *Listeners* y tareas automáticas, pero en clase se explicó el procedimiento exacto para que el contenedor de Flowable reconozca nuestras clases compiladas en ejecución:

1. **Empaquetado:** Compilar las clases Java (Task Listeners, Execution Listeners, JavaDelegates) en un archivo `.jar` (ej. `misclases.jar`).
2. **Copia al contenedor:** Copiar el `.jar` dentro de la carpeta `WEB-INF/lib/` de la aplicación desplegada en Tomcat dentro del contenedor Docker:
   ```bash
   docker cp /ruta/local/misclases.jar flowable-persistente:/app/WEB-INF/lib/
   ```
3. **Reinicio obligatorio:** Tomcat no carga nuevos `.jar` en caliente en esa ruta, por lo que es indispensable reiniciar el contenedor:
   ```bash
   docker restart flowable-persistente
   ```
4. **Coincidencia de nombres:** Los nombres de paquetes y clases configurados en el modelador BPMN deben coincidir **exactamente** con la estructura dentro del `.jar` (si no coincide, lanzará `ClassNotFoundException` en tiempo de ejecución).
5. **Logs en tiempo real:** Para depurar qué hace el motor o si falló algún *listener* al ejecutar desde la UI/REST, usar:
   ```bash
   docker logs -f --tail 100 flowable-persistente
   ```
6. **Si se usa Drools (`.drl`):** Si en lugar de DMN se opta por reglas en Drools, todos los `.jar` de dependencias de Drools también deben copiarse dentro de `/app/WEB-INF/lib/` y reiniciar el contenedor.

---

## 2. Simplificaciones y Atajos Permitidos para el Proyecto
Para evitar que los grupos se traben con configuraciones externas irrelevantes para la materia, el profesor autorizó las siguientes simplificaciones:

* **Envío de correos electrónicos (Mail Tasks):** No es obligatorio configurar un servidor SMTP real (como Gmail). Si el modelador o el motor exige una implementación para las tareas de notificación/envío de correo a la empresa, se pueden reemplazar por una *Service Task* (JavaDelegate) o *Script Task* que imprima un mensaje en consola (`System.out.println("Simulando envío de mail...")`).
* **Temporizadores (Timers) para la defensa:** Mantener los eventos temporizadores en el BPMN con la lógica pedida (recordatorios cada 5 días, espera hasta fecha de curso), pero **configurarlos con duraciones cortas (ej. 1 o 5 segundos / ISO 8601 `PT5S`)** durante las pruebas y la demostración para poder mostrar los escenarios de vencimiento en vivo sin esperar días.
* **Aplicación Móvil:** Se descartó el desarrollo de una app móvil nativa; las acciones correspondientes a docentes o participantes se integran en la aplicación web o local según corresponda.
* **Interfaz de usuario (Frontend):** El docente recalcó repetidamente **no perder tiempo en diseño estético** ("que sea linda, CSS complejo, etc."). Se evalúa la funcionalidad, la correcta invocación a Flowable y el cumplimiento de los escenarios, no el diseño gráfico.
* **Uso de Postman para disparar eventos:** Durante el desarrollo, antes de tener terminada la interfaz externa o el simulador, se recomienda usar Postman (con *Basic Auth*) para disparar mensajes/señales REST y hacer avanzar instancias detenidas.

---

## 3. Detalles de Implementación y Modelado BPMN
* **Formularios en Flowable UI:** Si se prueban formularios desde la interfaz web propia de Flowable (`flowable-ui`), **no usar formularios embebidos (*form properties*)** porque la UI de esta versión no los renderiza; deben crearse como formularios independientes y vincularse por **referencia** (*Form reference*). Además, cada `id` de campo del formulario se convierte automáticamente en una variable de proceso.
* **Orden en la sincronización por Señales (*Signal*):** Si se utilizan eventos de señal para sincronizar ramas paralelas (en lugar de una compuerta paralela `AND` de cierre), el flujo que espera la señal debe llegar al evento de captura **antes** de que la otra rama dispare la señal; de lo contrario, el evento se pierde porque no queda en estado de escucha.
* **Compensaciones:**
  * Solo se ejecutan si la actividad compensable ya fue completada previamente.
  * Si se usa un lanzador de compensación sin especificar actividad (*broadcast*), compensa todas las actividades completadas dentro del ámbito; si se necesita compensar en un orden estricto, deben usarse lanzadores específicos apuntando a cada actividad en secuencia.
  * Ante una cancelación durante la preparación del curso, asegurarse de que el flujo interrumpido **no continúe hacia la confirmación/ejecución del curso**, sino que derive exclusivamente a la compensación y cierre como cancelado.
* **Errores conceptuales comunes a evitar en el BPMN:**
  * Nunca modelar "Esperar fecha del curso" o "Esperar respuesta" como una **User Task** (tarea humana); las esperas pasivas se modelan con **Eventos Intermedios** (Timer o Message Catch).
  * No crear tareas genéricas llamadas "Gestionar inscripciones" cuando ese nombre engloba todo un subproceso compuesto por actividades concretas (recibir nómina, confeccionar lista).
  * No usar envío/recepción de **Mensajes** entre roles internos del mismo *Pool* (participante); los mensajes son únicamente para comunicación entre participantes externos (ej. Empresa $\leftrightarrow$ Institución). Internamente se usan flujos de secuencia, variables o señales.
  * No dejar eventos intermedios de envío sin flujo de secuencia saliente (el token quedaría bloqueado).

---

## 4. Base de Datos e Integración API Java vs. API REST
* **Persistencia crítica:** El motor Flowable es sin estado (*stateless*); todo el estado de las instancias, variables e historial reside en PostgreSQL.
* **Conexión directa en la App de Materiales:** La aplicación local que utiliza la API Java (`ProcessEngine`, `TaskService`, `IdentityService`) **no debe usar la base en memoria H2 por defecto**, sino apuntar exactamente a la misma instancia de PostgreSQL (`flowable-postgres`) que usa el contenedor Docker, para compartir las instancias en curso.
* **Permisos REST:** Todo usuario creado en Flowable IDM que deba interactuar mediante peticiones HTTP (como el usuario técnico del backend o los usuarios administrativos) debe tener asignado explícitamente el privilegio **`access-rest-api`**.
* **Variables desde el inicio:** Al iniciar una instancia desde el formulario web mediante la API REST (`/runtime/process-instances`), enviar en el cuerpo JSON el arreglo `variables` con los datos iniciales de la solicitud para que viajen durante todo el ciclo de vida del proceso.
* **Historial y trazas:** Para las consultas históricas y la extracción de logs hacia PM4Py, tener en cuenta que la API de historial (`HistoryService` / `/history/historic-activity-instances`) devuelve todos los elementos ejecutados (incluyendo compuertas y flujos de secuencia); se debe filtrar por tipo de actividad (`activityType == "userTask"`, `"serviceTask"`, etc.) y asegurarse de que **todos los elementos del BPMN tengan `ID` y `Name` descriptivos**.

---

## 5. Uso de Inteligencia Artificial y Criterios de Evaluación (Defensa)
* **Uso permitido pero supervisado:** El docente alienta el uso de IA (Cursor, ChatGPT, Claude) para acelerar el desarrollo de SQL, frontend y llamadas REST, pero advirtió sobre dos fallos frecuentes:
  1. **Eliminación silenciosa de código:** Al pedir modificaciones sobre archivos grandes, los asistentes suelen borrar funciones previas que ya andaban.
  2. **Complacencia (*Sycophancy*):** La IA suele dar la razón ante premisas incorrectas del usuario sobre cómo funciona Flowable.
* **Criterio de aprobación y nota:**
  * No es excluyente tener el 100% de las funcionalidades secundarias terminadas para aprobar; la nota es proporcional al alcance funcional logrado y a la correcta ejecución de los escenarios.
  * **Defensa oral:** Dado que se asume el uso de IA para generar código, la evaluación se centrará en **preguntas técnicas sobre el código y el modelo BPMN** durante la demostración para comprobar que el grupo comprende cómo funciona la solución entregada.