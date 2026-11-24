# Comandos de `preparar_escenarios.py`

Se corren desde la carpeta raíz del repo:

```bash
python3 demo/preparar_escenarios.py <comando>
```

Verificar la versión: `head -3 demo/preparar_escenarios.py` tiene que decir **VERSION 7 (23/11)**.

| Comando | Para qué lo usás |
|---|---|
| `preparar --limpiar` | Borra todo y deja las 4 instancias (B, C, D, E) avanzadas hasta el punto justo para la demo. |
| `preparar --limpiar --solo-solicitudes` | Borra todo y crea solo las 4 solicitudes, sin avanzarlas, para hacer cada flujo a mano. |
| `estado` | Muestra las instancias activas, en qué tarea está cada una, su `pid` y los logins de empresa. |
| `escenario4 [B / cursoId / pid]` | Demo interactiva del escenario 4: con cada Enter pasan 5 días, hasta los dos recordatorios y el cierre por falta de respuesta. |
| `timer [B / cursoId / pid]` | Dispara ya el timer pendiente de una instancia: un recordatorio de 5 días o la **fecha del curso** (`fecha_programa`). |
| `informe [E / cursoId / pid] ["texto"]` | Completa *Elaborar informe* con el texto, para que se genere el PDF (esa tarea no tiene formulario en Flowable). |
| `backup [nombre]` | Guarda el estado actual: las dos bases y los PDFs. |
| `restaurar [nombre]` | Vuelve exactamente al estado de un backup (cerrá antes la app de materiales). |
| `limpiar [--si]` | Borra las instancias activas y vacía la BD institucional, sin crear nada nuevo. |

## Uso en la defensa

| Momento | Comandos |
|---|---|
| Día anterior | `preparar --limpiar` → `backup` |
| Antes de empezar | `estado` (tiene que mostrar 4 activas) |
| Durante la demo | `escenario4` · `informe E` |
| Si algo se rompe | `restaurar` |

## Usuarios

Todas las claves son `1234`.

| Dónde | Usuarios |
|---|---|
| Web del personal / Flowable UI | `Jefe-Administrativo`, `empleado-administrativo`, `Jefe-Docente`, `docente-1`, `docente-2` |
| App de materiales | `empleado-administrativo`, `Jefe-Administrativo` |
| Portal de empresa | El identificador de la empresa (por ejemplo `software-andino`). Existe recién después de aceptar la propuesta. |
| Flowable UI como admin | `admin` / `test` |
