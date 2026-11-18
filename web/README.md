# Sitio web

Responsable: **Luka**.

Sitio público, acceso de empresas y funciones del personal, desarrollado con React y Vite.

## Ejecutar con Docker Compose

Desde la raíz del repositorio:

```sh
docker compose up -d --build
```

La web queda disponible en http://localhost:5173 junto con Flowable y pgAdmin.
También se puede levantar solamente la web con `docker compose up -d --build web`.
No hace falta instalar Node.js en el equipo: la imagen compila la aplicación y Nginx sirve los archivos generados, incluidas las rutas de React al recargar una página.

Las solicitudes `/api/` se envían al backend del equipo en el puerto 8081.
El backend debe iniciarse por separado, como indica el README principal, y aceptar conexiones desde la red Docker.
Para cambiar el destino, definir `WEB_BACKEND_URL` antes de ejecutar Compose
(por ejemplo, `WEB_BACKEND_URL=http://backend:8081` si se incorpora ese servicio a la misma red).
El destino debe ser una URL sin ruta ni barra final; el proxy conserva la ruta `/api/`.

Después de modificar el código, volver a ejecutar `docker compose up -d --build web`.
Para consultar los registros: `docker compose logs -f web`.

## Iniciar en desarrollo

Desde esta carpeta, con Node.js 20.19+ o 22.12+:

```sh
npm ci
npm run dev
```

Abrir http://localhost:5173. Este modo necesita el backend en http://localhost:8081 para las funciones que usan la API.

Para probar con datos simulados sin iniciar el backend:

```sh
npm run dev:mock
```

Para comprobar la compilación: `npm run build`.

Se utiliza Vite 7 con `@vitejs/plugin-react` 5: la dependencia nativa Rolldown de Vite 8 provoca `Illegal instruction` en el equipo de desarrollo.

Dependencias: API institucional de Lauty en [backend/](../backend/), proceso de Alejo y [contrato de integración](../docs/MODELO_Y_INTEGRACION.md#acuerdo-de-integración). La interacción con Flowable será por REST a través del backend; el navegador no recibe credenciales técnicas del motor.
