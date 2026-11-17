# App Java de preparación de materiales

Aplicación Swing para §4.2.1 de la consigna: login de administrativos,
bandejas Asignadas/Disponibles, detalle, reclamo y completado de materiales.
Usa **la API Java de Flowable 6.8.0** y PostgreSQL del motor compartido.

Desde la raíz del repositorio, con Java 21 y Maven:

```bash
mvn -f app-materiales/pom.xml clean verify
java -jar app-materiales/target/app-materiales.jar
```

Lee el `.env` de la raíz (o `../.env` si se ejecuta desde esta carpeta).
También se puede iniciar con `./app-materiales/ejecutar.sh`.

- [Funcionamiento, configuración, uso y pruebas](../docs/APP_MATERIALES.md)
- [Plan y estado de implementación](PLAN_DE_IMPLEMENTACION.md)

El backend REST no es necesario para operar materiales. PostgreSQL, el esquema
existente, las identidades y el proceso publicado sí son necesarios. Flowable UI
mantiene el ejecutor de timers/jobs; la app no despliega ni modifica el modelo.
