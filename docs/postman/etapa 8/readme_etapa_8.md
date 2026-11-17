# Postman — Backend etapa 8

## Importar

En Postman, importar ambos archivos y seleccionar el environment **Cursos - Local etapa 8**:

- `Etapa_8.postman_collection.json`
- `Local_Etapa_8.postman_environment.json`

## Requisitos

Backend en puerto 8081 con **`CURSOS_DATOS_DEMO=true`** en `.env` (necesario para que exista `empresa-demo` / `1234`), PostgreSQL disponible y Flowable levantado.

## Orden recomendado

1. **01 — Preparación de cursos:** crea un curso para `empresa-demo` (`cursoId`) y otro para `otra-empresa-etapa8` (`cursoIdAjeno`).
2. **02 — Portal de empresa:** loguea con `empresa-demo`, lista sus cursos (`200`), verifica que no aparece el curso ajeno, prueba detalle propio (`200`), detalle ajeno (`404`), actividades fuera de preparación (`items: []`) y actividades de curso ajeno (`404`).
3. **03 — Actividades en preparación:** requiere avanzar la instancia en Flowable hasta `preparar_curso` (completar `registrar_elaborar_propuesta` y enviar mensaje `aceptacion`) y ejecutar en PostgreSQL:
   ```sql
   UPDATE curso SET estado = 'EN_PREPARACION' WHERE id = '<cursoId>';
   ```
   Luego ejecutar `01 Actividades en EN_PREPARACION` para ver las ramas activas (`TAREA_USUARIO` y `ESPERA_MENSAJE`).
4. **04 — Permisos:** verifica que el personal recibe `403` en `GET /cursos` pero `200` en `GET /cursos/{id}`, y que sin sesión devuelve `401`.
