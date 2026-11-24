# Solución de problemas frecuentes (Preparación de la Defensa)

Este instructivo reúne los problemas que pueden aparecer al preparar los escenarios de la defensa (`python3 demo/preparar_escenarios.py preparar --limpiar`) en cualquier máquina del equipo, con sus causas reales, los comandos exactos para resolverlos en orden y cómo verificar cada arreglo.

---

## 1. Permiso denegado al conectarse a Docker (`docker.sock`)

### Síntoma
```text
⚠ no se pudo vaciar la BD institucional: permission denied while trying to connect to the docker API at unix:///var/run/docker.sock
```

### Causa
El script ejecuta `docker exec` sobre `flowable-postgres` para vaciar las tablas de la base institucional y hacer backups, pero el usuario del sistema no pertenece al grupo `docker` (o lo agregó y todavía no recargó los grupos en su sesión actual).

### Comandos para arreglarlo
1. Agregar tu usuario al grupo `docker`:
   ```bash
   sudo usermod -aG docker $USER
   ```
2. Aplicar el cambio de grupo en la sesión:
   - Cerrar sesión en el escritorio y volver a entrar (o reiniciar), **o bien**
   - Si no podés cerrar sesión en ese momento, activar el grupo en la terminal actual (`newgrp docker`) o habilitar acceso inmediato al socket para tu usuario:
     ```bash
     sudo setfacl -m u:$USER:rw /var/run/docker.sock
     ```

### Cómo verificar que quedó bien
```bash
id
docker ps
docker compose ps
```
`docker ps` y `docker compose ps` deben listar los contenedores activos sin usar `sudo` y sin dar `permission denied`.

---

## 2. Error HTTP 500 `ERROR_GENERACION_PDF` al registrar una propuesta

### Síntoma
```text
✖ POST http://localhost:8081/api/v1/tareas/{id}/propuesta → HTTP 500
     {"codigo":"ERROR_GENERACION_PDF","mensaje":"No se pudo guardar el PDF de la propuesta."}
```

### Causa
El contenedor `backend` corre como usuario no privilegiado (`UID/GID 10001:10001`) y no puede escribir en `/app/almacenamiento/propuestas`. Esto ocurre por una o más de estas tres razones:
1. **Volumen perdido en `docker-compose.yml`:** En el merge `8a3ddf7` se perdieron las líneas `CURSOS_PROPUESTAS_DIR: /app/almacenamiento/propuestas` y `volumes: - backend_archivos:/app/almacenamiento` dentro del servicio `backend`.
2. **Imagen vieja del backend:** El contenedor se levantó con una imagen construida antes de que el `backend/Dockerfile` incluyera la creación de `/app/almacenamiento/propuestas` con dueño `10001:10001` y permisos `750`.
3. **Permisos del directorio/volumen existente:** El volumen o directorio `/app/almacenamiento` existe con dueño `root:root` y el usuario `10001` no tiene permiso de escritura.

### Comandos para arreglarlo
1. Verificar que en `docker-compose.yml` el servicio `backend` tenga declarada la variable `CURSOS_PROPUESTAS_DIR` y el volumen `backend_archivos:/app/almacenamiento`:
   ```yaml
         FLOWABLE_PROCESO_CLAVE: ${FLOWABLE_PROCESO_CLAVE:-process}
         CURSOS_DATOS_DEMO: ${CURSOS_DATOS_DEMO:-false}
         CURSOS_PROPUESTAS_DIR: /app/almacenamiento/propuestas
       volumes:
         - backend_archivos:/app/almacenamiento
   ```
2. Reconstruir y recrear únicamente el contenedor `backend` (sin tocar las bases ni usar `down -v`):
   ```bash
   docker compose config --quiet
   docker compose up -d --build backend
   ```
3. Asegurar dueño `10001:10001` y permisos `750` sobre `/app/almacenamiento` dentro del contenedor:
   ```bash
   docker compose exec -u 0 backend sh -c 'mkdir -p /app/almacenamiento/propuestas && chown -R 10001:10001 /app/almacenamiento && chmod -R 750 /app/almacenamiento'
   ```

### Cómo verificar que quedó bien
1. Comprobar que el volumen `backend_archivos` esté montado en `/app/almacenamiento`:
   ```bash
   docker inspect "$(docker compose ps -q backend)" --format '{{range .Mounts}}{{println .Name "->" .Destination}}{{end}}'
   ```
   *(Debe mostrar `<proyecto>_backend_archivos -> /app/almacenamiento`)*.
2. Comprobar que el usuario `10001` vea el directorio con permisos `drwxr-x---` (`750`) y pueda escribir:
   ```bash
   docker compose exec backend sh -c 'id; ls -ld /app/almacenamiento /app/almacenamiento/propuestas; test -w /app/almacenamiento/propuestas && echo PDF_DIRECTORIO_ESCRIBIBLE'
   ```
   *(Debe imprimir `PDF_DIRECTORIO_ESCRIBIBLE`)*.

---

## 3. Modelo publicado desde el Modeler sin asociaciones de compensación

### Síntoma
```text
✖ El modelo desplegado (versión X) tiene 0 asociaciones de compensación (deben ser 3).
     Seguramente se publicó desde el Modeler. Desplegá con: bash motor-flowable/modelos/desplegar.sh
```
*(O bien falla al completar tareas de compensación porque el botón **Publish** de Flowable Modeler elimina las etiquetas `<association>` de compensación del XML BPMN o faltan los listeners en el contenedor).*

### Causa
Se usó el botón **Publish** desde la interfaz web de Flowable Modeler (que descarta las 3 `<association>` de compensación al serializar el modelo) o el contenedor `flowable-persistente` no tiene cargado el `.jar` con los listeners compilados (`flowable-extensiones.jar`).

### Comandos para arreglarlo
1. Copiar `flowable-extensiones.jar` al contenedor de Flowable y reiniciarlo:
   ```bash
   docker cp motor-flowable/extensiones/flowable-extensiones.jar flowable-persistente:/app/WEB-INF/lib/flowable-extensiones.jar
   docker restart flowable-persistente
   ```
2. Esperar ~20-30 segundos a que Flowable termine de arrancar y desplegar el `.bar` oficial por script (**nunca** con el botón Publish del Modeler):
   ```bash
   bash motor-flowable/modelos/desplegar.sh
   ```

### Cómo verificar que quedó bien
Correr la preparación de escenarios y el backup:
```bash
python3 demo/preparar_escenarios.py preparar --limpiar
python3 demo/preparar_escenarios.py estado
python3 demo/preparar_escenarios.py backup
```
La salida debe confirmar:
- `✔ proceso 'process' desplegado (versión ...)`
- `✔ el modelo tiene las 3 asociaciones de compensación`
- `✔ 4 instancias activas: la próxima solicitud será viable (cantCursos=4) y la siguiente NO_VIABLE`
- `✔ base 'flowable' respaldada`, `✔ base 'institucion' respaldada` y `✔ PDFs de propuestas respaldados`.
