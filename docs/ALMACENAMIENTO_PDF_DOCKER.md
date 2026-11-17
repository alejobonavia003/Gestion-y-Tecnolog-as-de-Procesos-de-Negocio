# PDFs de propuestas en Docker

Cambio I2 del 17/11/2026. El backend sigue ejecutándose como UID/GID `10001`.
La imagen crea `/app/almacenamiento/propuestas` con dueño `10001:10001` y permisos
`750`. Compose monta el volumen **`backend_archivos`** en `/app/almacenamiento`,
y configura `CURSOS_PROPUESTAS_DIR=/app/almacenamiento/propuestas`.

El nombre real del volumen lleva el prefijo del proyecto Compose. La base guarda
la ruta del archivo, no los bytes; la API conserva las rutas de descarga y valida
sesión y acceso al curso. No se exponen directorios como contenido público Nginx.
Los archivos conservan el formato `<cursoId>-v<version>.pdf`.

## Aplicar

Desde la raíz, conservando el `.env` existente:

```bash
docker compose config --quiet
docker compose up -d --build backend
```

En un volumen nuevo Docker copia los permisos del directorio preparado en la
imagen. Reiniciar, reconstruir o recrear el backend conserva los PDFs mientras
se use el mismo volumen/proyecto Compose. `docker compose down` también conserva
los volúmenes; **`down -v` los elimina**, incluidas las bases del proyecto.

La ejecución local con Maven mantiene `almacenamiento/propuestas` relativo al
directorio donde se ejecuta el backend, salvo que se defina `CURSOS_PROPUESTAS_DIR`.
No se cambiaron rutas persistidas anteriormente: Docker mantiene `/app/almacenamiento`.
Si había PDFs en el host por ejecución Maven, no se copian automáticamente al
volumen; antes de mezclar entornos hay que copiar esos archivos y conciliar sus
rutas en BD conservando un respaldo. En la comprobación inicial de este entorno
no existía el directorio de PDFs dentro del contenedor anterior.

## Comprobación mínima

```bash
docker compose exec backend sh -c 'test -w /app/almacenamiento/propuestas && echo PDF_DIRECTORIO_ESCRIBIBLE'
```

Luego, usando la colección Postman de etapa 7 o la API:

1. Login del jefe administrativo y creación de una solicitud ficticia.
2. Crear propuesta con `POST /api/v1/tareas/{taskId}/propuesta`: esperar `201`.
3. Descargar `GET /api/v1/cursos/{cursoId}/propuestas/1/pdf`: esperar `200`,
   `Content-Type: application/pdf` y comienzo `%PDF-`. Guardar una copia.
4. Recrear solamente el backend:

   ```bash
   docker compose up -d --no-deps --force-recreate backend
   ```

5. Esperar que responda `/api/v1/estado` y volver a iniciar sesión, porque las
   sesiones HTTP actuales viven en memoria. Descargar el mismo PDF y comparar
   el contenido o su SHA-256 con la copia anterior.

No hace falta agregar tests unitarios que comprueben líneas del Dockerfile: esta
verificación real comprueba permisos, generación, BD, proxy y persistencia.

## Operación

Para ubicar el volumen sin imprimir credenciales:

```bash
docker inspect "$(docker compose ps -q backend)" --format '{{range .Mounts}}{{println .Name "->" .Destination}}{{end}}'
```

Respaldar **base institucional y volumen de archivos juntos**. Restaurar solo la
BD puede dejar referencias a PDFs ausentes; restaurar solo archivos no recupera
las propuestas. Cambiar el nombre del proyecto Compose utiliza otro volumen.
No se incorporó una migración automática desde directorios locales ni se cambia
la propiedad de volúmenes previos con permisos diferentes de forma silenciosa.

Si un volumen existente tiene permisos incorrectos, verificar sus propietarios
y ajustar solamente `/app/almacenamiento` a `10001:10001` con acceso `750` para
los directorios, conservando los archivos. No usar `chmod 777` ni ejecutar el
backend permanentemente como root.

## Resultado verificado

Prueba real del 17/11/2026, usando la API a través del proxy web:

- Usuario del backend `10001:10001`, directorio escribible y volumen montado.
- Alta ficticia y propuesta: `201`; descarga: `200 application/pdf`, cabecera `%PDF-`.
- PDF de **1519 bytes**, SHA-256
  `5558fd3a207b9d50ff3184391e45b29db916bf17ac49a2549563a50dc6284cca`.
- Backend recreado con `--force-recreate`, login nuevo y segunda descarga `200`,
  con exactamente el mismo SHA-256 y tamaño.
- La reconstrucción ejecutó los **20 tests existentes del backend: 0 fallas y 0
  errores, BUILD SUCCESS**. No se agregó una suite nueva para esta corrección.
- Instancia, historial, solicitud, propuesta, fechas, empresa y archivo ficticios
  de I2 eliminados después de verificar. Las instancias y datos preexistentes se
  conservaron. Backend/web quedaron funcionando.

La prueba cubre el almacenamiento de PDFs; no implica que ya estén alineados los
formularios web o implementadas las etapas 9–10.
