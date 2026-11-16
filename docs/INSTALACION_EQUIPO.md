# Instalar la versión backend-alejo

## Código y configuración

Descargar la rama completa; no copiar archivos sueltos del backend:

```bash
git fetch origin
git switch backend-alejo
git pull --ff-only origin backend-alejo
```

En una instalación nueva, desde la raíz:

```bash
cp env.example .env
```

Editar `.env` antes de iniciar. No sobrescribir el de una instalación existente.

| Variable | Valor que debe configurar cada integrante |
| --- | --- |
| `POSTGRES_DB` | Base del motor; `flowable` en una instalación nueva. |
| `POSTGRES_USER`, `POSTGRES_PASSWORD` | Credenciales de PostgreSQL. Si ya existe el volumen, conservar las originales. |
| `INSTITUCION_DB_NAME` | `institucion`; Compose crea esta base si falta. |
| `FLOWABLE_REST_USUARIO`, `FLOWABLE_REST_CLAVE` | Cuenta con acceso a la API REST del Flowable local. La plantilla propone `admin` / `test`; verificar en cada instalación. |
| `FLOWABLE_PROCESO_CLAVE` | `process`, correspondiente al BPMN del repositorio. |
| `CURSOS_DATOS_DEMO` | `false`; usar `true` si se necesita la empresa demo. |

Para Docker no hace falta otro `.env` en `backend/` ni en `web/`: Compose pasa
las variables y configura las direcciones internas. La ejecución con Maven desde
`backend/` lee el mismo `.env` de la raíz y usa puertos locales por defecto.

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps -a
```

Esperar a que Flowable termine de arrancar. `institucion-db` con `Exited (0)` es normal.
No borrar volúmenes para actualizar: contienen los datos y los modelos publicados.

## Usuarios y modelos de Flowable

Con Flowable disponible y las credenciales técnicas configuradas:

```bash
bash motor-flowable/idm/crear-usuarios.sh
```

El script crea los usuarios y grupos faltantes; por defecto los usuarios nuevos
tienen clave `1234`. No cambia la clave de usuarios que ya existían.

En Flowable Modeler importar el BPMN actualizado de
`motor-flowable/modelos/organizacion-cursos.bpmn20.xml` y la tabla
`motor-flowable/modelos/reglaViabilidad.dmn`, incorporarlos a la aplicación y
publicarla. Ver `motor-flowable/modelos/README.md`: los ZIP/BAR anteriores no
incluyen todas las correcciones de etapa 6.

La tabla `reglaViabilidad` debe leer `cantCursos` (number) y devolver
`resultadoViabilidad` (boolean): `< 5` → `true`, `>= 5` → `false`.
Si el motor menciona `otrasInstanciasActivas`, todavía ejecuta una regla antigua.
Guardar en Modeler no basta: hay que publicar. Las instancias existentes conservan
la definición de proceso con la que se iniciaron.

## Comprobación y archivos para compartir

```bash
curl -i http://localhost:8081/api/v1/estado
curl -i http://localhost:8081/api/v1/diagnostico/flowable
```

La web está en http://localhost:5173 y Flowable en http://localhost:8080/flowable-ui/.
Importar en Postman `docs/postman/Etapa_6.postman_collection.json` y
`docs/postman/Local.postman_environment.json`; ajustar usuarios y claves y seguir
el orden de `docs/postman/README.md`. El diagnóstico comprueba acceso al motor,
pero la creación de solicitud comprueba además la ejecución de la regla DMN.

Compartir la rama y `env.example`, ambos disponibles en Git. No hace falta enviar
el `.env` personal: cada integrante lo crea con sus credenciales. Si se comparten
credenciales de un entorno común, hacerlo por un canal privado. No enviar cookies
de Postman. Los cambios hechos únicamente en Modeler no viajan con Git: exportar
los modelos actualizados y la app después de publicarlos para compartirlos.

La aprobación de pruebas Maven no sustituye las pruebas de Postman contra el
modelo publicado en la máquina de cada integrante.
