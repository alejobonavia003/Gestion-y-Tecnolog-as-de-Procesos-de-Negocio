# Simulador de comunicaciones REST (Java Swing)

Aplicación Swing para §4.2.2 de la consigna: permite autenticarse con un usuario del personal, listar las solicitudes registradas y simular las 5 comunicaciones de la empresa mediante la **API REST del backend** (`/api/v1/simulacion/solicitudes`):

- `ACEPTACION` (indicando versión de propuesta y fecha acordada del curso)
- `RECHAZO` (indicando versión de propuesta)
- `MODIFICACION` (indicando versión de propuesta y observaciones)
- `NOMINA` (enviando la lista de participantes)
- `CANCELACION` (durante la preparación del curso)

Solo habilita los botones de las acciones permitidas en el estado actual del curso en Flowable.

## Compilar y ejecutar

Desde la raíz del repositorio, con Java 21 y Maven:

```bash
mvn -f simulador-rest/pom.xml compile
./simulador-rest/target/simulador-rest
```

También se puede iniciar con `./simulador-rest/ejecutar.sh` o abriendo `simulador-rest/target/Simulador.desktop`.
